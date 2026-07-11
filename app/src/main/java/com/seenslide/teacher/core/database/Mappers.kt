package com.seenslide.teacher.core.database

import com.seenslide.teacher.core.network.model.SessionResponse
import com.seenslide.teacher.core.network.model.SlideInfo
import com.seenslide.teacher.core.network.model.TalkResponse

// --- Network → Room ---

fun SessionResponse.toEntity() = SessionEntity(
    sessionId = sessionId,
    presenterName = presenterName,
    status = status,
    totalSlides = totalSlides,
    viewerCount = viewerCount,
    createdAt = createdAt,
    activeTalkId = activeTalkId,
)

fun TalkResponse.toEntity() = TalkEntity(
    talkId = talkId,
    sessionId = sessionId,
    title = title,
    presenterName = presenterName,
    description = description,
    status = status,
    startTime = startTime,
    endTime = endTime,
    createdAt = createdAt,
    slideCount = slideCount,
)

fun SlideInfo.toEntity(talkId: String) = SlideEntity(
    slideId = slideId ?: "slide_${talkId}_$slideNumber",
    talkId = talkId,
    slideNumber = slideNumber,
    width = width,
    height = height,
)

// --- Room → Network model (for UI compatibility) ---

fun SessionEntity.toResponse() = SessionResponse(
    sessionId = sessionId,
    presenterName = presenterName,
    status = status,
    totalSlides = totalSlides,
    viewerCount = viewerCount,
    createdAt = createdAt,
    activeTalkId = activeTalkId,
)

fun TalkEntity.toResponse() = TalkResponse(
    talkId = talkId,
    sessionId = sessionId,
    title = title,
    presenterName = presenterName,
    description = description,
    status = status,
    startTime = startTime,
    endTime = endTime,
    createdAt = createdAt,
    slideCount = slideCount,
)

fun SlideEntity.toSlideInfo() = SlideInfo(
    slideNumber = slideNumber,
    slideId = slideId,
    width = width,
    height = height,
)
