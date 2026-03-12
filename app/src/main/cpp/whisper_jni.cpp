#include <jni.h>
#include <string>
#include <android/log.h>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_whisper_mobile_WhisperLib_initContext(
        JNIEnv *env, jobject /* this */,
        jstring model_path) {

    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Loading model from: %s", path);

    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;

    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(model_path, path);

    if (ctx == nullptr) {
        LOGE("Failed to initialize whisper context");
        return 0;
    }

    LOGI("Model loaded successfully");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_whisper_mobile_WhisperLib_transcribe(
        JNIEnv *env, jobject /* this */,
        jlong context_ptr, jfloatArray audio_data, jstring language) {

    auto *ctx = reinterpret_cast<struct whisper_context *>(context_ptr);
    if (ctx == nullptr) {
        return env->NewStringUTF("");
    }

    jfloat *samples = env->GetFloatArrayElements(audio_data, nullptr);
    jsize num_samples = env->GetArrayLength(audio_data);

    const char *lang = env->GetStringUTFChars(language, nullptr);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.single_segment = true;
    params.no_timestamps = true;
    params.n_threads = 4;

    if (strlen(lang) > 0) {
        params.language = lang;
    } else {
        params.language = nullptr;
        params.detect_language = true;
    }

    LOGI("Transcribing %d samples, language: %s", num_samples,
         strlen(lang) > 0 ? lang : "auto");

    int result = whisper_full(ctx, params, samples, num_samples);

    env->ReleaseStringUTFChars(language, lang);
    env->ReleaseFloatArrayElements(audio_data, samples, 0);

    if (result != 0) {
        LOGE("Transcription failed with code: %d", result);
        return env->NewStringUTF("");
    }

    int n_segments = whisper_full_n_segments(ctx);
    std::string text;
    for (int i = 0; i < n_segments; i++) {
        const char *segment_text = whisper_full_get_segment_text(ctx, i);
        text += segment_text;
    }

    LOGI("Transcription result: %s", text.c_str());
    return env->NewStringUTF(text.c_str());
}

JNIEXPORT void JNICALL
Java_com_whisper_mobile_WhisperLib_freeContext(
        JNIEnv *env, jobject /* this */,
        jlong context_ptr) {

    auto *ctx = reinterpret_cast<struct whisper_context *>(context_ptr);
    if (ctx != nullptr) {
        whisper_free(ctx);
        LOGI("Context freed");
    }
}

} // extern "C"
