package com.diego.awedio.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.diego.awedio.audio.AudioConverter
import com.diego.awedio.data.AppDatabase
import com.diego.awedio.data.TranscriptionEntity
import com.diego.awedio.model.ModelManager
import com.diego.awedio.whisper.WhisperLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.transcriptionDao()
    private val whisperLib = WhisperLib()

    val transcriptions: StateFlow<List<TranscriptionEntity>> = dao.getAllTranscriptions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

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
        checkModelStatus()
    }

    fun checkModelStatus() {
        val downloaded = ModelManager.isModelDownloaded(getApplication())
        _isModelDownloaded.value = downloaded
    }

    fun downloadModel() {
        if (_isDownloadingModel.value) return

        viewModelScope.launch {
            _isDownloadingModel.value = true
            _downloadProgress.value = 0f
            _statusMessage.value = "Downloading whisper base model..."

            ModelManager.downloadModel(
                context = getApplication(),
                onProgress = { progress ->
                    _downloadProgress.value = progress
                },
                onResult = { success, error ->
                    _isDownloadingModel.value = false
                    if (success) {
                        _isModelDownloaded.value = true
                        _statusMessage.value = "Whisper model downloaded successfully!"
                        _showModelMissingDialog.value = false

                        // Resume pending audio transcription if user was trying to share
                        pendingAudioUri?.let { uri ->
                            pendingAudioUri = null
                            processAudioUri(uri)
                        }
                    } else {
                        _statusMessage.value = "Model download error: ${error ?: "Unknown error"}"
                    }
                }
            )
        }
    }

    fun handleSharedAudioUri(uri: Uri) {
        if (!ModelManager.isModelDownloaded(getApplication())) {
            pendingAudioUri = uri
            _showModelMissingDialog.value = true
            return
        }
        processAudioUri(uri)
    }

    private fun processAudioUri(uri: Uri) {
        viewModelScope.launch {
            _isTranscribing.value = true
            _statusMessage.value = "Converting audio voice note..."

            val context = getApplication<Application>()
            val copiedFile = AudioConverter.copyUriToCache(context, uri)
            if (copiedFile == null) {
                _isTranscribing.value = false
                _statusMessage.value = "Failed to access shared audio file."
                return@launch
            }

            _statusMessage.value = "Converting to 16kHz mono WAV PCM..."
            val wavFile = AudioConverter.convertTo16kHzWav(context, copiedFile)
            if (wavFile == null || !wavFile.exists()) {
                _isTranscribing.value = false
                _statusMessage.value = "Audio conversion failed."
                return@launch
            }

            _statusMessage.value = "Transcribing voice note with whisper.cpp (pt)..."
            val transcribedText = withContext(Dispatchers.IO) {
                try {
                    val modelFile = ModelManager.getModelFile(context)
                    val ctxPtr = whisperLib.initContext(modelFile.absolutePath)
                    if (ctxPtr == 0L) {
                        Log.e("MainViewModel", "Failed to init whisper context")
                        return@withContext "Error: Failed to initialize whisper model."
                    }

                    val samples = WhisperLib.readWavSamples(wavFile)
                    val result = whisperLib.transcribeBuffer(ctxPtr, samples, "pt")
                    whisperLib.freeContext(ctxPtr)
                    result
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Transcription error: ${e.message}", e)
                    "Error transcribing audio: ${e.message}"
                }
            }

            _isTranscribing.value = false

            val durationSeconds = (wavFile.length() - 44) / (16000 * 2)
            val entity = TranscriptionEntity(
                timestamp = System.currentTimeMillis(),
                transcribedText = transcribedText,
                audioFilename = copiedFile.name,
                durationSeconds = if (durationSeconds > 0) durationSeconds.toInt() else null
            )

            val id = dao.insertTranscription(entity)
            val newEntity = entity.copy(id = id)

            _selectedTranscription.value = newEntity
            _statusMessage.value = "Transcription completed!"
        }
    }

    fun selectTranscription(entity: TranscriptionEntity?) {
        _selectedTranscription.value = entity
    }

    fun deleteTranscription(entity: TranscriptionEntity) {
        viewModelScope.launch {
            dao.deleteTranscription(entity)
            if (_selectedTranscription.value?.id == entity.id) {
                _selectedTranscription.value = null
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            dao.clearAll()
            _selectedTranscription.value = null
        }
    }

    fun dismissModelDialog() {
        _showModelMissingDialog.value = false
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
