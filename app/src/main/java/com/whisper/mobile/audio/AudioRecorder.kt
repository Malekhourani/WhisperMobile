package com.whisper.mobile.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AudioRecorder(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private val audioBuffer = mutableListOf<Short>()

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun start(): Boolean {
        if (_isRecording.value) return false
        if (!hasPermission()) return false

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            return false
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )
        } catch (_: SecurityException) {
            return false
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            audioRecord = null
            return false
        }

        audioBuffer.clear()
        audioRecord?.startRecording()
        _isRecording.value = true

        // Start reading audio in background
        Thread {
            val buffer = ShortArray(bufferSize / 2)
            while (_isRecording.value) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    synchronized(audioBuffer) {
                        for (i in 0 until read) {
                            audioBuffer.add(buffer[i])
                        }
                    }
                }
            }
        }.start()

        return true
    }

    suspend fun stop(): FloatArray = withContext(Dispatchers.Default) {
        _isRecording.value = false

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        val samples: List<Short>
        synchronized(audioBuffer) {
            samples = audioBuffer.toList()
            audioBuffer.clear()
        }

        // Convert 16-bit PCM to float [-1.0, 1.0]
        FloatArray(samples.size) { i ->
            samples[i].toFloat() / Short.MAX_VALUE
        }
    }

    fun release() {
        _isRecording.value = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}
