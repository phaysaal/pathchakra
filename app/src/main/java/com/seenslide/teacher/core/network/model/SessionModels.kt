package com.seenslide.teacher.core.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreateSessionRequest(
    @Json(name = "presenter_name") val presenterName: String,
    @Json(name = "presenter_email") val presenterEmail: String,
    @Json(name = "max_slides") val maxSlides: Int = 50,
)

@JsonClass(generateAdapter = true)
data class SessionResponse(
    @Json(name = "session_id") val sessionId: String,
    @Json(name = "presenter_name") val presenterName: String? = null,
    val status: String? = null,
    @Json(name = "total_slides") val totalSlides: Int = 0,
    @Json(name = "viewer_count") val viewerCount: Int = 0,
    @Json(name = "created_at") val createdAt: Double? = null,
    @Json(name = "active_talk_id") val activeTalkId: String? = null,
)

@JsonClass(generateAdapter = true)
data class RenameSessionRequest(
    @Json(name = "presenter_name") val presenterName: String,
)

@JsonClass(generateAdapter = true)
data class CreateTalkRequest(
    val title: String,
    @Json(name = "presenter_name") val presenterName: String? = null,
    val description: String? = null,
)

@JsonClass(generateAdapter = true)
data class TalkResponse(
    @Json(name = "talk_id") val talkId: String,
    @Json(name = "session_id") val sessionId: String,
    val title: String,
    @Json(name = "presenter_name") val presenterName: String? = null,
    val description: String? = null,
    val status: String? = null,
    @Json(name = "start_time") val startTime: Double? = null,
    @Json(name = "end_time") val endTime: Double? = null,
    @Json(name = "created_at") val createdAt: Double? = null,
    @Json(name = "slide_count") val slideCount: Int = 0,
)

@JsonClass(generateAdapter = true)
data class TalkListResponse(
    val talks: List<TalkResponse>,
    val total: Int = 0,
)

@JsonClass(generateAdapter = true)
data class UpdateTalkRequest(
    val title: String? = null,
    @Json(name = "presenter_name") val presenterName: String? = null,
    val description: String? = null,
)

@JsonClass(generateAdapter = true)
data class SessionListResponse(
    val sessions: List<SessionResponse>,
)
