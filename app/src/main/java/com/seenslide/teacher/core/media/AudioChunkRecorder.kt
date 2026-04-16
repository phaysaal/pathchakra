package com.seenslide.teacher.core.media

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records audio from the microphone and produces chunks for streaming upload.
 * Uses AudioRecord (low-level) for chunked PCM capture.
 * Chunks are produced as raw PCM bytes (server accepts application/octet-stream).
 *
 * Flow:
 * 1. start() → opens mic, spawns recording thread
 * 2. flushChunk() → returns accumulated PCM bytes since last flush
 * 3. stop() → stops recording, returns final chunk
 */
@Singleton
class AudioChunkRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "AudioChunkRecorder"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var _isRecording = false

    private val chunkBuffer = ByteArrayOutputStream()
    private val chunkLock = Object()

    private var startTimeMs: Long = 0L
    val isRecording: Boolean get() = _isRecording
    val recordingStartTime: Long get() = startTimeMs

    /** Elapsed seconds since recording started. */
    val elapsedSeconds: Double
        get() = if (_isRecording) (System.currentTimeMillis() - startTimeMs) / 1000.0 else 0.0

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(): Boolean {
        if (_isRecording) return true

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size: $bufferSize")
            return false
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                bufferSize * 2,
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            _isRecording = true
            startTimeMs = System.currentTimeMillis()

            synchronized(chunkLock) {
                chunkBuffer.reset()
            }

            // Spawn recording thread
            recordingThread = Thread({
                val buffer = ByteArray(bufferSize)
                while (_isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        synchronized(chunkLock) {
                            chunkBuffer.write(buffer, 0, read)
                        }
                    }
                }
            }, "audio-recorder").apply {
                isDaemon = true
                start()
            }

            Log.d(TAG, "Recording started at $SAMPLE_RATE Hz")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            return false
        }
    }

    /**
     * Flush accumulated audio since last flush.
     * Returns raw PCM bytes (16-bit mono 44100Hz) or null if empty.
     */
    fun flushChunk(): ByteArray? {
        synchronized(chunkLock) {
            if (chunkBuffer.size() == 0) return null
            val data = chunkBuffer.toByteArray()
            chunkBuffer.reset()
            return data
        }
    }

    /**
     * Stop recording and return any remaining audio.
     */
    fun stop(): ByteArray? {
        _isRecording = false

        try {
            recordingThread?.join(3000)
        } catch (_: InterruptedException) {}
        recordingThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null

        val finalChunk = flushChunk()
        Log.d(TAG, "Recording stopped, final chunk: ${finalChunk?.size ?: 0} bytes")
        return finalChunk
    }
}
