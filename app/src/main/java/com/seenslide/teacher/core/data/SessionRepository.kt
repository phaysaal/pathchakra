package com.seenslide.teacher.core.data

import android.util.Log
import com.seenslide.teacher.core.database.SessionDao
import com.seenslide.teacher.core.database.SlideDao
import com.seenslide.teacher.core.database.TalkDao
import com.seenslide.teacher.core.database.toEntity
import com.seenslide.teacher.core.database.toResponse
import com.seenslide.teacher.core.database.toSlideInfo
import com.seenslide.teacher.core.network.api.SessionApi
import com.seenslide.teacher.core.network.model.CreateSessionRequest
import com.seenslide.teacher.core.network.model.CreateTalkRequest
import com.seenslide.teacher.core.network.model.RenameSessionRequest
import com.seenslide.teacher.core.network.model.SessionResponse
import com.seenslide.teacher.core.network.model.SlideInfo
import com.seenslide.teacher.core.network.model.TalkResponse
import com.seenslide.teacher.core.network.model.UpdateTalkRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for sessions, talks, and slide metadata.
 * Reads from Room first (instant), syncs from network in background.
 */
@Singleton
class SessionRepository @Inject constructor(
    private val sessionApi: SessionApi,
    private val sessionDao: SessionDao,
    private val talkDao: TalkDao,
    private val slideDao: SlideDao,
) {
    companion object {
        private const val TAG = "SessionRepo"
    }

    // --- Sessions ---

    /** Observe sessions from local cache. */
    fun observeSessions(): Flow<List<SessionResponse>> {
        return sessionDao.observeAll().map { entities ->
            entities.map { it.toResponse() }
        }
    }

    /** Get cached sessions, then refresh from network. */
    suspend fun getSessions(): List<SessionResponse> {
        return sessionDao.getAll().map { it.toResponse() }
    }

    /** Fetch sessions from network and update local cache. */
    suspend fun refreshSessions(): List<SessionResponse> {
        val response = sessionApi.listSessions()
        sessionDao.upsertAll(response.sessions.map { it.toEntity() })
        return response.sessions
    }

    suspend fun createSession(request: CreateSessionRequest): SessionResponse {
        val response = sessionApi.createSession(request)
        sessionDao.upsert(response.toEntity())
        return response
    }

    suspend fun renameSession(sessionId: String, name: String) {
        sessionApi.renameSession(sessionId, RenameSessionRequest(name))
        sessionDao.rename(sessionId, name)
    }

    suspend fun deleteSession(sessionId: String) {
        sessionApi.deleteSession(sessionId)
        sessionDao.delete(sessionId)
    }

    // --- Talks ---

    /** Observe talks for a session from local cache. */
    fun observeTalks(sessionId: String): Flow<List<TalkResponse>> {
        return talkDao.observeBySession(sessionId).map { entities ->
            entities.map { it.toResponse() }
        }
    }

    suspend fun getTalks(sessionId: String): List<TalkResponse> {
        return talkDao.getBySession(sessionId).map { it.toResponse() }
    }

    suspend fun refreshTalks(sessionId: String): List<TalkResponse> {
        val response = sessionApi.listTalks(sessionId)
        talkDao.upsertAll(response.talks.map { it.toEntity() })
        return response.talks
    }

    suspend fun getTalk(talkId: String): TalkResponse? {
        return talkDao.getById(talkId)?.toResponse()
    }

    suspend fun createTalk(sessionId: String, request: CreateTalkRequest): TalkResponse {
        val response = sessionApi.createTalk(sessionId, request)
        talkDao.upsert(response.toEntity())
        return response
    }

    suspend fun updateTalk(talkId: String, request: UpdateTalkRequest): TalkResponse {
        val response = sessionApi.updateTalk(talkId, request)
        talkDao.upsert(response.toEntity())
        return response
    }

    suspend fun deleteTalk(talkId: String) {
        sessionApi.deleteTalk(talkId)
        talkDao.delete(talkId)
    }

    // --- Slides (metadata) ---

    fun observeSlides(talkId: String): Flow<List<SlideInfo>> {
        return slideDao.observeByTalk(talkId).map { entities ->
            entities.map { it.toSlideInfo() }
        }
    }

    suspend fun getSlides(talkId: String): List<SlideInfo> {
        return slideDao.getByTalk(talkId).map { it.toSlideInfo() }
    }

    /** Update local slide cache from a freshly fetched list. */
    suspend fun cacheSlides(talkId: String, slides: List<SlideInfo>) {
        slideDao.replaceAllForTalk(
            talkId,
            slides.map { it.toEntity(talkId) },
        )
    }

    suspend fun deleteSlide(sessionId: String, slideId: String) {
        sessionApi.deleteSlide(sessionId, slideId)
        slideDao.delete(slideId)
    }
}
