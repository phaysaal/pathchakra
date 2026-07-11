package com.seenslide.teacher.core.network.api

import com.seenslide.teacher.core.network.model.CreateSessionRequest
import com.seenslide.teacher.core.network.model.CreateTalkRequest
import com.seenslide.teacher.core.network.model.NavigateSlideRequest
import com.seenslide.teacher.core.network.model.RenameSessionRequest
import com.seenslide.teacher.core.network.model.SessionListResponse
import com.seenslide.teacher.core.network.model.SessionResponse
import com.seenslide.teacher.core.network.model.TalkListResponse
import com.seenslide.teacher.core.network.model.TalkResponse
import com.seenslide.teacher.core.network.model.UpdateTalkRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SessionApi {

    // --- Session CRUD ---

    @POST("api/cloud/session/create")
    suspend fun createSession(@Body request: CreateSessionRequest): SessionResponse

    @GET("api/cloud/sessions")
    suspend fun listSessions(): SessionListResponse

    @POST("api/cloud/session/{sessionId}/rename")
    suspend fun renameSession(
        @Path("sessionId") sessionId: String,
        @Body request: RenameSessionRequest,
    ): Map<String, Any>

    @DELETE("api/cloud/session/{sessionId}")
    suspend fun deleteSession(@Path("sessionId") sessionId: String): Map<String, Any>

    // --- Talk CRUD ---

    @POST("api/cloud/session/{sessionId}/talks")
    suspend fun createTalk(
        @Path("sessionId") sessionId: String,
        @Body request: CreateTalkRequest,
    ): TalkResponse

    @GET("api/cloud/session/{sessionId}/talks")
    suspend fun listTalks(@Path("sessionId") sessionId: String): TalkListResponse

    @PATCH("api/cloud/talk/{talkId}")
    suspend fun updateTalk(
        @Path("talkId") talkId: String,
        @Body request: UpdateTalkRequest,
    ): TalkResponse

    @DELETE("api/cloud/talk/{talkId}")
    suspend fun deleteTalk(
        @Path("talkId") talkId: String,
        @Query("delete_slides") deleteSlides: Boolean = true,
    ): Map<String, Any>

    // --- Slide ---

    @DELETE("api/cloud/session/{sessionId}/slide/{slideId}")
    suspend fun deleteSlide(
        @Path("sessionId") sessionId: String,
        @Path("slideId") slideId: String,
    ): Map<String, Any>

    // --- Live Navigation ---

    @POST("api/cloud/talk/{talkId}/go-live")
    suspend fun goLive(@Path("talkId") talkId: String): Map<String, Any>

    @POST("api/cloud/talk/{talkId}/navigate")
    suspend fun navigateSlide(
        @Path("talkId") talkId: String,
        @Body request: NavigateSlideRequest,
    ): Map<String, Any>

    @POST("api/cloud/talk/{talkId}/end-live")
    suspend fun endLive(@Path("talkId") talkId: String): Map<String, Any>

    // --- Stroke Recording ---

    @POST("api/cloud/talk/{talkId}/stroke-recording")
    suspend fun uploadStrokeRecording(
        @Path("talkId") talkId: String,
        @Body recording: Map<String, @JvmSuppressWildcards Any>,
    ): Map<String, Any>
}
