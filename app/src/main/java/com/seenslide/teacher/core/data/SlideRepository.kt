package com.seenslide.teacher.core.data

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
) {
    suspend fun uploadSlide(sessionId: String, talkId: String?, imageBytes: ByteArray): SlideUploadResponse {
        val body = imageBytes.toRequestBody("image/jpeg".toMediaType())
        val part = MultipartBody.Part.createFormData("file", "slide.jpg", body)

        return if (talkId != null) {
            slideApi.uploadSlideToTalk(talkId, part)
        } else {
            slideApi.uploadSlideToSession(sessionId, part)
        }
    }

    suspend fun getSlides(talkId: String): List<SlideInfo> {
        return slideApi.getTalkSlides(talkId).slides
    }
}
