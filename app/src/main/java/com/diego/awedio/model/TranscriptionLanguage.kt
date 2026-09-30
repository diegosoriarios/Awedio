package com.diego.awedio.model

import androidx.annotation.StringRes
import com.diego.awedio.R

enum class TranscriptionLanguage(
    val prefValue: String,
    @StringRes val labelRes: Int
) {
    DEVICE("device", R.string.lang_device),
    AUTO("auto", R.string.lang_auto),
    ENGLISH("en", R.string.lang_en),
    PORTUGUESE("pt", R.string.lang_pt),
    SPANISH("es", R.string.lang_es);

    companion object {
        const val PREF_KEY = "transcription_language"

        private val WHISPER_CODES = setOf("en", "pt", "es")

        fun fromPref(value: String?): TranscriptionLanguage =
            entries.firstOrNull { it.prefValue == value } ?: DEVICE

        fun whisperCode(language: TranscriptionLanguage, deviceLanguage: String): String =
            when (language) {
                AUTO -> "auto"
                DEVICE -> if (deviceLanguage in WHISPER_CODES) deviceLanguage else "auto"
                ENGLISH -> "en"
                PORTUGUESE -> "pt"
                SPANISH -> "es"
            }
    }
}
