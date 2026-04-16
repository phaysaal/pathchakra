package com.seenslide.teacher.core.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SlideUploadResponse(
    val success: Boolean,
    @Json(name = "slide_number") val slideNumber: Int? = null,
    @Json(name = "slide_id") val slideId: String? = null,
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
