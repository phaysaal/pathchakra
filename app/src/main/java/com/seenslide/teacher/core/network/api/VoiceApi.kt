package com.seenslide.teacher.core.network.api

import com.seenslide.teacher.core.network.model.ChunkUploadResponse
import com.seenslide.teacher.core.network.model.StartRecordingResponse
import com.seenslide.teacher.core.network.model.StopRecordingResponse
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface VoiceApi {

    @POST("api/voice/desktop/start/{sessionId}")
    suspend fun startRecording(
        @Path("sessionId") sessionId: String,
        @Query("audio_format") audioFormat: String = "ogg",
        @Query("talk_id") talkId: String? = null,
    ): StartRecordingResponse

    @Multipart
    @POST("api/voice/upload-chunk/{recordingId}")
    suspend fun uploadChunk(
        @Path("recordingId") recordingId: String,
        @Part file: MultipartBody.Part,
    ): ChunkUploadResponse

    @POST("api/voice/desktop/marker/{recordingId}")
    suspend fun addMarker(
        @Path("recordingId") recordingId: String,
        @Query("slide_number") slideNumber: Int,
        @Query("timestamp_seconds") timestampSeconds: Double,
    ): Map<String, Any>

    @Multipart
    @POST("api/voice/desktop/upload-final/{recordingId}")
    suspend fun uploadFinal(
        @Path("recordingId") recordingId: String,
        @Part file: MultipartBody.Part,
        @Query("duration_seconds") durationSeconds: Double,
    ): Map<String, Any>

    @POST("api/voice/desktop/stop/{recordingId}")
    suspend fun stopRecording(
        @Path("recordingId") recordingId: String,
        @Query("duration_seconds") durationSeconds: Double,
    ): StopRecordingResponse
}
