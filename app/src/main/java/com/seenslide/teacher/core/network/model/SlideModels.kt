package com.seenslide.teacher.core.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SlideUploadResponse(
    @Json(name = "slide_id") val slideId: String? = null,
    @Json(name = "slide_number") val slideNumber: Int? = null,
    @Json(name = "session_id") val sessionId: String? = null,
    @Json(name = "talk_id") val talkId: String? = null,
    val message: String? = null,
)

@JsonClass(generateAdapter = true)
data class SlideInfo(
    @Json(name = "slide_number") val slideNumber: Int,
    @Json(name = "slide_id") val slideId: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@JsonClass(generateAdapter = true)
data class SlideListResponse(
    val slides: List<SlideInfo>,
    @Json(name = "total_slides") val totalSlides: Int,
)

@JsonClass(generateAdapter = true)
data class SwapSlidesResponse(
    val success: Boolean = false,
    val message: String? = null,
)
