package com.seenslide.teacher.core.network.api

import com.seenslide.teacher.core.network.model.DeviceLoginRequest
import com.seenslide.teacher.core.network.model.DeviceLoginResponse
import com.seenslide.teacher.core.network.model.RegisterDeviceRequest
import com.seenslide.teacher.core.network.model.RegisterDeviceResponse
import com.seenslide.teacher.core.network.model.UpdatePhoneRequest
import com.seenslide.teacher.core.network.model.UpdatePhoneResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface TeacherAuthApi {

    @POST("api/auth/teacher/register-device")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): RegisterDeviceResponse

    @POST("api/auth/teacher/device-login")
    suspend fun deviceLogin(@Body request: DeviceLoginRequest): DeviceLoginResponse

    @POST("api/auth/teacher/update-phone")
    suspend fun updatePhone(@Body request: UpdatePhoneRequest): UpdatePhoneResponse
}
