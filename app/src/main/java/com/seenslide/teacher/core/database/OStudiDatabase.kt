package com.seenslide.teacher.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SessionEntity::class,
        TalkEntity::class,
        SlideEntity::class,
        StudentEntity::class,
        AttendanceEntity::class,
        NoteEntity::class,
        PendingChangeEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class OStudiDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun talkDao(): TalkDao
    abstract fun slideDao(): SlideDao
    abstract fun studentDao(): StudentDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun noteDao(): NoteDao
    abstract fun pendingChangeDao(): PendingChangeDao
}
