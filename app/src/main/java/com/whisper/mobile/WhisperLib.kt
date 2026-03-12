package com.whisper.mobile

object WhisperLib {
    init {
        System.loadLibrary("whisper_jni")
    }

    /**
     * Initialize a Whisper context from a model file path.
     * Returns a native pointer (long) to the context, or 0 on failure.
     */
    external fun initContext(modelPath: String): Long

    /**
     * Transcribe audio samples.
     * @param contextPtr Native pointer from initContext
     * @param audioData PCM float samples normalized to [-1, 1] at 16kHz mono
     * @param language "ar" for Arabic, "en" for English, or "" for auto-detect
     * @return Transcribed text
     */
    external fun transcribe(contextPtr: Long, audioData: FloatArray, language: String): String

    /**
     * Free a Whisper context.
     */
    external fun freeContext(contextPtr: Long)
}
