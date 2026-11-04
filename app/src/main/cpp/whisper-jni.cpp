#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Forward declarations of whisper C functions if whisper.h is included
#if __has_include("whisper.h")
#include "whisper.h"
#else
// Declarations for whisper C API when linked externally or dynamically
struct whisper_context;
struct whisper_context_params {
    bool use_gpu;
    bool flash_attn;
    int gpu_device;
};

extern "C" {
    struct whisper_context_params whisper_context_default_params();
    struct whisper_context * whisper_init_from_file_with_params(const char * path_model, struct whisper_context_params params);
    void whisper_free(struct whisper_context * ctx);
    int whisper_full(struct whisper_context * ctx, struct whisper_full_params params, const float * samples, int n_samples);
    int whisper_full_n_segments(struct whisper_context * ctx);
    const char * whisper_full_get_segment_text(struct whisper_context * ctx, int i_segment);
    const char * whisper_print_system_info(void);
}
#endif

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_diego_awedio_whisper_WhisperLib_initContext(
        JNIEnv *env,
        jobject thiz,
        jstring model_path_str) {
    if (model_path_str == nullptr) {
        LOGE("Model path is null");
        return 0;
    }

    const char *model_path = env->GetStringUTFChars(model_path_str, nullptr);
    LOGI("Initializing whisper context from: %s", model_path);

#if __has_include("whisper.h")
    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    cparams.flash_attn = true;
    struct whisper_context *ctx = whisper_init_from_file_with_params(model_path, cparams);
#else
    // Fallback if full native whisper library is dynamically linked
    struct whisper_context *ctx = nullptr;
#endif

    env->ReleaseStringUTFChars(model_path_str, model_path);

    if (ctx == nullptr) {
        LOGE("Failed to create whisper context");
        return 0;
    }

    LOGI("Whisper context initialized successfully at %p", ctx);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_diego_awedio_whisper_WhisperLib_freeContext(
        JNIEnv *env,
        jobject thiz,
        jlong context_ptr) {
    if (context_ptr != 0) {
        struct whisper_context *ctx = reinterpret_cast<struct whisper_context *>(context_ptr);
        LOGI("Freeing whisper context at %p", ctx);
#if __has_include("whisper.h")
        whisper_free(ctx);
#endif
    }
}

JNIEXPORT jstring JNICALL
Java_com_diego_awedio_whisper_WhisperLib_transcribeBuffer(
        JNIEnv *env,
        jobject thiz,
        jlong context_ptr,
        jfloatArray samples_array,
        jstring lang_str,
        jint beam_size) {
    if (context_ptr == 0) {
        LOGE("Cannot transcribe: Context pointer is null");
        return env->NewStringUTF("Error: Context is null");
    }

    if (samples_array == nullptr) {
        LOGE("Cannot transcribe: Samples array is null");
        return env->NewStringUTF("Error: Samples array is null");
    }

    struct whisper_context *ctx = reinterpret_cast<struct whisper_context *>(context_ptr);

    jsize n_samples = env->GetArrayLength(samples_array);
    jfloat *samples = env->GetFloatArrayElements(samples_array, nullptr);

    const char *lang = lang_str ? env->GetStringUTFChars(lang_str, nullptr) : "pt";

    LOGI("Starting whisper transcription for %d audio samples with language: %s", n_samples, lang);

    std::string result_text = "";

#if __has_include("whisper.h")
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = lang;
    params.offset_ms = 0;

    unsigned int hw_threads = std::thread::hardware_concurrency();
    params.n_threads = hw_threads > 0 ? std::min(8u, hw_threads) : 4;
    LOGI("Using %d threads", params.n_threads);

    // Greedy decoding without temperature fallback: avoids re-decoding
    // segments at rising temperatures (major speedup, minimal quality loss).
    params.temperature = 0.0f;
    params.temperature_inc = 0.0f;

    if (beam_size > 1) {
        params.strategy = WHISPER_SAMPLING_BEAM_SEARCH;
        params.beam_search.beam_size = beam_size;
        LOGI("Using beam search with beam size %d", beam_size);
    } else {
        params.greedy.best_of = 1;
    }

    int ret = whisper_full(ctx, params, samples, n_samples);

    if (ret == 0) {
        int n_segments = whisper_full_n_segments(ctx);
        for (int i = 0; i < n_segments; ++i) {
            const char *text = whisper_full_get_segment_text(ctx, i);
            if (text != nullptr) {
                result_text += text;
            }
        }
    } else {
        LOGE("whisper_full failed with code %d", ret);
        result_text = "Error during transcription processing.";
    }
#else
    result_text = "Whisper C API header not included at compile time.";
#endif

    env->ReleaseFloatArrayElements(samples_array, samples, JNI_ABORT);
    if (lang_str) {
        env->ReleaseStringUTFChars(lang_str, lang);
    }

    LOGI("Transcription result: %s", result_text.c_str());
    return env->NewStringUTF(result_text.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_diego_awedio_whisper_WhisperLib_getSystemInfo(
        JNIEnv *env,
        jobject thiz) {
#if __has_include("whisper.h")
    return env->NewStringUTF(whisper_print_system_info());
#else
    return env->NewStringUTF("whisper.cpp JNI wrapper initialized");
#endif
}

}
