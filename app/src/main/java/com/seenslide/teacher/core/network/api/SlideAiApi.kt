package com.seenslide.teacher.core.network.api

import com.seenslide.teacher.core.network.model.GenerateDeckRequest
import com.seenslide.teacher.core.network.model.GenerateDeckResponse
import com.seenslide.teacher.core.network.model.ImproveSlideRequest
import com.seenslide.teacher.core.network.model.ImproveSlideResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface SlideAiApi {

    @POST("api/cloud/ai/generate-deck")
    suspend fun generateDeck(@Body body: GenerateDeckRequest): GenerateDeckResponse

    @POST("api/cloud/ai/improve-slide")
    suspend fun improveSlide(@Body body: ImproveSlideRequest): ImproveSlideResponse
}
