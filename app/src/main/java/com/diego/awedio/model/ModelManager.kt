package com.diego.awedio.model

import android.content.Context
import com.diego.awedio.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ModelManager {
    private const val TAG = "ModelManager"

    private const val SIZE_TOLERANCE = 0.98f

    fun getModelFile(context: Context, model: WhisperModelDef): File {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        return File(modelsDir, model.fileName)
    }

    fun isModelDownloaded(context: Context, model: WhisperModelDef): Boolean {
        val file = getModelFile(context, model)
        val minBytes = (model.sizeBytes * SIZE_TOLERANCE).toLong()
        val exists = file.exists() && file.length() >= minBytes
        AppLogger.i(TAG, "isModelDownloaded check [$model.id]: $exists (${file.length()} bytes, expected >= $minBytes at ${file.absolutePath})")
        return exists
    }

    fun getDownloadStatusMap(context: Context): Map<String, Boolean> {
        return WhisperModels.ALL.associate { it.id to isModelDownloaded(context, it) }
    }

    fun deleteModel(context: Context, model: WhisperModelDef): Boolean {
        val file = getModelFile(context, model)
        return if (file.exists() && file.delete()) {
            AppLogger.i(TAG, "Model [$model.id] deleted from ${file.absolutePath}")
            true
        } else {
            AppLogger.e(TAG, "Failed to delete model [$model.id] at ${file.absolutePath}")
            false
        }
    }

    suspend fun downloadModel(
        context: Context,
        model: WhisperModelDef,
        onProgress: (Float) -> Unit,
        onResult: (Boolean, String?) -> Unit
    ) = withContext(Dispatchers.IO) {
        val targetFile = getModelFile(context, model)
        val tempFile = File(targetFile.parentFile, "${model.fileName}.tmp")

        try {
            AppLogger.i(TAG, "Starting download of [${model.id}] from ${model.url}")
            var currentUrl = model.url
            var connection: HttpURLConnection? = null
            var redirects = 0

            while (redirects < 5) {
                val url = URL(currentUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = true

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == 307 || status == 308
                ) {
                    currentUrl = connection.getHeaderField("Location")
                    redirects++
                } else {
                    break
                }
            }

            if (connection == null || connection.responseCode != HttpURLConnection.HTTP_OK) {
                val responseMsg = connection?.responseMessage ?: "Unknown error"
                AppLogger.e(TAG, "HTTP error during model download: ${connection?.responseCode} - $responseMsg")
                onResult(false, "HTTP ${connection?.responseCode}: $responseMsg")
                return@withContext
            }

            val contentLength = connection.contentLengthLong
            AppLogger.i(TAG, "Downloading [${model.id}]. Expected size: $contentLength bytes")

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int
                    var totalDownloaded: Long = 0

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead

                        if (contentLength > 0) {
                            val progress = totalDownloaded.toFloat() / contentLength.toFloat()
                            withContext(Dispatchers.Main) { onProgress(progress) }
                        }
                    }
                }
            }

            val minBytes = (model.sizeBytes * SIZE_TOLERANCE).toLong()
            if (tempFile.exists() && tempFile.length() >= minBytes) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                AppLogger.i(TAG, "Model [${model.id}] downloaded successfully to ${targetFile.absolutePath}")
                withContext(Dispatchers.Main) {
                    onProgress(1.0f)
                    onResult(true, null)
                }
            } else {
                AppLogger.e(TAG, "Downloaded file incomplete (${tempFile.length()} bytes, expected >= $minBytes)")
                tempFile.delete()
                withContext(Dispatchers.Main) {
                    onResult(false, "Arquivo baixado incompleto (${tempFile.length()} bytes).")
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error downloading model: ${e.message}", e)
            if (tempFile.exists()) tempFile.delete()
            withContext(Dispatchers.Main) {
                onResult(false, e.message ?: "Network error downloading model")
            }
        }
    }
}
