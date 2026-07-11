package com.seenslide.teacher.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY createdAt DESC")
    suspend fun getAll(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE sessionId = :id")
    suspend fun getById(id: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sessions: List<SessionEntity>)

    @Query("DELETE FROM sessions WHERE sessionId = :id")
    suspend fun delete(id: String)

    @Query("UPDATE sessions SET presenterName = :name WHERE sessionId = :id")
    suspend fun rename(id: String, name: String)
}

@Dao
interface TalkDao {
    @Query("SELECT * FROM talks WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun observeBySession(sessionId: String): Flow<List<TalkEntity>>

    @Query("SELECT * FROM talks WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    suspend fun getBySession(sessionId: String): List<TalkEntity>

    @Query("SELECT * FROM talks WHERE talkId = :id")
    suspend fun getById(id: String): TalkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(talk: TalkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(talks: List<TalkEntity>)

    @Query("DELETE FROM talks WHERE talkId = :id")
    suspend fun delete(id: String)

    @Query("UPDATE talks SET title = :title WHERE talkId = :id")
    suspend fun rename(id: String, title: String)
}

@Dao
interface SlideDao {
    @Query("SELECT * FROM slides WHERE talkId = :talkId ORDER BY slideNumber ASC")
    fun observeByTalk(talkId: String): Flow<List<SlideEntity>>

    @Query("SELECT * FROM slides WHERE talkId = :talkId ORDER BY slideNumber ASC")
    suspend fun getByTalk(talkId: String): List<SlideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(slide: SlideEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(slides: List<SlideEntity>)

    @Query("DELETE FROM slides WHERE slideId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM slides WHERE talkId = :talkId")
    suspend fun deleteByTalk(talkId: String)

    @Transaction
    suspend fun replaceAllForTalk(talkId: String, slides: List<SlideEntity>) {
        deleteByTalk(talkId)
        upsertAll(slides)
    }
}

@Dao
interface StudentDao {
    @Query("SELECT * FROM students WHERE sessionId = :sessionId ORDER BY rollNumber ASC, name ASC")
    fun observeBySession(sessionId: String): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE sessionId = :sessionId ORDER BY rollNumber ASC, name ASC")
    suspend fun getBySession(sessionId: String): List<StudentEntity>

    @Query("SELECT * FROM students WHERE studentId = :id")
    suspend fun getById(id: String): StudentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(student: StudentEntity)

    @Query("UPDATE students SET name = :name, rollNumber = :rollNumber WHERE studentId = :id")
    suspend fun update(id: String, name: String, rollNumber: Int)

    @Query("DELETE FROM students WHERE studentId = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM students WHERE sessionId = :sessionId")
    suspend fun countBySession(sessionId: String): Int
}

@Dao
interface AttendanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(attendance: AttendanceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<AttendanceEntity>)

    @Query("SELECT * FROM attendance WHERE sessionId = :sessionId AND date = :date")
    fun observeBySessionAndDate(sessionId: String, date: String): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE sessionId = :sessionId AND date = :date")
    suspend fun getBySessionAndDate(sessionId: String, date: String): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE sessionId = :sessionId ORDER BY date DESC")
    suspend fun getBySession(sessionId: String): List<AttendanceEntity>

    @Query("SELECT DISTINCT date FROM attendance WHERE sessionId = :sessionId ORDER BY date DESC")
    suspend fun getDatesBySession(sessionId: String): List<String>

    @Query(
        """
        SELECT s.studentId, s.name, s.rollNumber,
               COUNT(CASE WHEN a.present = 1 THEN 1 END) AS presentCount,
               COUNT(a.attendanceId) AS totalCount
        FROM students s
        LEFT JOIN attendance a ON s.studentId = a.studentId AND a.sessionId = :sessionId
        WHERE s.sessionId = :sessionId
        GROUP BY s.studentId
        ORDER BY s.rollNumber ASC, s.name ASC
        """
    )
    suspend fun getAttendanceSummary(sessionId: String): List<AttendanceSummary>

    @Query("SELECT * FROM attendance WHERE studentId = :studentId ORDER BY date DESC")
    suspend fun getByStudent(studentId: String): List<AttendanceEntity>

    @Query("DELETE FROM attendance WHERE sessionId = :sessionId AND date = :date")
    suspend fun deleteBySessionAndDate(sessionId: String, date: String)
}

data class AttendanceSummary(
    val studentId: String,
    val name: String,
    val rollNumber: Int,
    val presentCount: Int,
    val totalCount: Int,
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE sessionId = :sessionId ORDER BY updatedAt DESC")
    fun observeBySession(sessionId: String): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Query("DELETE FROM notes WHERE noteId = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM notes WHERE noteId = :id")
    suspend fun getById(id: String): NoteEntity?

    @Query("SELECT COUNT(*) FROM notes WHERE sessionId = :sessionId")
    suspend fun countBySession(sessionId: String): Int
}

@Dao
interface PendingChangeDao {
    @Insert
    suspend fun insert(change: PendingChangeEntity)

    @Query("SELECT * FROM pending_changes ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingChangeEntity>

    @Query("SELECT COUNT(*) FROM pending_changes")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM pending_changes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE pending_changes SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetry(id: Long)

    @Query("DELETE FROM pending_changes WHERE retryCount >= :maxRetries")
    suspend fun pruneStale(maxRetries: Int = 10)

    @Query("DELETE FROM pending_changes")
    suspend fun deleteAll()
}
