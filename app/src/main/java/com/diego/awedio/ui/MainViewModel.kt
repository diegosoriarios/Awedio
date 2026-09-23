package com.diego.awedio.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.diego.awedio.audio.AudioConverter
import com.diego.awedio.data.AppDatabase
import com.diego.awedio.data.TranscriptionEntity
import com.diego.awedio.model.ModelManager
import com.diego.awedio.util.AppLogger
import com.diego.awedio.whisper.WhisperLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db by lazy { AppDatabase.getDatabase(getApplication()) }
    private val dao by lazy { db.transcriptionDao() }
    private val whisperLib by lazy { WhisperLib() }

    private val _transcriptionsList = MutableStateFlow<List<TranscriptionEntity>>(emptyList())
    val transcriptions: StateFlow<List<TranscriptionEntity>> = _transcriptionsList.asStateFlow()

    private val _selectedTranscription = MutableStateFlow<TranscriptionEntity?>(null)
    val selectedTranscription: StateFlow<TranscriptionEntity?> = _selectedTranscription.asStateFlow()

    private val _isModelDownloaded = MutableStateFlow(false)
    val isModelDownloaded: StateFlow<Boolean> = _isModelDownloaded.asStateFlow()

    private val _isDownloadingModel = MutableStateFlow(false)
    val isDownloadingModel: StateFlow<Boolean> = _isDownloadingModel.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _showModelMissingDialog = MutableStateFlow(false)
    val showModelMissingDialog: StateFlow<Boolean> = _showModelMissingDialog.asStateFlow()

    private var pendingAudioUri: Uri? = null

    init {
        AppLogger.i(TAG, "MainViewModel initialized.")
        checkModelStatus()
        refreshTranscriptions()
    }

    fun refreshTranscriptions() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AppLogger.i(TAG, "Executing query to fetch transcriptions from Room...")
                val list = dao.transcriptionsList
                val count = list?.size ?: 0
                AppLogger.i(TAG, "Fetched $count transcription(s) from Room database.")
                _transcriptionsList.value = list ?: emptyList()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error fetching transcriptions list from Room: ${e.message}", e)
            }
        }
    }

    fun checkModelStatus() {
        try {
            val downloaded = ModelManager.isModelDownloaded(getApplication())
            _isModelDownloaded.value = downloaded
            AppLogger.i(TAG, "Model status checked: downloaded=$downloaded")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error checking model status: ${e.message}", e)
        }
    }

    fun downloadModel() {
        if (_isDownloadingModel.value) return

        viewModelScope.launch {
            _isDownloadingModel.value = true
            _downloadProgress.value = 0f
            _statusMessage.value = "Baixando modelo Whisper base (~142MB)..."
            AppLogger.i(TAG, "User triggered model download.")

            ModelManager.downloadModel(
                context = getApplication(),
                onProgress = { progress ->
                    _downloadProgress.value = progress
                },
                onResult = { success, error ->
                    _isDownloadingModel.value = false
                    if (success) {
                        _isModelDownloaded.value = true
                        _statusMessage.value = "Modelo Whisper instalado com sucesso!"
                        _showModelMissingDialog.value = false
                        AppLogger.i(TAG, "Model download completed successfully.")

                        pendingAudioUri?.let { uri ->
                            AppLogger.i(TAG, "Resuming pending audio transcription for Uri: $uri")
                            pendingAudioUri = null
                            processAudioUri(uri)
                        }
                    } else {
                        val errorMsg = "Erro no download: ${error ?: "Erro desconhecido"}"
                        _statusMessage.value = errorMsg
                        AppLogger.e(TAG, errorMsg)
                    }
                }
            )
        }
    }

    fun handleSharedAudioUri(uri: Uri) {
        AppLogger.i(TAG, "handleSharedAudioUri called for Uri: $uri")
        if (!ModelManager.isModelDownloaded(getApplication())) {
            AppLogger.w(TAG, "Whisper model not downloaded. Prompting user to download.")
            pendingAudioUri = uri
            _showModelMissingDialog.value = true
            _statusMessage.value = "Baixe o modelo Whisper (~142MB) para transcrever offline."
            return
        }
        processAudioUri(uri)
    }

    private fun processAudioUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isTranscribing.value = true
            _statusMessage.value = "Acessando nota de voz compartilhada..."
            AppLogger.i(TAG, "Starting audio processing pipeline for Uri: $uri")

            val context = getApplication<Application>()
            val copiedFile = AudioConverter.copyUriToCache(context, uri)
            if (copiedFile == null) {
                _isTranscribing.value = false
                _statusMessage.value = "Erro: Não foi possível acessar o arquivo de áudio."
                AppLogger.e(TAG, "Failed to copy shared Uri stream to cache file.")
                return@launch
            }

            _statusMessage.value = "Convertendo áudio para PCM 16kHz via FFmpeg..."
            val wavFile = AudioConverter.convertTo16kHzWav(context, copiedFile)
            if (wavFile == null || !wavFile.exists()) {
                _isTranscribing.value = false
                _statusMessage.value = "Erro: Falha na conversão do arquivo de áudio."
                AppLogger.e(TAG, "FFmpeg conversion produced null or empty file.")
                return@launch
            }

            _statusMessage.value = "Transcrevendo áudio em Português com Whisper.cpp..."
            val transcribedText = withContext(Dispatchers.IO) {
                try {
                    val modelFile = ModelManager.getModelFile(context)
                    AppLogger.i(TAG, "Initializing Whisper JNI context with model: ${modelFile.absolutePath}")
                    val ctxPtr = whisperLib.initContext(modelFile.absolutePath)
                    if (ctxPtr == 0L) {
                        AppLogger.e(TAG, "Failed to init whisper context (returned 0)")
                        return@withContext "Erro: Não foi possível inicializar o modelo Whisper."
                    }

                    val samples = WhisperLib.readWavSamples(wavFile)
                    if (samples.isEmpty()) {
                        whisperLib.freeContext(ctxPtr)
                        AppLogger.e(TAG, "Audio sample buffer was empty after reading WAV")
                        return@withContext "Erro: Nenhuma amostra de áudio válida foi extraída."
                    }

                    AppLogger.i(TAG, "Calling whisperLib.transcribeBuffer for ${samples.size} samples...")
                    val result = whisperLib.transcribeBuffer(ctxPtr, samples, "pt")
                    whisperLib.freeContext(ctxPtr)
                    AppLogger.i(TAG, "Whisper transcription returned text length: ${result.length}")
                    result
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Transcription error: ${e.message}", e)
                    "Erro durante a transcrição: ${e.message}"
                }
            }

            _isTranscribing.value = false

            val textToSave = if (transcribedText.isBlank()) {
                "Áudio processado (nenhuma fala detectada)."
            } else {
                transcribedText
            }

            val durationSeconds = (wavFile.length() - 44) / (16000 * 2)
            val entity = TranscriptionEntity(
                timestamp = System.currentTimeMillis(),
                transcribedText = textToSave,
                audioFilename = copiedFile.name,
                durationSeconds = if (durationSeconds > 0) durationSeconds.toInt() else null
            )

            try {
                AppLogger.i(TAG, "Inserting transcription entity into Room database...")
                val id = dao.insertTranscription(entity)
                val newEntity = entity.copy(id = id)
                AppLogger.i(TAG, "Successfully inserted entity into Room with generated ID: $id")
                _selectedTranscription.value = newEntity
                _statusMessage.value = "Transcrição concluída com sucesso!"
                refreshTranscriptions()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error saving transcription to Room: ${e.message}", e)
                _statusMessage.value = "Erro ao salvar a transcrição: ${e.message}"
            }
        }
    }

    fun selectTranscription(entity: TranscriptionEntity?) {
        _selectedTranscription.value = entity
    }

    fun deleteTranscription(entity: TranscriptionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AppLogger.i(TAG, "Deleting transcription ID ${entity.id}...")
                dao.deleteTranscription(entity)
                if (_selectedTranscription.value?.id == entity.id) {
                    _selectedTranscription.value = null
                }
                refreshTranscriptions()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error deleting transcription: ${e.message}", e)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AppLogger.i(TAG, "Clearing all transcription history from Room...")
                dao.clearAll()
                _selectedTranscription.value = null
                refreshTranscriptions()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error clearing history: ${e.message}", e)
            }
        }
    }

    fun dismissModelDialog() {
        _showModelMissingDialog.value = false
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}
