package com.seenslide.teacher.core.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeviceBootstrapRequest(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "device_label") val deviceLabel: String? = null,
)

@JsonClass(generateAdapter = true)
data class IdentityUserPayload(
    @Json(name = "user_id") val userId: String,
    val email: String? = null,
    @Json(name = "phone_number") val phoneNumber: String? = null,
    @Json(name = "full_name") val fullName: String? = null,
    @Json(name = "is_anonymous") val isAnonymous: Boolean,
    @Json(name = "account_tier") val accountTier: String = "free",
)

@JsonClass(generateAdapter = true)
data class DeviceBootstrapResponse(
    @Json(name = "session_token") val sessionToken: String,
    val user: IdentityUserPayload,
)

@JsonClass(generateAdapter = true)
data class ClaimRequest(
    @Json(name = "device_id") val deviceId: String,
    val email: String? = null,
    @Json(name = "phone_number") val phoneNumber: String? = null,
    val secret: String,
)

@JsonClass(generateAdapter = true)
data class ClaimResponse(
    @Json(name = "session_token") val sessionToken: String,
    val user: IdentityUserPayload,
    val action: String,
)

@JsonClass(generateAdapter = true)
data class IdentityLoginRequest(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "device_label") val deviceLabel: String? = null,
    val email: String? = null,
    @Json(name = "phone_number") val phoneNumber: String? = null,
    val secret: String,
)

@JsonClass(generateAdapter = true)
data class IdentityLoginResponse(
    @Json(name = "session_token") val sessionToken: String,
    val user: IdentityUserPayload,
)

@JsonClass(generateAdapter = true)
data class UpdateIdentifiersRequest(
    @Json(name = "current_secret") val currentSecret: String? = null,
    @Json(name = "new_email") val newEmail: String? = null,
    @Json(name = "new_phone") val newPhone: String? = null,
    @Json(name = "new_secret") val newSecret: String? = null,
)

@JsonClass(generateAdapter = true)
data class RecoveryRequest(val email: String)

@JsonClass(generateAdapter = true)
data class RecoveryResponse(val sent: Boolean)
