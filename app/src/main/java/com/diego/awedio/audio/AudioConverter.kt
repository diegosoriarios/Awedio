package com.diego.awedio.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.diego.awedio.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioConverter {
    private const val TAG = "AudioConverter"
    private const val TARGET_SAMPLE_RATE = 16000
    private const val TIMEOUT_US = 10_000L

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

            AppLogger.i(TAG, "Decoding audio to PCM 16kHz mono via MediaCodec: ${inputFile.absolutePath}")

            val pcmShorts = decodeToMonoPcm(inputFile)
            if (pcmShorts == null || pcmShorts.isEmpty()) {
                AppLogger.e(TAG, "Audio decoding produced no PCM samples.")
                return@withContext null
            }

            AppLogger.i(TAG, "Decoded ${pcmShorts.size} mono samples; writing WAV...")
            writeWav16kMono(outputFile, pcmShorts)
            AppLogger.i(TAG, "FFmpeg-free conversion succeeded! Output: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
            outputFile
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error during audio conversion: ${e.message}", e)
            null
        }
    }

    private fun decodeToMonoPcm(inputFile: File): ShortArray? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(inputFile.absolutePath)

            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || inputFormat == null) {
                AppLogger.e(TAG, "No audio track found in input file: ${inputFile.absolutePath}")
                return null
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
            AppLogger.i(TAG, "Audio track found: mime=$mime, sampleRate=${inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)}, channels=${inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)}")

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            val pcmBytes = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var outputSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var outputChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            while (!outputDone) {
                if (!inputDone) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                                AppLogger.i(TAG, "Extractor reached end of stream.")
                            } else {
                                decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = decoder.outputFormat
                        outputSampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        outputChannels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        AppLogger.i(TAG, "Decoder output format changed: sampleRate=$outputSampleRate, channels=$outputChannels")
                    }
                    outputBufferIndex >= 0 -> {
                        val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            pcmBytes.write(outputBuffer.array(), outputBuffer.arrayOffset() + bufferInfo.offset, bufferInfo.size)
                        }
                        decoder.releaseOutputBuffer(outputBufferIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            val allBytes = pcmBytes.toByteArray()
            AppLogger.i(TAG, "Decoder produced ${allBytes.size} PCM bytes (sampleRate=$outputSampleRate, channels=$outputChannels).")
            if (allBytes.isEmpty()) return null

            var shorts = ShortArray(allBytes.size / 2)
            ByteBuffer.wrap(allBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)

            if (outputChannels > 1) {
                shorts = downmixToMono(shorts, outputChannels)
            }
            if (outputSampleRate != TARGET_SAMPLE_RATE) {
                shorts = resample(shorts, outputSampleRate, TARGET_SAMPLE_RATE)
            }
            return shorts
        } catch (e: Exception) {
            AppLogger.e(TAG, "MediaCodec decoding failed: ${e.message}", e)
            return null
        } finally {
            try {
                decoder?.stop()
            } catch (_: Exception) {
            }
            try {
                decoder?.release()
            } catch (_: Exception) {
            }
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun downmixToMono(samples: ShortArray, channels: Int): ShortArray {
        val frameCount = samples.size / channels
        val mono = ShortArray(frameCount)
        for (frame in 0 until frameCount) {
            var sum = 0
            for (ch in 0 until channels) {
                sum += samples[frame * channels + ch].toInt()
            }
            mono[frame] = (sum / channels).toShort()
        }
        AppLogger.i(TAG, "Downmixed ${channels}ch to mono: $frameCount frames.")
        return mono
    }

    private fun resample(samples: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (fromRate <= 0 || samples.isEmpty()) return samples
        val ratio = fromRate.toDouble() / toRate.toDouble()
        val outLength = (samples.size / ratio).toLong().toInt()
        val out = ShortArray(outLength)
        for (i in 0 until outLength) {
            val srcPos = i * ratio
            val srcIndex = srcPos.toInt()
            val nextIndex = (srcIndex + 1).coerceAtMost(samples.size - 1)
            val frac = srcPos - srcIndex
            val a = samples[srcIndex].toInt()
            val b = samples[nextIndex].toInt()
            out[i] = (a + (b - a) * frac).toInt().toShort()
        }
        AppLogger.i(TAG, "Resampled from ${fromRate}Hz to ${toRate}Hz: ${samples.size} -> $outLength samples.")
        return out
    }

    private fun writeWav16kMono(outputFile: File, samples: ShortArray) {
        val pcmBytes = ByteArray(samples.size * 2)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(samples)

        val totalDataLen = pcmBytes.size + 36
        FileOutputStream(outputFile).use { out ->
            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            out.write(intToLeBytes(totalDataLen))
            out.write("WAVE".toByteArray(Charsets.US_ASCII))
            out.write("fmt ".toByteArray(Charsets.US_ASCII))
            out.write(intToLeBytes(16))
            out.write(shortToLeBytes(1))
            out.write(shortToLeBytes(1))
            out.write(intToLeBytes(TARGET_SAMPLE_RATE))
            out.write(intToLeBytes(TARGET_SAMPLE_RATE * 2))
            out.write(shortToLeBytes(2))
            out.write(shortToLeBytes(16))
            out.write("data".toByteArray(Charsets.US_ASCII))
            out.write(intToLeBytes(pcmBytes.size))
            out.write(pcmBytes)
        }
    }

    private fun intToLeBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xff).toByte(),
            ((value shr 8) and 0xff).toByte(),
            ((value shr 16) and 0xff).toByte(),
            ((value shr 24) and 0xff).toByte()
        )
    }

    private fun shortToLeBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xff).toByte(),
            ((value shr 8) and 0xff).toByte()
        )
    }
}
