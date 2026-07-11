package com.seenslide.teacher.core.network.api

import com.seenslide.teacher.core.network.model.SlideListResponse
import com.seenslide.teacher.core.network.model.SlideInfo
import com.seenslide.teacher.core.network.model.SlideUploadResponse
import com.seenslide.teacher.core.network.model.SwapSlidesResponse
import okhttp3.MultipartBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface SlideApi {

    @Multipart
    @POST("api/cloud/talk/{talkId}/upload-slide")
    suspend fun uploadSlideToTalk(
        @Path("talkId") talkId: String,
        @Query("slide_number") slideNumber: Int,
        @Part file: MultipartBody.Part,
    ): SlideUploadResponse

    @Multipart
    @POST("api/cloud/session/{sessionId}/upload-slide")
    suspend fun uploadSlideToSession(
        @Path("sessionId") sessionId: String,
        @Query("slide_number") slideNumber: Int,
        @Part file: MultipartBody.Part,
    ): SlideUploadResponse

    @GET("api/cloud/talk/{talkId}/slides")
    suspend fun getTalkSlides(@Path("talkId") talkId: String): List<SlideInfo>

    @GET("api/cloud/session/{sessionId}/slides")
    suspend fun getSessionSlides(@Path("sessionId") sessionId: String): SlideListResponse

    @POST("api/cloud/talk/{talkId}/slides/swap")
    suspend fun swapSlides(
        @Path("talkId") talkId: String,
        @Query("slide_a") slideA: Int,
        @Query("slide_b") slideB: Int,
    ): SwapSlidesResponse
}
