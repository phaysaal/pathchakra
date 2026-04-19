package com.seenslide.teacher.core.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RegisterDeviceRequest(
    @Json(name = "phone_number") val phoneNumber: String,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "pin_hash") val pinHash: String,
    @Json(name = "full_name") val fullName: String? = null,
)

@JsonClass(generateAdapter = true)
data class RegisterDeviceResponse(
    val success: Boolean,
    @Json(name = "session_token") val sessionToken: String,
    @Json(name = "user_id") val userId: String,
    @Json(name = "is_new_user") val isNewUser: Boolean,
    @Json(name = "full_name") val fullName: String? = null,
)

@JsonClass(generateAdapter = true)
data class DeviceLoginRequest(
    @Json(name = "phone_number") val phoneNumber: String,
    @Json(name = "device_id") val deviceId: String,
)

@JsonClass(generateAdapter = true)
data class DeviceLoginResponse(
    val success: Boolean,
    @Json(name = "session_token") val sessionToken: String,
    @Json(name = "user_id") val userId: String,
)

@JsonClass(generateAdapter = true)
data class UpdatePhoneRequest(
    @Json(name = "old_phone") val oldPhone: String,
    @Json(name = "new_phone") val newPhone: String,
    @Json(name = "device_id") val deviceId: String,
)

@JsonClass(generateAdapter = true)
data class UpdatePhoneResponse(
    val success: Boolean,
)
