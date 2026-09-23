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
    private const val MODEL_FILENAME = "ggml-base.bin"
    const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"

    fun getModelFile(context: Context): File {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        return File(modelsDir, MODEL_FILENAME)
    }

    fun isModelDownloaded(context: Context): Boolean {
        val file = getModelFile(context)
        val exists = file.exists() && file.length() > 100_000_000L
        AppLogger.i(TAG, "isModelDownloaded check: $exists (${file.length()} bytes at ${file.absolutePath})")
        return exists
    }

    suspend fun downloadModel(
        context: Context,
        onProgress: (Float) -> Unit,
        onResult: (Boolean, String?) -> Unit
    ) = withContext(Dispatchers.IO) {
        val targetFile = getModelFile(context)
        val tempFile = File(targetFile.parentFile, "$MODEL_FILENAME.tmp")

        try {
            AppLogger.i(TAG, "Starting download of whisper base model from $MODEL_URL")
            var currentUrl = MODEL_URL
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
            AppLogger.i(TAG, "Downloading whisper base model. Expected size: $contentLength bytes")

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

            if (tempFile.exists() && tempFile.length() > 100_000_000L) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                AppLogger.i(TAG, "Model downloaded successfully to ${targetFile.absolutePath}")
                withContext(Dispatchers.Main) {
                    onProgress(1.0f)
                    onResult(true, null)
                }
            } else {
                AppLogger.e(TAG, "Downloaded file incomplete (${tempFile.length()} bytes)")
                tempFile.delete()
                withContext(Dispatchers.Main) {
                    onResult(false, "Downloaded file is incomplete.")
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
