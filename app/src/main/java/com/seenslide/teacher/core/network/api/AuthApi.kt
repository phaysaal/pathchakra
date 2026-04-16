package com.seenslide.teacher.core.network.api

import com.seenslide.teacher.core.network.model.AuthResponse
import com.seenslide.teacher.core.network.model.MagicLinkRequest
import com.seenslide.teacher.core.network.model.MagicLinkResponse
import com.seenslide.teacher.core.network.model.VerifyTokenRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("api/auth/request-magic-link")
    suspend fun requestMagicLink(@Body request: MagicLinkRequest): MagicLinkResponse

    @POST("api/auth/verify-token")
    suspend fun verifyToken(@Body request: VerifyTokenRequest): AuthResponse
}
