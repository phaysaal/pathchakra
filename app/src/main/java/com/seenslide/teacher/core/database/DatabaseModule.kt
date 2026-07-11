package com.seenslide.teacher.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OStudiDatabase {
        return Room.databaseBuilder(
            context,
            OStudiDatabase::class.java,
            "seenslide.db",
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideSessionDao(db: OStudiDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideTalkDao(db: OStudiDatabase): TalkDao = db.talkDao()

    @Provides
    fun provideSlideDao(db: OStudiDatabase): SlideDao = db.slideDao()

    @Provides
    fun provideStudentDao(db: OStudiDatabase): StudentDao = db.studentDao()

    @Provides
    fun provideAttendanceDao(db: OStudiDatabase): AttendanceDao = db.attendanceDao()

    @Provides
    fun provideNoteDao(db: OStudiDatabase): NoteDao = db.noteDao()

    @Provides
    fun providePendingChangeDao(db: OStudiDatabase): PendingChangeDao = db.pendingChangeDao()
}
