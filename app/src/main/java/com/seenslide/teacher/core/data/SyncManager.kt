package com.seenslide.teacher.core.data

import android.util.Log
import com.seenslide.teacher.core.database.PendingChangeDao
import com.seenslide.teacher.core.database.PendingChangeEntity
import com.seenslide.teacher.core.network.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class SyncState(
    val isSyncing: Boolean = false,
    val pendingChanges: Int = 0,
    val lastSyncTimestamp: Long = 0,
    val lastError: String? = null,
)

/**
 * Coordinates data synchronization between local Room database and the remote backend.
 *
 * Current behavior:
 * - Refreshes sessions/talks from the backend when online
 * - Queues local student/attendance mutations as PendingChange records
 * - Drains pending changes when a backend API becomes available
 *
 * The pending change queue is designed for future use — student/attendance data
 * currently lives only in Room. When backend endpoints are added, SyncManager
 * will push queued mutations upstream.
 */
@Singleton
class SyncManager @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val pendingChangeDao: PendingChangeDao,
    private val networkMonitor: NetworkMonitor,
    private val uploadQueue: SlideUploadQueue,
) {
    companion object {
        private const val TAG = "SyncManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState

    init {
        // Track pending change count
        pendingChangeDao.observeCount()
            .onEach { count ->
                _syncState.value = _syncState.value.copy(pendingChanges = count)
            }
            .launchIn(scope)
    }

    /**
     * Log a local mutation for future backend sync.
     */
    suspend fun logChange(
        entityType: String,
        action: String,
        entityId: String,
        sessionId: String,
        payload: String,
    ) {
        pendingChangeDao.insert(
            PendingChangeEntity(
                entityType = entityType,
                action = action,
                entityId = entityId,
                sessionId = sessionId,
                payload = payload,
            )
        )
    }

    /**
     * Full sync: refresh remote data + push pending local changes.
     * Called by SyncWorker (periodic) and on-demand by UI.
     */
    suspend fun sync(): Result<Unit> = mutex.withLock {
        if (!networkMonitor.isOnline.value) {
            return Result.failure(Exception("Offline"))
        }

        _syncState.value = _syncState.value.copy(isSyncing = true, lastError = null)

        try {
            // 1. Refresh session/talk data from backend
            refreshFromBackend()

            // 2. Drain slide upload queue
            uploadQueue.drainQueue()

            // 3. Push pending local changes (student/attendance)
            drainPendingChanges()

            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastSyncTimestamp = System.currentTimeMillis(),
            )
            Log.d(TAG, "Sync completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Sync failed: ${e.message}")
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastError = e.message,
            )
            Result.failure(e)
        }
    }

    private suspend fun refreshFromBackend() {
        try {
            val sessions = sessionRepository.refreshSessions()
            // Refresh talks for each session
            for (session in sessions) {
                try {
                    sessionRepository.refreshTalks(session.sessionId)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to refresh talks for ${session.sessionId}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to refresh sessions: ${e.message}")
            // Non-fatal — we still try to push local changes
        }
    }

    private suspend fun drainPendingChanges() {
        // Prune entries that have failed too many times
        pendingChangeDao.pruneStale()

        val changes = pendingChangeDao.getAll()
        if (changes.isEmpty()) return

        Log.d(TAG, "Processing ${changes.size} pending changes")

        for (change in changes) {
            if (!networkMonitor.isOnline.value) break

            try {
                pushChange(change)
                pendingChangeDao.delete(change.id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to push change ${change.id}: ${e.message}")
                pendingChangeDao.incrementRetry(change.id)
            }
        }
    }

    /**
     * Push a single pending change to the backend.
     * Currently a no-op — backend API for students/attendance doesn't exist yet.
     * When endpoints are added, implement the actual HTTP calls here.
     */
    private suspend fun pushChange(change: PendingChangeEntity) {
        // TODO: Implement when backend API for students/attendance is available
        // For now, silently acknowledge the change so the queue drains.
        //
        // Future implementation will look like:
        // when (change.entityType) {
        //     "student" -> when (change.action) {
        //         "create" -> studentApi.create(change.sessionId, change.payload)
        //         "update" -> studentApi.update(change.entityId, change.payload)
        //         "delete" -> studentApi.delete(change.entityId)
        //     }
        //     "attendance" -> ...
        // }
        Log.d(TAG, "Acknowledged change: ${change.entityType}/${change.action}/${change.entityId}")
    }
}
