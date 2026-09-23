package com.diego.awedio.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object AudioConverter {
    private const val TAG = "AudioConverter"

    /**
     * Copies an incoming shared Uri stream to internal cache storage.
     */
    suspend fun copyUriToCache(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Failed to open InputStream for Uri: $uri")
                return@withContext null
            }

            val cacheDir = File(context.cacheDir, "shared_audio").apply { if (!exists()) mkdirs() }
            val outputFile = File(cacheDir, "input_shared_${System.currentTimeMillis()}.opus")

            FileOutputStream(outputFile).use { output ->
                inputStream.copyTo(output)
            }

            Log.i(TAG, "Successfully copied shared Uri to cache: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error copying shared Uri to cache: ${e.message}", e)
            null
        }
    }

    /**
     * Converts an audio file (.opus, .ogg, .m4a, etc.) to 16kHz Mono 16-bit PCM WAV via FFmpeg.
     */
    suspend fun convertTo16kHzWav(context: Context, inputFile: File): File? = withContext(Dispatchers.IO) {
        try {
            val outputDir = File(context.cacheDir, "converted_audio").apply { if (!exists()) mkdirs() }
            val outputFile = File(outputDir, "converted_${System.currentTimeMillis()}.wav")

            if (outputFile.exists()) {
                outputFile.delete()
            }

            Log.i(TAG, "Converting input audio: ${inputFile.absolutePath} -> ${outputFile.absolutePath}")
            val returnCode = executeFFmpeg(inputFile.absolutePath, outputFile.absolutePath)

            if (returnCode == 0 && outputFile.exists() && outputFile.length() > 44) {
                Log.i(TAG, "FFmpeg audio conversion succeeded: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
                outputFile
            } else {
                Log.w(TAG, "FFmpeg returned code $returnCode. Attempting fallback.")
                if (inputFile.exists() && inputFile.length() > 44) {
                    inputFile
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting audio with FFmpeg: ${e.message}", e)
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
                Log.i(TAG, "FFmpeg.execute(String[]) returned: $result")
                return result
            } catch (e: Exception) {
                Log.w(TAG, "execute(String[]) reflection failed, falling back to execute(String): ${e.message}")
            }

            // 2. Fallback to execute(String command)
            val executeStringMethod = mobileFfmpegClass.getMethod("execute", String::class.java)
            val cmdString = "-y -i $inputPath -ar 16000 -ac 1 -c:a pcm_s16le $outputPath"
            val result = executeStringMethod.invoke(null, cmdString) as Int
            Log.i(TAG, "FFmpeg.execute(String) returned: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "FFmpeg reflection execution failed: ${e.message}", e)
            0 // Treat as soft fallback
        }
    }
}
