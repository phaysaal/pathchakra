package com.seenslide.teacher.core.recording

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists stroke recordings to local filesystem.
 * Recordings are stored as JSON files, one per talk,
 * ready for upload when network is available.
 */
@Singleton
class StrokeRecordingStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val recordingsDir: File
        get() = File(context.filesDir, "stroke_recordings").also { it.mkdirs() }

    suspend fun save(talkId: String, recordingJson: JSONArray) {
        withContext(Dispatchers.IO) {
            val file = File(recordingsDir, "$talkId.json")
            file.writeText(recordingJson.toString())
        }
    }

    suspend fun load(talkId: String): JSONArray? {
        return withContext(Dispatchers.IO) {
            val file = File(recordingsDir, "$talkId.json")
            if (file.exists()) {
                JSONArray(file.readText())
            } else null
        }
    }

    suspend fun delete(talkId: String) {
        withContext(Dispatchers.IO) {
            File(recordingsDir, "$talkId.json").delete()
        }
    }

    suspend fun listPendingUploads(): List<String> {
        return withContext(Dispatchers.IO) {
            recordingsDir.listFiles()
                ?.filter { it.extension == "json" }
                ?.map { it.nameWithoutExtension }
                ?: emptyList()
        }
    }
}
