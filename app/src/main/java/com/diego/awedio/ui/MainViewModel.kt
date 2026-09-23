package com.diego.awedio.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.diego.awedio.audio.AudioConverter
import com.diego.awedio.data.AppDatabase
import com.diego.awedio.data.TranscriptionEntity
import com.diego.awedio.model.ModelManager
import com.diego.awedio.model.WhisperModelDef
import com.diego.awedio.model.WhisperModels
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
    private val prefs by lazy {
        getApplication<Application>().getSharedPreferences(WhisperModels.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }

    private val _transcriptionsList = MutableStateFlow<List<TranscriptionEntity>>(emptyList())
    val transcriptions: StateFlow<List<TranscriptionEntity>> = _transcriptionsList.asStateFlow()

    private val _selectedTranscription = MutableStateFlow<TranscriptionEntity?>(null)
    val selectedTranscription: StateFlow<TranscriptionEntity?> = _selectedTranscription.asStateFlow()

    private val _selectedModel = MutableStateFlow(WhisperModels.DEFAULT)
    val selectedModel: StateFlow<WhisperModelDef> = _selectedModel.asStateFlow()

    private val _availableModels = MutableStateFlow(WhisperModels.ALL)
    val availableModels: StateFlow<List<WhisperModelDef>> = _availableModels.asStateFlow()

    private val _modelDownloadStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val modelDownloadStatus: StateFlow<Map<String, Boolean>> = _modelDownloadStatus.asStateFlow()

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
        restoreSelectedModel()
        refreshModelStatus()
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

    private fun restoreSelectedModel() {
        try {
            val savedId = prefs.getString(WhisperModels.PREF_SELECTED_MODEL_ID, WhisperModels.DEFAULT.id)
            val model = WhisperModels.byId(savedId)
            _selectedModel.value = model
            AppLogger.i(TAG, "Restored selected model from prefs: ${model.id}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error restoring selected model: ${e.message}", e)
        }
    }

    private fun refreshModelStatus() {
        try {
            val statusMap = ModelManager.getDownloadStatusMap(getApplication())
            _modelDownloadStatus.value = statusMap
            _isModelDownloaded.value = statusMap[_selectedModel.value.id] == true
            AppLogger.i(TAG, "Model status refreshed: $statusMap (selected=${_selectedModel.value.id})")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error refreshing model status: ${e.message}", e)
        }
    }

    fun selectModel(model: WhisperModelDef) {
        if (_isDownloadingModel.value) {
            AppLogger.w(TAG, "Cannot switch model while downloading.")
            _statusMessage.value = "Aguarde o download terminar para trocar de modelo."
            return
        }

        val isDownloaded = _modelDownloadStatus.value[model.id] == true
        _selectedModel.value = model
        _isModelDownloaded.value = isDownloaded

        try {
            prefs.edit().putString(WhisperModels.PREF_SELECTED_MODEL_ID, model.id).apply()
            AppLogger.i(TAG, "Selected model persisted: ${model.id}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error persisting selected model: ${e.message}", e)
        }

        _statusMessage.value = if (isDownloaded) {
            "Modelo ${model.displayName} selecionado."
        } else {
            "Modelo ${model.displayName} selecionado. Baixe (${model.sizeLabel}) para usar."
        }
    }

    fun checkModelStatus() {
        refreshModelStatus()
    }

    fun downloadModel() {
        if (_isDownloadingModel.value) return

        val model = _selectedModel.value

        viewModelScope.launch {
            _isDownloadingModel.value = true
            _downloadProgress.value = 0f
            _statusMessage.value = "Baixando ${model.displayName} (${model.sizeLabel})..."
            AppLogger.i(TAG, "User triggered model download for [${model.id}].")

            ModelManager.downloadModel(
                context = getApplication(),
                model = model,
                onProgress = { progress ->
                    _downloadProgress.value = progress
                },
                onResult = { success, error ->
                    _isDownloadingModel.value = false
                    if (success) {
                        refreshModelStatus()
                        _statusMessage.value = "Modelo ${model.displayName} instalado com sucesso!"
                        _showModelMissingDialog.value = false
                        AppLogger.i(TAG, "Model [${model.id}] download completed successfully.")

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

    fun deleteModel(model: WhisperModelDef) {
        if (_isDownloadingModel.value && _selectedModel.value.id == model.id) {
            AppLogger.w(TAG, "Cannot delete model [${model.id}] while downloading it.")
            return
        }

        if (_selectedModel.value.id == model.id) {
            _statusMessage.value = "Não é possível excluir o modelo em uso. Selecione outro modelo primeiro."
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val deleted = ModelManager.deleteModel(getApplication(), model)
            if (deleted) {
                _statusMessage.value = "Modelo ${model.displayName} excluído."
            } else {
                _statusMessage.value = "Erro ao excluir o modelo ${model.displayName}."
            }
            withContext(Dispatchers.Main) { refreshModelStatus() }
        }
    }

    fun handleSharedAudioUri(uri: Uri) {
        AppLogger.i(TAG, "handleSharedAudioUri called for Uri: $uri")
        if (!_isModelDownloaded.value) {
            val model = _selectedModel.value
            AppLogger.w(TAG, "Model [${model.id}] not downloaded. Prompting user to download.")
            pendingAudioUri = uri
            _showModelMissingDialog.value = true
            _statusMessage.value = "Baixe o modelo ${model.displayName} (${model.sizeLabel}) para transcrever offline."
            return
        }
        processAudioUri(uri)
    }

    private fun processAudioUri(uri: Uri) {
        val model = _selectedModel.value

        viewModelScope.launch(Dispatchers.IO) {
            _isTranscribing.value = true
            _statusMessage.value = "Acessando nota de voz compartilhada..."
            AppLogger.i(TAG, "Starting audio processing pipeline for Uri: $uri (model=${model.id})")

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

            _statusMessage.value = "Transcrevendo áudio em Português com ${model.displayName}..."
            val transcribedText = withContext(Dispatchers.IO) {
                try {
                    val modelFile = ModelManager.getModelFile(context, model)
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
