package com.seenslide.teacher.core.media

import android.util.Log
import com.seenslide.teacher.core.network.api.VoiceApi
import com.seenslide.teacher.core.network.model.MarkerItem
import com.seenslide.teacher.core.network.model.SyncMarkersRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the voice streaming lifecycle:
 * 1. Starts a recording session on the server (AAC, real sample rate)
 * 2. Periodically flushes PCM, encodes to ADTS AAC (~25x smaller), and
 *    uploads with an explicit chunk_index so retries can't reorder audio
 * 3. Sends slide markers when slides change (audio-clock timestamps)
 * 4. On stop: drains the queue, bulk-syncs the authoritative marker list,
 *    finalizes — the server merges the chunks into one seekable OGG/Opus
 *
 * Failed chunks are buffered in a retry queue and retried on subsequent
 * flush cycles; compressed AAC means the queue holds minutes of audio in
 * a few hundred KB.
 */
@Singleton
class VoiceStreamingService @Inject constructor(
    private val voiceApi: VoiceApi,
    private val audioRecorder: AudioChunkRecorder,
    private val aacEncoder: AacEncoder,
) {
    companion object {
        private const val TAG = "VoiceStreaming"
        private const val CHUNK_INTERVAL_MS = 1500L
        private const val MAX_PENDING_CHUNKS = 120 // ~3 min at 1.5s/chunk (AAC ≈ 6KB each)
    }

    private data class IndexedChunk(val index: Int, val data: ByteArray, val ext: String)

    private var recordingId: String? = null
    private var chunkJob: Job? = null
    private var nextChunkIndex = 1
    private var scope: CoroutineScope? = null

    /**
     * Wall-clock instant audio capture began (epoch ms), 0 if never started.
     * Survives stop() — stroke recordings rebase their timestamps onto the
     * audio timeline at export using this anchor.
     */
    var voiceStartWallMs: Long = 0L
        private set

    /** Current position on the audio timeline in ms (0 when not streaming). */
    val elapsedMs: Long
        get() = if (audioRecorder.isRecording) (audioRecorder.elapsedSeconds * 1000).toLong() else 0L

    // Retry queue: chunks that failed to upload, ordered oldest-first
    private val pendingChunks = ArrayDeque<IndexedChunk>(MAX_PENDING_CHUNKS)

    // Every marker added this session — bulk-synced at stop so markers
    // lost to network blips mid-lecture are recovered
    private val markers = mutableListOf<MarkerItem>()

    val isStreaming: Boolean get() = recordingId != null && audioRecorder.isRecording

    /**
     * Start voice recording and streaming.
     * Call from a coroutine scope.
     */
    suspend fun start(sessionId: String, talkId: String?, coroutineScope: CoroutineScope): Boolean {
        try {
            // Start local audio capture FIRST so we know the mic works
            // before creating a server-side recording row.
            val started = audioRecorder.start()
            if (!started) {
                Log.e(TAG, "Failed to start local audio capture")
                return false
            }
            voiceStartWallMs = System.currentTimeMillis()

            val response = voiceApi.startRecording(
                sessionId = sessionId,
                audioFormat = "aac",
                talkId = talkId,
                sampleRate = audioRecorder.sampleRate,
                channels = 1,
            )
            recordingId = response.recordingId
            nextChunkIndex = 1
            scope = coroutineScope
            pendingChunks.clear()
            markers.clear()

            // Start periodic chunk upload
            chunkJob = coroutineScope.launch(Dispatchers.IO) {
                while (isActive && audioRecorder.isRecording) {
                    delay(CHUNK_INTERVAL_MS)
                    uploadPendingChunks()
                }
            }

            Log.d(TAG, "Voice streaming started, recordingId=$recordingId")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start voice streaming", e)
            audioRecorder.stop()
            return false
        }
    }

    /**
     * Send a slide marker at the current audio position. Also remembered
     * locally for the authoritative bulk sync at stop.
     */
    suspend fun addSlideMarker(slideNumber: Int) {
        val rid = recordingId ?: return
        val timestamp = audioRecorder.elapsedSeconds
        synchronized(markers) {
            markers.add(MarkerItem(slideNumber, timestamp))
        }
        try {
            voiceApi.addMarker(rid, slideNumber, timestamp)
            Log.d(TAG, "Marker added: slide=$slideNumber, t=${timestamp}s")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to add marker (bulk sync at stop recovers it)", e)
        }
    }

    /**
     * Stop recording and finalize.
     */
    suspend fun stop() {
        val rid = recordingId ?: return

        // Stop chunk upload loop
        chunkJob?.cancel()
        chunkJob = null

        // Duration BEFORE stop (elapsedSeconds survives stop, but be explicit)
        val duration = audioRecorder.elapsedSeconds

        // Stop local recording — get final chunk
        val finalChunk = audioRecorder.stop()
        if (finalChunk != null && finalChunk.isNotEmpty()) {
            enqueuePcm(finalChunk)
        }

        // Drain all remaining pending chunks (best-effort)
        drainAllPending(rid)

        // Authoritative marker reconciliation
        val allMarkers = synchronized(markers) { markers.toList() }
        if (allMarkers.isNotEmpty()) {
            try {
                voiceApi.syncMarkers(rid, SyncMarkersRequest(allMarkers))
                Log.d(TAG, "Markers reconciled: ${allMarkers.size}")
            } catch (e: Exception) {
                Log.w(TAG, "Marker bulk sync failed", e)
            }
        }

        // Tell server we're done — it merges the chunks into one OGG/Opus
        try {
            voiceApi.stopRecording(rid, duration)
            Log.d(TAG, "Voice streaming stopped, duration=${duration}s")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop server recording", e)
        }

        recordingId = null
        scope = null
        pendingChunks.clear()
        synchronized(markers) { markers.clear() }
    }

    /**
     * Flush new audio from recorder into the queue, then try to upload all pending chunks.
     */
    private suspend fun uploadPendingChunks() {
        val rid = recordingId ?: return

        val newPcm = audioRecorder.flushChunk()
        if (newPcm != null && newPcm.isNotEmpty()) {
            enqueuePcm(newPcm)
        }

        drainAllPending(rid)
    }

    /**
     * Encode PCM → ADTS AAC and queue it with a fixed index. Encoding at
     * enqueue time (not upload time) keeps the retry queue compressed.
     * Falls back to raw PCM if the codec fails — the server sniffs.
     */
    private fun enqueuePcm(pcm: ByteArray) {
        val aac = aacEncoder.encode(pcm, audioRecorder.sampleRate, 1)
        val chunk = if (aac != null && aac.isNotEmpty()) {
            IndexedChunk(nextChunkIndex, aac, "aac")
        } else {
            Log.w(TAG, "AAC encode failed — uploading raw PCM chunk")
            IndexedChunk(nextChunkIndex, pcm, "pcm")
        }
        nextChunkIndex += 1
        if (pendingChunks.size >= MAX_PENDING_CHUNKS) {
            val dropped = pendingChunks.poll()
            Log.w(TAG, "Chunk queue full, dropping oldest chunk #${dropped?.index}")
        }
        pendingChunks.offer(chunk)
    }

    /**
     * Attempt to upload all queued chunks. Stops at the first failure
     * so ordering is preserved (chunk_index makes order authoritative
     * server-side regardless).
     */
    private suspend fun drainAllPending(rid: String) {
        while (pendingChunks.isNotEmpty()) {
            val chunk = pendingChunks.peek() ?: break
            if (tryUploadChunk(rid, chunk)) {
                pendingChunks.poll()
            } else {
                Log.d(TAG, "Upload failed, ${pendingChunks.size} chunks queued")
                break
            }
        }
    }

    private suspend fun tryUploadChunk(rid: String, chunk: IndexedChunk): Boolean {
        return try {
            val mediaType = if (chunk.ext == "aac") "audio/aac" else "application/octet-stream"
            val body = chunk.data.toRequestBody(mediaType.toMediaType())
            val part = MultipartBody.Part.createFormData(
                "file",
                "chunk_%04d.%s".format(chunk.index, chunk.ext),
                body,
            )
            voiceApi.uploadChunk(rid, part, chunkIndex = chunk.index)
            Log.d(TAG, "Chunk #${chunk.index} uploaded: ${chunk.data.size} bytes (${chunk.ext})")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Chunk #${chunk.index} upload failed: ${e.message}")
            false
        }
    }
}
