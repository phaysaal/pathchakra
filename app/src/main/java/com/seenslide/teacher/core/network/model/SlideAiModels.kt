package com.seenslide.teacher.core.network.model

import com.seenslide.teacher.core.slidedoc.SlideDoc
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GenerateDeckRequest(
    val topic: String,
    @Json(name = "num_slides") val numSlides: Int = 6,
    val language: String = "en",
    val provider: String = "auto",
)

@JsonClass(generateAdapter = true)
data class GenerateDeckResponse(
    val slides: List<SlideDoc>,
)

@JsonClass(generateAdapter = true)
data class ImproveSlideRequest(
    val doc: SlideDoc,
    val instruction: String,
    val language: String = "en",
    val provider: String = "auto",
)

@JsonClass(generateAdapter = true)
data class ImproveSlideResponse(
    val doc: SlideDoc,
)
