package com.seenslide.teacher.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val sessionId: String,
    val presenterName: String? = null,
    val status: String? = null,
    val totalSlides: Int = 0,
    val viewerCount: Int = 0,
    val createdAt: Double? = null,
    val activeTalkId: String? = null,
    val lastSyncedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "talks",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class TalkEntity(
    @PrimaryKey val talkId: String,
    val sessionId: String,
    val title: String,
    val presenterName: String? = null,
    val description: String? = null,
    val status: String? = null,
    val startTime: Double? = null,
    val endTime: Double? = null,
    val createdAt: Double? = null,
    val slideCount: Int = 0,
    val lastSyncedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "slides",
    foreignKeys = [
        ForeignKey(
            entity = TalkEntity::class,
            parentColumns = ["talkId"],
            childColumns = ["talkId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("talkId")],
)
data class SlideEntity(
    @PrimaryKey val slideId: String,
    val talkId: String,
    val slideNumber: Int,
    val width: Int? = null,
    val height: Int? = null,
    val lastSyncedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "students",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class StudentEntity(
    @PrimaryKey val studentId: String,
    val sessionId: String,
    val name: String,
    val rollNumber: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "attendance",
    foreignKeys = [
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["studentId"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("studentId"),
        Index("sessionId", "date"),
    ],
)
data class AttendanceEntity(
    @PrimaryKey val attendanceId: String,
    val studentId: String,
    val sessionId: String,
    @ColumnInfo(defaultValue = "") val talkId: String = "",
    val date: String, // yyyy-MM-dd
    val present: Boolean = false,
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class NoteEntity(
    @PrimaryKey val noteId: String,
    val sessionId: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Tracks local mutations that need to be synced to the backend.
 * When a backend API for students/attendance exists, the SyncManager
 * will drain this table by pushing changes upstream.
 */
@Entity(tableName = "pending_changes")
data class PendingChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String, // "student" or "attendance"
    val action: String, // "create", "update", "delete"
    val entityId: String, // studentId or attendanceId
    val sessionId: String,
    val payload: String, // JSON-serialized entity data
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
)
