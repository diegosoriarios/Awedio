package com.diego.awedio.whisper

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WhisperLib {

    external fun initContext(modelPath: String): Long
    external fun freeContext(contextPtr: Long)
    external fun transcribeBuffer(contextPtr: Long, samples: FloatArray, language: String): String
    external fun getSystemInfo(): String

    companion object {
        private const val TAG = "WhisperLib"

        init {
            try {
                System.loadLibrary("whisper-jni")
                Log.i(TAG, "whisper-jni native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load whisper-jni native library: ${e.message}")
            }
        }

        /**
         * Reads a 16kHz Mono 16-bit PCM WAV file and converts it to normalized FloatArray [-1.0, 1.0].
         */
        fun readWavSamples(wavFile: File): FloatArray {
            if (!wavFile.exists() || wavFile.length() <= 44) {
                Log.e(TAG, "WAV file does not exist or is too short: ${wavFile.absolutePath}")
                return FloatArray(0)
            }

            return try {
                FileInputStream(wavFile).use { inputStream ->
                    val header = ByteArray(44)
                    inputStream.read(header) // Skip 44-byte WAV header

                    val audioData = inputStream.readBytes()
                    val shortBuffer = ByteBuffer.wrap(audioData)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer()

                    val floatSamples = FloatArray(shortBuffer.remaining())
                    for (i in floatSamples.indices) {
                        floatSamples[i] = shortBuffer.get(i) / 32768.0f
                    }
                    Log.i(TAG, "Successfully read ${floatSamples.size} audio samples (~${floatSamples.size / 16000} seconds)")
                    floatSamples
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading WAV file samples: ${e.message}", e)
                FloatArray(0)
            }
        }
    }
}
