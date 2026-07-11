package com.seenslide.teacher.core.data

import android.util.Log
import com.seenslide.teacher.core.database.SlideDao
import com.seenslide.teacher.core.database.toEntity
import com.seenslide.teacher.core.database.toSlideInfo
import com.seenslide.teacher.core.network.NetworkMonitor
import com.seenslide.teacher.core.network.api.SlideApi
import com.seenslide.teacher.core.network.model.SlideInfo
import com.seenslide.teacher.core.network.model.SlideUploadResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SlideRepository @Inject constructor(
    private val slideApi: SlideApi,
    private val networkMonitor: NetworkMonitor,
    private val uploadQueue: SlideUploadQueue,
    private val slideDao: SlideDao,
) {
    companion object {
        private const val TAG = "SlideRepository"
    }

    /**
     * Upload a slide image. If the network is unavailable or upload fails,
     * queues to disk and throws [SlideQueuedLocallyException] so the caller
     * can show "saved locally" instead of a hard error.
     */
    suspend fun uploadSlide(
        sessionId: String,
        talkId: String?,
        imageBytes: ByteArray,
        replaceSlideNumber: Int? = null,
    ): SlideUploadResponse {
        Log.d(TAG, "uploadSlide: session=$sessionId, talk=$talkId, bytes=${imageBytes.size}, replace=$replaceSlideNumber")

        val slideNumber = replaceSlideNumber ?: getNextSlideNumber(sessionId, talkId)

        // If offline, queue immediately
        if (!networkMonitor.isOnline.value) {
            queueUpload(sessionId, talkId, imageBytes, slideNumber, replaceSlideNumber)
            throw SlideQueuedLocallyException(slideNumber)
        }

        return try {
            val body = imageBytes.toRequestBody("image/jpeg".toMediaType())
            val part = MultipartBody.Part.createFormData("file", "slide.jpg", body)
            Log.d(TAG, "uploadSlide: slideNumber=$slideNumber")

            val response = if (talkId != null) {
                slideApi.uploadSlideToTalk(talkId, slideNumber, part)
            } else {
                slideApi.uploadSlideToSession(sessionId, slideNumber, part)
            }
            Log.d(TAG, "uploadSlide: success slideId=${response.slideId}")
            response
        } catch (e: Exception) {
            // Network error during upload — queue for retry
            Log.w(TAG, "Upload failed, queuing for retry: ${e.message}")
            queueUpload(sessionId, talkId, imageBytes, slideNumber, replaceSlideNumber)
            throw SlideQueuedLocallyException(slideNumber)
        }
    }

    /**
     * Get slides: tries network first, falls back to local cache.
     * Caches the result for offline use.
     */
    suspend fun getSlides(talkId: String): List<SlideInfo> {
        return try {
            val slides = slideApi.getTalkSlides(talkId)
            // Cache for offline
            slideDao.replaceAllForTalk(talkId, slides.map { it.toEntity(talkId) })
            slides
        } catch (e: Exception) {
            // Offline — read from cache
            val cached = slideDao.getByTalk(talkId)
            if (cached.isNotEmpty()) {
                Log.d(TAG, "Serving ${cached.size} cached slides for talk $talkId")
                cached.map { it.toSlideInfo() }
            } else {
                throw e // No cache, propagate the error
            }
        }
    }

    private suspend fun getNextSlideNumber(sessionId: String, talkId: String?): Int {
        return try {
            val slides = if (talkId != null) {
                slideApi.getTalkSlides(talkId)
            } else {
                slideApi.getSessionSlides(sessionId).slides
            }
            (slides.maxOfOrNull { it.slideNumber } ?: 0) + 1
        } catch (e: Exception) {
            // If we can't fetch slides, use timestamp-based number to avoid collisions
            Log.w(TAG, "Could not fetch slides for numbering, using fallback")
            ((System.currentTimeMillis() / 1000) % 10000).toInt()
        }
    }

    private suspend fun queueUpload(
        sessionId: String,
        talkId: String?,
        imageBytes: ByteArray,
        slideNumber: Int,
        replaceSlideNumber: Int?,
    ) {
        uploadQueue.enqueue(sessionId, talkId, imageBytes, slideNumber, replaceSlideNumber)
        Log.d(TAG, "Slide queued for later upload: slideNumber=$slideNumber")
    }
}

/**
 * Thrown when a slide was saved locally for later upload instead of uploaded immediately.
 * ViewModels should catch this to show "Saved locally" instead of an error.
 */
class SlideQueuedLocallyException(val slideNumber: Int) : Exception("Slide saved locally for upload when online")
