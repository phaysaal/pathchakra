package com.seenslide.teacher.core.data

import android.content.Context
import android.util.Log
import com.seenslide.teacher.core.network.NetworkMonitor
import com.seenslide.teacher.core.network.api.SlideApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents a pending slide upload persisted to disk.
 */
data class PendingUpload(
    val id: String,
    val sessionId: String,
    val talkId: String?,
    val slideNumber: Int,
    val replaceSlideNumber: Int?,
)

/**
 * Disk-backed queue for slide uploads that failed due to network issues.
 * Automatically drains when connectivity is restored.
 */
@Singleton
class SlideUploadQueue @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkMonitor: NetworkMonitor,
    private val slideApi: SlideApi,
) {
    companion object {
        private const val TAG = "SlideUploadQueue"
        private const val QUEUE_DIR = "upload_queue"
        private const val META_SUFFIX = ".json"
        private const val DATA_SUFFIX = ".jpg"
    }

    private val queueDir: File by lazy {
        File(context.filesDir, QUEUE_DIR).also { it.mkdirs() }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount

    private val _lastEvent = MutableStateFlow<UploadQueueEvent?>(null)
    val lastEvent: StateFlow<UploadQueueEvent?> = _lastEvent

    init {
        _pendingCount.value = countPendingFiles()

        // Observe network: when online, drain the queue
        networkMonitor.isOnline
            .filter { it }
            .onEach { drainQueue() }
            .launchIn(scope)
    }

    /**
     * Enqueue a slide upload for later retry.
     * Persists image bytes and metadata to disk.
     */
    suspend fun enqueue(
        sessionId: String,
        talkId: String?,
        imageBytes: ByteArray,
        slideNumber: Int,
        replaceSlideNumber: Int?,
    ): String = mutex.withLock {
        val id = "${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}"

        val dataFile = File(queueDir, "$id$DATA_SUFFIX")
        dataFile.writeBytes(imageBytes)

        val metaFile = File(queueDir, "$id$META_SUFFIX")
        val meta = JSONObject().apply {
            put("id", id)
            put("sessionId", sessionId)
            put("talkId", talkId ?: JSONObject.NULL)
            put("slideNumber", slideNumber)
            put("replaceSlideNumber", replaceSlideNumber ?: JSONObject.NULL)
        }
        metaFile.writeText(meta.toString())

        _pendingCount.value = countPendingFiles()
        Log.d(TAG, "Enqueued upload: id=$id, pending=${_pendingCount.value}")
        _lastEvent.value = UploadQueueEvent.SavedLocally(id, slideNumber)
        id
    }

    /**
     * Trigger queue drain. Called automatically on network restore.
     */
    fun drainQueue() {
        scope.launch { drainQueueInternal() }
    }

    private suspend fun drainQueueInternal() = mutex.withLock {
        val metaFiles = queueDir.listFiles { f -> f.name.endsWith(META_SUFFIX) }
            ?.sortedBy { it.name }
            ?: return

        if (metaFiles.isEmpty()) return
        Log.d(TAG, "Draining queue: ${metaFiles.size} pending uploads")

        for (metaFile in metaFiles) {
            if (!networkMonitor.isOnline.value) {
                Log.d(TAG, "Network lost during drain, stopping")
                break
            }

            val pending = readMeta(metaFile) ?: continue
            val dataFile = File(queueDir, "${pending.id}$DATA_SUFFIX")
            if (!dataFile.exists()) {
                metaFile.delete()
                continue
            }

            try {
                val imageBytes = dataFile.readBytes()
                val body = imageBytes.toRequestBody("image/jpeg".toMediaType())
                val part = MultipartBody.Part.createFormData("file", "slide.jpg", body)
                val slideNum = pending.replaceSlideNumber ?: pending.slideNumber

                if (pending.talkId != null) {
                    slideApi.uploadSlideToTalk(pending.talkId, slideNum, part)
                } else {
                    slideApi.uploadSlideToSession(pending.sessionId, slideNum, part)
                }

                dataFile.delete()
                metaFile.delete()
                Log.d(TAG, "Queued upload ${pending.id} succeeded")
                _lastEvent.value = UploadQueueEvent.Uploaded(pending.id, pending.slideNumber)
            } catch (e: Exception) {
                Log.w(TAG, "Queued upload ${pending.id} still failing: ${e.message}")
                break
            }
        }

        _pendingCount.value = countPendingFiles()
    }

    private fun readMeta(file: File): PendingUpload? {
        return try {
            val json = JSONObject(file.readText())
            PendingUpload(
                id = json.getString("id"),
                sessionId = json.getString("sessionId"),
                talkId = json.optString("talkId").takeIf { it != "null" && it.isNotEmpty() },
                slideNumber = json.getInt("slideNumber"),
                replaceSlideNumber = json.optInt("replaceSlideNumber", -1).takeIf { it > 0 },
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read queue metadata: ${file.name}", e)
            file.delete()
            null
        }
    }

    private fun countPendingFiles(): Int {
        return queueDir.listFiles { f -> f.name.endsWith(META_SUFFIX) }?.size ?: 0
    }
}

sealed class UploadQueueEvent {
    data class SavedLocally(val id: String, val slideNumber: Int) : UploadQueueEvent()
    data class Uploaded(val id: String, val slideNumber: Int) : UploadQueueEvent()
}
