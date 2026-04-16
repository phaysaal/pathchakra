package com.seenslide.teacher.core.media

import android.util.Log
import com.seenslide.teacher.core.network.api.VoiceApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the voice streaming lifecycle:
 * 1. Starts a recording session on the server
 * 2. Periodically flushes audio chunks and uploads them
 * 3. Sends slide markers when slides change
 * 4. Stops and finalizes the recording
 *
 * Mirrors the desktop app's VoiceCloudUploader behavior.
 */
@Singleton
class VoiceStreamingService @Inject constructor(
    private val voiceApi: VoiceApi,
    private val audioRecorder: AudioChunkRecorder,
) {
    companion object {
        private const val TAG = "VoiceStreaming"
        private const val CHUNK_INTERVAL_MS = 3000L // Flush every 3 seconds
    }

    private var recordingId: String? = null
    private var chunkJob: Job? = null
    private var chunkNumber = 0
    private var scope: CoroutineScope? = null

    val isStreaming: Boolean get() = recordingId != null && audioRecorder.isRecording

    /**
     * Start voice recording and streaming.
     * Call from a coroutine scope.
     */
    suspend fun start(sessionId: String, talkId: String?, coroutineScope: CoroutineScope): Boolean {
        try {
            // Start server-side recording session
            val response = voiceApi.startRecording(
                sessionId = sessionId,
                audioFormat = "raw", // PCM — server handles it
                talkId = talkId,
            )
            recordingId = response.recordingId
            chunkNumber = 0
            scope = coroutineScope

            // Start local audio capture
            val started = audioRecorder.start()
            if (!started) {
                Log.e(TAG, "Failed to start local audio capture")
                return false
            }

            // Start periodic chunk upload
            chunkJob = coroutineScope.launch(Dispatchers.IO) {
                while (isActive && audioRecorder.isRecording) {
                    delay(CHUNK_INTERVAL_MS)
                    uploadPendingChunk()
                }
            }

            Log.d(TAG, "Voice streaming started, recordingId=$recordingId")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start voice streaming", e)
            return false
        }
    }

    /**
     * Send a slide marker at the current audio position.
     */
    suspend fun addSlideMarker(slideNumber: Int) {
        val rid = recordingId ?: return
        val timestamp = audioRecorder.elapsedSeconds
        try {
            voiceApi.addMarker(rid, slideNumber, timestamp)
            Log.d(TAG, "Marker added: slide=$slideNumber, t=${timestamp}s")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to add marker", e)
        }
    }

    /**
     * Stop recording and finalize.
     */
    suspend fun stop() {
        val rid = recordingId ?: return
        val duration = audioRecorder.elapsedSeconds

        // Stop chunk upload loop
        chunkJob?.cancel()
        chunkJob = null

        // Stop local recording — get final chunk
        val finalChunk = audioRecorder.stop()

        // Upload final chunk if any
        if (finalChunk != null && finalChunk.isNotEmpty()) {
            uploadChunk(rid, finalChunk)
        }

        // Tell server we're done
        try {
            voiceApi.stopRecording(rid, duration)
            Log.d(TAG, "Voice streaming stopped, duration=${duration}s")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop server recording", e)
        }

        recordingId = null
        scope = null
    }

    private suspend fun uploadPendingChunk() {
        val rid = recordingId ?: return
        val chunk = audioRecorder.flushChunk() ?: return
        if (chunk.isEmpty()) return
        uploadChunk(rid, chunk)
    }

    private suspend fun uploadChunk(rid: String, data: ByteArray) {
        try {
            val body = data.toRequestBody("application/octet-stream".toMediaType())
            val part = MultipartBody.Part.createFormData(
                "file",
                "chunk_${chunkNumber}.pcm",
                body,
            )
            voiceApi.uploadChunk(rid, part)
            chunkNumber++
            Log.d(TAG, "Chunk #$chunkNumber uploaded: ${data.size} bytes")
        } catch (e: Exception) {
            Log.w(TAG, "Chunk upload failed (will retry next cycle)", e)
        }
    }
}
