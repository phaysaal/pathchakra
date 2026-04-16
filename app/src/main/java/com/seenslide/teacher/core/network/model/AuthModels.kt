package com.seenslide.teacher.core.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MagicLinkRequest(
    val email: String,
    @Json(name = "redirect_url") val redirectUrl: String? = "seenslide://auth/callback",
)

@JsonClass(generateAdapter = true)
data class MagicLinkResponse(
    val success: Boolean,
    val message: String? = null,
)

@JsonClass(generateAdapter = true)
data class VerifyTokenRequest(
    val token: String,
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    @Json(name = "session_token") val sessionToken: String,
    @Json(name = "user_id") val userId: String,
    val email: String,
    @Json(name = "full_name") val fullName: String? = null,
)
