package com.diego.awedio.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.diego.awedio.R
import com.diego.awedio.audio.AudioConverter
import com.diego.awedio.data.AppDatabase
import com.diego.awedio.data.TranscriptionEntity
import com.diego.awedio.model.ModelManager
import com.diego.awedio.model.TranscriptionLanguage
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
import java.util.Locale

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

    private val _statusMessage = MutableStateFlow<UiMessage?>(null)
    val statusMessage: StateFlow<UiMessage?> = _statusMessage.asStateFlow()

    private val _showModelMissingDialog = MutableStateFlow(false)
    val showModelMissingDialog: StateFlow<Boolean> = _showModelMissingDialog.asStateFlow()

    private val _transcriptionLanguage = MutableStateFlow(TranscriptionLanguage.DEVICE)
    val transcriptionLanguage: StateFlow<TranscriptionLanguage> = _transcriptionLanguage.asStateFlow()

    private var pendingAudioUri: Uri? = null

    init {
        AppLogger.i(TAG, "MainViewModel initialized.")
        restoreSelectedModel()
        restoreTranscriptionLanguage()
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

    private fun restoreTranscriptionLanguage() {
        val saved = prefs.getString(TranscriptionLanguage.PREF_KEY, null)
        _transcriptionLanguage.value = TranscriptionLanguage.fromPref(saved)
        AppLogger.i(TAG, "Restored transcription language: ${_transcriptionLanguage.value.prefValue}")
    }

    fun setTranscriptionLanguage(language: TranscriptionLanguage) {
        _transcriptionLanguage.value = language
        prefs.edit().putString(TranscriptionLanguage.PREF_KEY, language.prefValue).apply()
        AppLogger.i(TAG, "Transcription language set to: ${language.prefValue}")
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
            _statusMessage.value = UiMessage(R.string.msg_wait_download)
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
            UiMessage(R.string.msg_model_selected, listOf(model.displayName))
        } else {
            UiMessage(R.string.msg_model_selected_download, listOf(model.displayName, model.sizeLabel))
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
            _statusMessage.value = UiMessage(R.string.msg_downloading, listOf(model.displayName, model.sizeLabel))
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
                        _statusMessage.value = UiMessage(R.string.msg_model_installed, listOf(model.displayName))
                        _showModelMissingDialog.value = false
                        AppLogger.i(TAG, "Model [${model.id}] download completed successfully.")

                        pendingAudioUri?.let { uri ->
                            AppLogger.i(TAG, "Resuming pending audio transcription for Uri: $uri")
                            pendingAudioUri = null
                            processAudioUri(uri)
                        }
                    } else {
                        val appContext = getApplication<Application>()
                        val errorDetail = error ?: appContext.getString(R.string.unknown_error)
                        _statusMessage.value = UiMessage(R.string.msg_download_error, listOf(errorDetail))
                        AppLogger.e(TAG, "Download error: $errorDetail")
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
            _statusMessage.value = UiMessage(R.string.msg_cannot_delete_in_use)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val deleted = ModelManager.deleteModel(getApplication(), model)
            _statusMessage.value = if (deleted) {
                UiMessage(R.string.msg_model_deleted, listOf(model.displayName))
            } else {
                UiMessage(R.string.msg_model_delete_error, listOf(model.displayName))
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
            _statusMessage.value = UiMessage(R.string.msg_download_to_transcribe, listOf(model.displayName, model.sizeLabel))
            return
        }
        processAudioUri(uri)
    }

    private fun processAudioUri(uri: Uri) {
        val model = _selectedModel.value
        val appContext = getApplication<Application>()

        val language = _transcriptionLanguage.value
        val deviceLanguage = Locale.getDefault().language
        val whisperLanguage = TranscriptionLanguage.whisperCode(language, deviceLanguage)
        val languageLabel = appContext.getString(language.labelRes)

        viewModelScope.launch(Dispatchers.IO) {
            _isTranscribing.value = true
            _statusMessage.value = UiMessage(R.string.msg_accessing_audio)
            AppLogger.i(TAG, "Starting audio processing pipeline for Uri: $uri (model=${model.id}, lang=$whisperLanguage)")

            val copiedFile = AudioConverter.copyUriToCache(appContext, uri)
            if (copiedFile == null) {
                _isTranscribing.value = false
                _statusMessage.value = UiMessage(R.string.err_access_audio)
                AppLogger.e(TAG, "Failed to copy shared Uri stream to cache file.")
                return@launch
            }

            _statusMessage.value = UiMessage(R.string.msg_converting)
            val wavFile = AudioConverter.convertTo16kHzWav(appContext, copiedFile)
            if (wavFile == null || !wavFile.exists()) {
                _isTranscribing.value = false
                _statusMessage.value = UiMessage(R.string.err_conversion)
                AppLogger.e(TAG, "FFmpeg conversion produced null or empty file.")
                return@launch
            }

            _statusMessage.value = UiMessage(R.string.msg_transcribing, listOf(model.displayName, languageLabel))
            val transcribedText = withContext(Dispatchers.IO) {
                try {
                    val modelFile = ModelManager.getModelFile(appContext, model)
                    AppLogger.i(TAG, "Initializing Whisper JNI context with model: ${modelFile.absolutePath}")
                    val ctxPtr = whisperLib.initContext(modelFile.absolutePath)
                    if (ctxPtr == 0L) {
                        AppLogger.e(TAG, "Failed to init whisper context (returned 0)")
                        return@withContext appContext.getString(R.string.err_model_init)
                    }

                    val samples = WhisperLib.readWavSamples(wavFile)
                    if (samples.isEmpty()) {
                        whisperLib.freeContext(ctxPtr)
                        AppLogger.e(TAG, "Audio sample buffer was empty after reading WAV")
                        return@withContext appContext.getString(R.string.err_no_samples)
                    }

                    AppLogger.i(TAG, "Calling whisperLib.transcribeBuffer for ${samples.size} samples (language=$whisperLanguage)...")
                    val result = whisperLib.transcribeBuffer(ctxPtr, samples, whisperLanguage)
                    whisperLib.freeContext(ctxPtr)
                    AppLogger.i(TAG, "Whisper transcription returned text length: ${result.length}")
                    result
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Transcription error: ${e.message}", e)
                    appContext.getString(R.string.err_transcription, e.message ?: "")
                }
            }

            _isTranscribing.value = false

            val textToSave = if (transcribedText.isBlank()) {
                appContext.getString(R.string.msg_no_speech)
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
                _statusMessage.value = UiMessage(R.string.msg_transcription_complete)
                refreshTranscriptions()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error saving transcription to Room: ${e.message}", e)
                _statusMessage.value = UiMessage(R.string.err_save, listOf(e.message ?: ""))
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
