package com.diego.awedio.audio

import android.content.Context
import android.net.Uri
import com.diego.awedio.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object AudioConverter {
    private const val TAG = "AudioConverter"

    suspend fun copyUriToCache(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            AppLogger.i(TAG, "Attempting to copy Uri to cache: $uri")
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                AppLogger.e(TAG, "Failed to open InputStream for Uri: $uri")
                return@withContext null
            }

            val cacheDir = File(context.cacheDir, "shared_audio").apply { if (!exists()) mkdirs() }
            val outputFile = File(cacheDir, "input_shared_${System.currentTimeMillis()}.opus")

            FileOutputStream(outputFile).use { output ->
                inputStream.copyTo(output)
            }

            AppLogger.i(TAG, "Copied Uri to cache file: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
            outputFile
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error copying shared Uri to cache: ${e.message}", e)
            null
        }
    }

    suspend fun convertTo16kHzWav(context: Context, inputFile: File): File? = withContext(Dispatchers.IO) {
        try {
            val outputDir = File(context.cacheDir, "converted_audio").apply { if (!exists()) mkdirs() }
            val outputFile = File(outputDir, "converted_${System.currentTimeMillis()}.wav")

            if (outputFile.exists()) {
                outputFile.delete()
            }

            AppLogger.i(TAG, "Converting input audio via FFmpeg: ${inputFile.absolutePath} -> ${outputFile.absolutePath}")
            val returnCode = executeFFmpeg(inputFile.absolutePath, outputFile.absolutePath)

            if (returnCode == 0 && outputFile.exists() && outputFile.length() > 44) {
                AppLogger.i(TAG, "FFmpeg conversion succeeded! Output: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
                outputFile
            } else {
                AppLogger.w(TAG, "FFmpeg returned code $returnCode. Checking fallback...")
                if (inputFile.exists() && inputFile.length() > 44) {
                    AppLogger.i(TAG, "Using input file directly as fallback.")
                    inputFile
                } else null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error during FFmpeg conversion: ${e.message}", e)
            null
        }
    }

    private fun executeFFmpeg(inputPath: String, outputPath: String): Int {
        return try {
            val mobileFfmpegClass = Class.forName("com.arthenica.mobileffmpeg.FFmpeg")
            
            // 1. Try execute(String[] args)
            try {
                val executeArgsMethod = mobileFfmpegClass.getMethod("execute", Array<String>::class.java)
                val cmdArray = arrayOf("-y", "-i", inputPath, "-ar", "16000", "-ac", "1", "-c:a", "pcm_s16le", outputPath)
                val result = executeArgsMethod.invoke(null, cmdArray) as Int
                AppLogger.i(TAG, "FFmpeg.execute(String[]) returned exit code: $result")
                return result
            } catch (e: Exception) {
                AppLogger.w(TAG, "execute(String[]) reflection failed, trying execute(String): ${e.message}")
            }

            // 2. Fallback to execute(String command)
            val executeStringMethod = mobileFfmpegClass.getMethod("execute", String::class.java)
            val cmdString = "-y -i $inputPath -ar 16000 -ac 1 -c:a pcm_s16le $outputPath"
            val result = executeStringMethod.invoke(null, cmdString) as Int
            AppLogger.i(TAG, "FFmpeg.execute(String) returned exit code: $result")
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "FFmpeg reflection execution exception: ${e.message}", e)
            0
        }
    }
}
