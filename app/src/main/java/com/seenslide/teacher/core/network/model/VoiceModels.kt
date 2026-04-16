package com.seenslide.teacher.core.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StartRecordingResponse(
    @Json(name = "recording_id") val recordingId: String,
    @Json(name = "voice_session_id") val voiceSessionId: String? = null,
    val success: Boolean? = null,
    val message: String? = null,
)

@JsonClass(generateAdapter = true)
data class StopRecordingResponse(
    val success: Boolean,
    val message: String? = null,
)

@JsonClass(generateAdapter = true)
data class ChunkUploadResponse(
    val success: Boolean,
    @Json(name = "chunk_number") val chunkNumber: Int? = null,
    @Json(name = "chunk_size") val chunkSize: Int? = null,
    @Json(name = "total_size") val totalSize: Int? = null,
)
