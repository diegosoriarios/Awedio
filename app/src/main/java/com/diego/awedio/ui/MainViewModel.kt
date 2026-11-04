package com.diego.awedio.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.diego.awedio.R
import com.diego.awedio.audio.AudioConverter
import com.diego.awedio.data.AppDatabase
import com.diego.awedio.data.TranscriptionEntity
import com.diego.awedio.data.TranscriptionStatus
import com.diego.awedio.model.ModelManager
import com.diego.awedio.model.TranscriptionLanguage
import com.diego.awedio.model.WhisperModelDef
import com.diego.awedio.model.WhisperModels
import com.diego.awedio.util.AppLogger
import com.diego.awedio.whisper.WhisperLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private data class QueueProgress(val done: Int, val failed: Int, val total: Int)

    private data class QueueWorkItem(val entityId: Long, val uri: Uri)

    private val db by lazy { AppDatabase.getDatabase(getApplication()) }
    private val dao by lazy { db.transcriptionDao() }
    private val whisperLib by lazy { WhisperLib() }
    private val prefs by lazy {
        getApplication<Application>().getSharedPreferences(WhisperModels.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }

    private val queueChannel = Channel<QueueWorkItem>(Channel.UNLIMITED)
    private val progressLock = Any()
    private val pendingQueue = LinkedHashMap<Long, Uri>()
    private val remainingAutoOpen = AtomicInteger(0)
    private val _queueProgress = MutableStateFlow<QueueProgress?>(null)

    private var whisperContextPtr: Long = 0L
    private var whisperContextModelId: String? = null

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

    init {
        AppLogger.i(TAG, "MainViewModel initialized.")
        restoreSelectedModel()
        restoreTranscriptionLanguage()
        refreshModelStatus()
        sweepStaleQueueItems()
        launchQueueWorker()
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

    private fun sweepStaleQueueItems() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val staleCount = dao.failStaleQueueItems()
                if (staleCount > 0) {
                    AppLogger.w(TAG, "Marked $staleCount stale queued item(s) as ERROR after restart.")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error sweeping stale queue items: ${e.message}", e)
            }
            refreshTranscriptions()
        }
    }

    private fun launchQueueWorker() {
        viewModelScope.launch(Dispatchers.IO) {
            AppLogger.i(TAG, "Queue worker started.")
            for (work in queueChannel) {
                try {
                    processQueuedItem(work)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Queue worker error for item ${work.entityId}: ${e.message}", e)
                    markItemFailed(work.entityId, e)
                    onItemFinished(failed = true, entity = null)
                }
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

                        val pending = synchronized(progressLock) {
                            val snapshot = pendingQueue.toList()
                            pendingQueue.clear()
                            snapshot
                        }
                        if (pending.isNotEmpty()) {
                            AppLogger.i(TAG, "Resuming ${pending.size} queued item(s) after model download.")
                            enqueueItems(pending)
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

    fun handleSharedAudioUris(uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val distinctUris = uris.distinct()
            AppLogger.i(TAG, "handleSharedAudioUris: ${distinctUris.size} uri(s) received.")
            val appContext = getApplication<Application>()

            val entities = distinctUris.map { uri ->
                TranscriptionEntity(
                    timestamp = System.currentTimeMillis(),
                    transcribedText = "",
                    status = TranscriptionStatus.QUEUED.name,
                    displayName = queryDisplayName(appContext, uri)
                )
            }

            val ids = try {
                entities.map { dao.insertTranscription(it) }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to queue shared audio: ${e.message}", e)
                _statusMessage.value = UiMessage(
                    R.string.err_save,
                    listOf(e.message ?: appContext.getString(R.string.unknown_error))
                )
                return@launch
            }
            refreshTranscriptions()

            if (!_isModelDownloaded.value) {
                val model = _selectedModel.value
                AppLogger.w(TAG, "Model [${model.id}] not downloaded. Holding ${ids.size} item(s) until download completes.")
                synchronized(progressLock) { pendingQueue.putAll(ids.zip(distinctUris)) }
                _showModelMissingDialog.value = true
                _statusMessage.value = UiMessage(
                    R.string.msg_batch_queued_download,
                    listOf(ids.size, model.displayName, model.sizeLabel)
                )
            } else {
                enqueueItems(ids.zip(distinctUris))
            }
        }
    }

    private fun enqueueItems(items: List<Pair<Long, Uri>>) {
        if (items.isEmpty()) return

        synchronized(progressLock) {
            val current = _queueProgress.value
            _queueProgress.value = if (current == null) {
                QueueProgress(done = 0, failed = 0, total = items.size)
            } else {
                current.copy(total = current.total + items.size)
            }
            val progress = _queueProgress.value!!
            _statusMessage.value = UiMessage(
                R.string.msg_transcribing_progress,
                listOf(progress.done + 1, progress.total)
            )
        }

        remainingAutoOpen.addAndGet(items.size)
        _isTranscribing.value = true
        items.forEach { (id, uri) ->
            queueChannel.trySend(QueueWorkItem(id, uri))
        }
    }

    private fun onItemFinished(failed: Boolean, entity: TranscriptionEntity?) {
        synchronized(progressLock) {
            val current = _queueProgress.value
            if (current == null) {
                _isTranscribing.value = false
                return
            }

            val done = current.done + 1
            val failedCount = current.failed + if (failed) 1 else 0

            if (done >= current.total) {
                _queueProgress.value = null
                _isTranscribing.value = false
                releaseWhisperContext()
                val succeeded = current.total - failedCount
                _statusMessage.value = if (failedCount > 0) {
                    UiMessage(R.string.msg_batch_done_errors, listOf(succeeded, current.total, failedCount))
                } else {
                    UiMessage(R.string.msg_batch_done, listOf(current.total, current.total))
                }
                AppLogger.i(TAG, "Queue batch finished: $succeeded/${current.total} transcribed, $failedCount failed.")
            } else {
                _queueProgress.value = current.copy(done = done, failed = failedCount)
                _statusMessage.value = UiMessage(
                    R.string.msg_transcribing_progress,
                    listOf(done + 1, current.total)
                )
            }
        }

        val autoOpen = remainingAutoOpen.decrementAndGet() <= 0 && entity != null
        if (autoOpen) {
            _selectedTranscription.value = entity
        }
    }

    private suspend fun processQueuedItem(work: QueueWorkItem) {
        val appContext = getApplication<Application>()
        val model = _selectedModel.value
        val language = _transcriptionLanguage.value
        val whisperLanguage = TranscriptionLanguage.whisperCode(language, Locale.getDefault().language)
        val languageLabel = appContext.getString(language.labelRes)

        val entity = try {
            dao.getTranscriptionById(work.entityId)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to load queued entity ${work.entityId}: ${e.message}", e)
            null
        }
        if (entity == null) {
            AppLogger.w(TAG, "Queued item ${work.entityId} no longer exists (deleted). Skipping.")
            onItemFinished(failed = true, entity = null)
            return
        }

        AppLogger.i(TAG, "Processing queued item ${work.entityId} (model=${model.id}, lang=$whisperLanguage)")
        try {
            dao.updateStatus(work.entityId, TranscriptionStatus.PROCESSING.name)
            refreshTranscriptions()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to mark item ${work.entityId} as PROCESSING: ${e.message}", e)
        }

        var failed = false
        var resultText = ""
        var durationSeconds: Int? = null
        var copiedFile: File? = null
        var wavFile: File? = null

        try {
            copiedFile = AudioConverter.copyUriToCache(appContext, work.uri)
            if (copiedFile == null) {
                failed = true
                resultText = appContext.getString(R.string.err_access_audio)
                AppLogger.e(TAG, "Failed to copy shared Uri stream to cache file for item ${work.entityId}.")
            } else {
                wavFile = AudioConverter.convertTo16kHzWav(appContext, copiedFile)
                if (wavFile == null || !wavFile.exists()) {
                    failed = true
                    resultText = appContext.getString(R.string.err_conversion)
                    AppLogger.e(TAG, "Audio conversion produced null or empty file for item ${work.entityId}.")
                } else {
                    durationSeconds = ((wavFile.length() - 44) / (16000 * 2)).toInt().takeIf { it > 0 }
                    val (transcribeFailed, text) = transcribeWithWhisper(
                        appContext = appContext,
                        model = model,
                        wavFile = wavFile,
                        whisperLanguage = whisperLanguage
                    )
                    failed = transcribeFailed
                    resultText = text
                }
            }
        } catch (e: Exception) {
            failed = true
            resultText = appContext.getString(
                R.string.err_transcription,
                e.message ?: appContext.getString(R.string.unknown_error)
            )
            AppLogger.e(TAG, "Pipeline error for item ${work.entityId}: ${e.message}", e)
        }

        try {
            wavFile?.let { if (it.exists()) it.delete() }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not delete converted WAV: ${e.message}")
        }

        if (!failed && resultText.isBlank()) {
            resultText = appContext.getString(R.string.msg_no_speech)
        }

        val finalStatus = if (failed) TranscriptionStatus.ERROR else TranscriptionStatus.DONE
        try {
            dao.completeTranscription(work.entityId, finalStatus.name, resultText, copiedFile?.name, durationSeconds)
            AppLogger.i(TAG, "Item ${work.entityId} finished with status ${finalStatus.name}.")
            refreshTranscriptions()
            onItemFinished(
                failed = failed,
                entity = entity.copy(
                    transcribedText = resultText,
                    audioFilename = copiedFile?.name,
                    durationSeconds = durationSeconds,
                    status = finalStatus.name
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error saving transcription for item ${work.entityId}: ${e.message}", e)
            _statusMessage.value = UiMessage(R.string.err_save, listOf(e.message ?: ""))
            onItemFinished(failed = true, entity = null)
        }
    }

    private fun ensureWhisperContext(model: WhisperModelDef, modelFile: File): Long {
        if (whisperContextPtr != 0L && whisperContextModelId == model.id) {
            AppLogger.i(TAG, "Reusing cached whisper context for model [${model.id}].")
            return whisperContextPtr
        }
        releaseWhisperContext()
        AppLogger.i(TAG, "Initializing Whisper JNI context with model: ${modelFile.absolutePath}")
        val ptr = whisperLib.initContext(modelFile.absolutePath)
        if (ptr != 0L) {
            whisperContextPtr = ptr
            whisperContextModelId = model.id
        }
        return ptr
    }

    private fun releaseWhisperContext() {
        if (whisperContextPtr != 0L) {
            try {
                whisperLib.freeContext(whisperContextPtr)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error freeing whisper context: ${e.message}", e)
            }
            whisperContextPtr = 0L
            whisperContextModelId = null
        }
    }

    override fun onCleared() {
        releaseWhisperContext()
        super.onCleared()
    }

    private fun transcribeWithWhisper(
        appContext: Context,
        model: WhisperModelDef,
        wavFile: File,
        whisperLanguage: String
    ): Pair<Boolean, String> {
        return try {
            val modelFile = ModelManager.getModelFile(appContext, model)
            val ctxPtr = ensureWhisperContext(model, modelFile)
            if (ctxPtr == 0L) {
                AppLogger.e(TAG, "Failed to init whisper context (returned 0)")
                return true to appContext.getString(R.string.err_model_init)
            }

            val samples = WhisperLib.readWavSamples(wavFile)
            if (samples.isEmpty()) {
                AppLogger.e(TAG, "Audio sample buffer was empty after reading WAV")
                return true to appContext.getString(R.string.err_no_samples)
            }

            AppLogger.i(TAG, "Calling whisperLib.transcribeBuffer for ${samples.size} samples (language=$whisperLanguage, beam=${model.beamSize})...")
            val result = whisperLib.transcribeBuffer(ctxPtr, samples, whisperLanguage, model.beamSize)
            AppLogger.i(TAG, "Whisper transcription returned text length: ${result.length}")
            false to result
        } catch (e: Exception) {
            AppLogger.e(TAG, "Transcription error: ${e.message}", e)
            true to appContext.getString(R.string.err_transcription, e.message ?: "")
        }
    }

    private fun markItemFailed(entityId: Long, e: Exception) {
        try {
            val appContext = getApplication<Application>()
            dao.completeTranscription(
                entityId,
                TranscriptionStatus.ERROR.name,
                appContext.getString(R.string.err_transcription, e.message ?: appContext.getString(R.string.unknown_error)),
                null,
                null
            )
            refreshTranscriptions()
        } catch (dbError: Exception) {
            AppLogger.e(TAG, "Failed to mark item $entityId as ERROR: ${dbError.message}", dbError)
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Could not resolve display name for $uri: ${e.message}")
            null
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
