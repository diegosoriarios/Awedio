package com.diego.awedio.model

data class WhisperModelDef(
    val id: String,
    val displayName: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val speedLabel: String,
    val minRamGb: Int,
    val recommended: Boolean = false
) {
    val sizeLabel: String
        get() {
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1024.0) {
                String.format(java.util.Locale.US, "~%.1fGB", mb / 1024.0)
            } else {
                String.format(java.util.Locale.US, "~%dMB", mb.toInt())
            }
        }
}

object WhisperModels {

    const val PREFS_NAME = "awedio_prefs"
    const val PREF_SELECTED_MODEL_ID = "selected_model_id"

    val BASE = WhisperModelDef(
        id = "base",
        displayName = "Whisper Base",
        fileName = "ggml-base.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
        sizeBytes = 147_951_465L,
        speedLabel = "Rápido",
        minRamGb = 1
    )

    val SMALL_Q5_1 = WhisperModelDef(
        id = "small-q5_1",
        displayName = "Whisper Small (Q5_1)",
        fileName = "ggml-small-q5_1.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
        sizeBytes = 190_085_487L,
        speedLabel = "Médio",
        minRamGb = 2
    )

    val LARGE_V3_TURBO_Q5_0 = WhisperModelDef(
        id = "large-v3-turbo-q5_0",
        displayName = "Whisper Large-v3 Turbo (Q5_0)",
        fileName = "ggml-large-v3-turbo-q5_0.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin",
        sizeBytes = 574_041_195L,
        speedLabel = "Bom",
        minRamGb = 3,
        recommended = true
    )

    val LARGE_V3_Q5_0 = WhisperModelDef(
        id = "large-v3-q5_0",
        displayName = "Whisper Large-v3 (Q5_0)",
        fileName = "ggml-large-v3-q5_0.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-q5_0.bin",
        sizeBytes = 1_081_140_203L,
        speedLabel = "Muito lento",
        minRamGb = 4
    )

    val ALL: List<WhisperModelDef> = listOf(BASE, SMALL_Q5_1, LARGE_V3_TURBO_Q5_0, LARGE_V3_Q5_0)

    val DEFAULT: WhisperModelDef = BASE

    fun byId(id: String?): WhisperModelDef = ALL.find { it.id == id } ?: DEFAULT
}
