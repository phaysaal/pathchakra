package com.seenslide.teacher.core.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.seenslide.teacher.core.database.AttendanceDao
import com.seenslide.teacher.core.database.SessionDao
import com.seenslide.teacher.core.database.StudentDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val sessionDao: SessionDao,
    private val studentDao: StudentDao,
    private val attendanceDao: AttendanceDao,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "AutoBackupWorker"
        private const val WORK_NAME = "pathchakra_auto_backup"
        private const val BACKUP_DIR = "auto_backups"
        private const val MAX_BACKUPS = 3

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
                24, TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
            Log.d(TAG, "Auto-backup scheduled (daily, when battery not low)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Auto-backup cancelled")
        }

        fun getBackupDir(context: Context): File {
            return File(context.filesDir, BACKUP_DIR).also { it.mkdirs() }
        }

        fun getLatestBackupTime(context: Context): Long? {
            val dir = getBackupDir(context)
            return dir.listFiles()
                ?.filter { it.name.endsWith(".json") }
                ?.maxByOrNull { it.lastModified() }
                ?.lastModified()
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Auto-backup running")
        return try {
            val sessions = sessionDao.getAll()
            val root = JSONObject()

            val studentsArray = JSONArray()
            val attendanceArray = JSONArray()

            for (session in sessions) {
                val students = studentDao.getBySession(session.sessionId)
                for (s in students) {
                    studentsArray.put(JSONObject().apply {
                        put("studentId", s.studentId)
                        put("sessionId", s.sessionId)
                        put("name", s.name)
                        put("rollNumber", s.rollNumber)
                    })
                }
                val records = attendanceDao.getBySession(session.sessionId)
                for (a in records) {
                    attendanceArray.put(JSONObject().apply {
                        put("attendanceId", a.attendanceId)
                        put("studentId", a.studentId)
                        put("sessionId", a.sessionId)
                        put("talkId", a.talkId)
                        put("date", a.date)
                        put("present", a.present)
                    })
                }
            }

            root.put("students", studentsArray)
            root.put("attendance", attendanceArray)
            root.put("exportedAt", System.currentTimeMillis())

            val dir = getBackupDir(context)
            val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
            val file = File(dir, "backup_$dateStr.json")
            file.writeText(root.toString(2))

            // Keep only the last N backups
            dir.listFiles()
                ?.filter { it.name.endsWith(".json") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_BACKUPS)
                ?.forEach { it.delete() }

            Log.d(TAG, "Auto-backup completed: ${file.name} (${studentsArray.length()} students, ${attendanceArray.length()} records)")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Auto-backup failed: ${e.message}", e)
            Result.retry()
        }
    }
}
