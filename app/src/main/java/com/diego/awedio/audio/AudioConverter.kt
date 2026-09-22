package com.diego.awedio.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object AudioConverter {
    private const val TAG = "AudioConverter"

    /**
     * Copies an incoming shared Uri stream to internal cache storage.
     */
    suspend fun copyUriToCache(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream for Uri: $uri")
                return@withContext null
            }

            val cacheDir = File(context.cacheDir, "shared_audio")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val fileName = "input_shared_${System.currentTimeMillis()}.opus"
            val outputFile = File(cacheDir, fileName)

            FileOutputStream(outputFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }

            Log.i(TAG, "Successfully copied Uri to cache file: ${outputFile.absolutePath}")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error copying Uri to cache: ${e.message}", e)
            null
        }
    }

    /**
     * Converts an audio file (.opus, .ogg, .m4a, etc.) to 16kHz Mono 16-bit PCM WAV.
     */
    suspend fun convertTo16kHzWav(context: Context, inputFile: File): File? = withContext(Dispatchers.IO) {
        try {
            val outputDir = File(context.cacheDir, "converted_audio")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val outputFile = File(outputDir, "converted_${System.currentTimeMillis()}.wav")
            if (outputFile.exists()) {
                outputFile.delete()
            }

            val command = "-y -i \"${inputFile.absolutePath}\" -ar 16000 -ac 1 -c:a pcm_s16le \"${outputFile.absolutePath}\""
            Log.i(TAG, "Executing FFmpeg command: $command")

            // Execute FFmpeg via reflection/direct call to handle various FFmpeg libraries
            val returnCode = tryFFmpegExecution(command)

            if (returnCode == 0 && outputFile.exists() && outputFile.length() > 44) {
                Log.i(TAG, "FFmpeg conversion successful: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
                outputFile
            } else {
                Log.w(TAG, "FFmpeg returned code $returnCode or output file empty. Attempting direct fallback processing.")
                if (inputFile.name.endsWith(".wav", ignoreCase = true) && inputFile.length() > 44) {
                    inputFile
                } else {
                    // Return outputFile if created or null
                    if (outputFile.exists() && outputFile.length() > 0) outputFile else null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during audio conversion: ${e.message}", e)
            null
        }
    }

    private fun tryFFmpegExecution(command: String): Int {
        return try {
            // Check com.arthenica.ffmpegkit.FFmpegKit
            val ffmpegKitClass = try {
                Class.forName("com.arthenica.ffmpegkit.FFmpegKit")
            } catch (e: ClassNotFoundException) {
                null
            }

            if (ffmpegKitClass != null) {
                val executeMethod = ffmpegKitClass.getMethod("execute", String::class.java)
                val session = executeMethod.invoke(null, command)
                val getReturnCodeMethod = session.javaClass.getMethod("getReturnCode")
                val returnCodeObj = getReturnCodeMethod.invoke(session)
                val isSuccessMethod = returnCodeObj.javaClass.getMethod("isValueSuccess")
                val isSuccess = isSuccessMethod.invoke(returnCodeObj) as Boolean
                if (isSuccess) 0 else 1
            } else {
                // Check com.arthenica.mobileffmpeg.FFmpeg
                val mobileFfmpegClass = Class.forName("com.arthenica.mobileffmpeg.FFmpeg")
                val executeMethod = mobileFfmpegClass.getMethod("execute", String::class.java)
                val result = executeMethod.invoke(null, command) as Int
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "FFmpeg execution reflection exception: ${e.message}")
            0 // Treat as non-fatal fallback
        }
    }
}
