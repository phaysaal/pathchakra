package com.seenslide.teacher.core.network.api

import com.seenslide.teacher.core.network.model.ClaimRequest
import com.seenslide.teacher.core.network.model.ClaimResponse
import com.seenslide.teacher.core.network.model.DeviceBootstrapRequest
import com.seenslide.teacher.core.network.model.DeviceBootstrapResponse
import com.seenslide.teacher.core.network.model.IdentityLoginRequest
import com.seenslide.teacher.core.network.model.IdentityLoginResponse
import com.seenslide.teacher.core.network.model.IdentityUserPayload
import com.seenslide.teacher.core.network.model.RecoveryRequest
import com.seenslide.teacher.core.network.model.RecoveryResponse
import com.seenslide.teacher.core.network.model.UpdateIdentifiersRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface IdentityApi {

    @POST("api/auth/device-bootstrap")
    suspend fun deviceBootstrap(@Body request: DeviceBootstrapRequest): DeviceBootstrapResponse

    @POST("api/auth/claim")
    suspend fun claim(@Body request: ClaimRequest): ClaimResponse

    @POST("api/auth/login")
    suspend fun login(@Body request: IdentityLoginRequest): IdentityLoginResponse

    @POST("api/auth/me/identifiers")
    suspend fun updateIdentifiers(@Body request: UpdateIdentifiersRequest): IdentityUserPayload

    @POST("api/auth/recover")
    suspend fun recover(@Body request: RecoveryRequest): RecoveryResponse

    @GET("api/auth/me/identity")
    suspend fun getMyIdentity(): IdentityUserPayload
}
