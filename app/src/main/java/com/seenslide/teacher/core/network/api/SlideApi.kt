package com.seenslide.teacher.core.network.api

import com.seenslide.teacher.core.network.model.SlideListResponse
import com.seenslide.teacher.core.network.model.SlideUploadResponse
import okhttp3.MultipartBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface SlideApi {

    @Multipart
    @POST("api/cloud/talk/{talkId}/upload-slide")
    suspend fun uploadSlideToTalk(
        @Path("talkId") talkId: String,
        @Part file: MultipartBody.Part,
    ): SlideUploadResponse

    @Multipart
    @POST("api/cloud/session/{sessionId}/upload-slide")
    suspend fun uploadSlideToSession(
        @Path("sessionId") sessionId: String,
        @Part file: MultipartBody.Part,
    ): SlideUploadResponse

    @GET("api/cloud/talk/{talkId}/slides")
    suspend fun getTalkSlides(@Path("talkId") talkId: String): SlideListResponse
}
