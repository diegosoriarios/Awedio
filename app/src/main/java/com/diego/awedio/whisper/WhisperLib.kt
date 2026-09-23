package com.diego.awedio.whisper

import com.diego.awedio.util.AppLogger
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WhisperLib {

    external fun initContext(modelPath: String): Long
    external fun freeContext(contextPtr: Long)
    external fun transcribeBuffer(contextPtr: Long, samples: FloatArray, language: String): String

    companion object {
        private const val TAG = "WhisperLib"

        init {
            try {
                System.loadLibrary("whisper-jni")
                AppLogger.i(TAG, "whisper-jni native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                AppLogger.e(TAG, "Failed to load whisper-jni native library: ${e.message}", e)
            }
        }

        fun readWavSamples(wavFile: File): FloatArray {
            if (!wavFile.exists() || wavFile.length() <= 44) {
                AppLogger.e(TAG, "WAV file does not exist or is too short: ${wavFile.absolutePath}")
                return FloatArray(0)
            }

            return try {
                FileInputStream(wavFile).use { inputStream ->
                    inputStream.skip(44) // Skip 44-byte WAV header
                    val audioData = inputStream.readBytes()
                    val shortBuffer = ByteBuffer.wrap(audioData)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer()

                    val floatSamples = FloatArray(shortBuffer.remaining())
                    for (i in floatSamples.indices) {
                        floatSamples[i] = shortBuffer.get(i) / 32768.0f
                    }
                    AppLogger.i(TAG, "Extracted ${floatSamples.size} audio float samples from ${wavFile.name}")
                    floatSamples
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error reading WAV samples: ${e.message}", e)
                FloatArray(0)
            }
        }
    }
}
