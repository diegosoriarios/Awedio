#ifndef WHISPER_H
#define WHISPER_H

#include <stdbool.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

struct whisper_context;

enum whisper_sampling_strategy {
    WHISPER_SAMPLING_GREEDY = 0,
    WHISPER_SAMPLING_BEAM_SEARCH = 1,
};

struct whisper_context_params {
    bool use_gpu;
    bool flash_attn;
    int gpu_device;
};

struct whisper_full_params {
    enum whisper_sampling_strategy strategy;

    int n_threads;
    int n_max_text_ctx;
    int offset_ms;
    int duration_ms;

    bool translate;
    bool no_context;
    bool no_timestamps;
    bool single_segment;
    bool print_special;
    bool print_progress;
    bool print_realtime;
    bool print_timestamps;

    const char * language;
    bool suppress_blank;
    bool suppress_non_speech_tokens;

    float temperature;
    float max_initial_ts;
    float length_penalty;

    float temperature_inc;
    float entropy_thold;
    float logprob_thold;
    float no_speech_thold;
};

struct whisper_context_params whisper_context_default_params(void);
struct whisper_full_params whisper_full_default_params(enum whisper_sampling_strategy strategy);

struct whisper_context * whisper_init_from_file_with_params(
        const char * path_model,
        struct whisper_context_params params);

void whisper_free(struct whisper_context * ctx);

int whisper_full(
        struct whisper_context * ctx,
        struct whisper_full_params params,
        const float * samples,
        int n_samples);

int whisper_full_n_segments(struct whisper_context * ctx);
const char * whisper_full_get_segment_text(struct whisper_context * ctx, int i_segment);
const char * whisper_print_system_info(void);

#ifdef __cplusplus
}
#endif

#endif // WHISPER_H
