package com.seenslide.teacher.core.network.api

import com.seenslide.teacher.core.network.model.CreateSessionRequest
import com.seenslide.teacher.core.network.model.SessionResponse
import com.seenslide.teacher.core.network.model.StartTalkRequest
import com.seenslide.teacher.core.network.model.TalkResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface SessionApi {

    @POST("api/cloud/session")
    suspend fun createSession(@Body request: CreateSessionRequest): SessionResponse

    @GET("api/cloud/sessions")
    suspend fun listSessions(): List<SessionResponse>

    @POST("api/cloud/session/{sessionId}/start-talk")
    suspend fun startTalk(
        @Path("sessionId") sessionId: String,
        @Body request: StartTalkRequest,
    ): TalkResponse
}
