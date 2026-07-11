package com.seenslide.teacher.core.media

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-slide narration audio, stored locally until published.
 *
 * Layout: filesDir/narrations/{talkId}/slide_{n}.aac  (ADTS AAC)
 *         filesDir/narrations/{talkId}/meta.json      ({"12": 34.5, ...})
 *
 * meta.json maps slide_number → duration seconds; durations come from the
 * PCM byte count at record time (exact), not from parsing the AAC back.
 * Re-recording a slide overwrites both. Publish concatenates the files as
 * ordered chunks on the server, so nothing here needs re-encoding.
 */
@Singleton
class NarrationStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "NarrationStore"
    }

    private fun dir(talkId: String): File =
        File(File(context.filesDir, "narrations"), talkId)

    private fun metaFile(talkId: String): File = File(dir(talkId), "meta.json")

    fun fileFor(talkId: String, slideNumber: Int): File =
        File(dir(talkId), "slide_$slideNumber.aac")

    /** slide_number → duration seconds for every narrated slide. */
    fun durations(talkId: String): Map<Int, Double> {
        val f = metaFile(talkId)
        if (!f.exists()) return emptyMap()
        return try {
            val obj = JSONObject(f.readText())
            buildMap {
                for (key in obj.keys()) {
                    val n = key.toIntOrNull() ?: continue
                    // Only report slides whose audio file actually exists
                    if (fileFor(talkId, n).exists()) put(n, obj.getDouble(key))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "meta.json unreadable: ${e.message}")
            emptyMap()
        }
    }

    fun save(talkId: String, slideNumber: Int, aac: ByteArray, durationSec: Double) {
        val d = dir(talkId)
        d.mkdirs()
        fileFor(talkId, slideNumber).writeBytes(aac)
        val meta = JSONObject(
            metaFile(talkId).takeIf { it.exists() }?.readText() ?: "{}"
        )
        meta.put(slideNumber.toString(), durationSec)
        metaFile(talkId).writeText(meta.toString())
    }

    // --- Ink (stroke timeline recorded while narrating) ---

    private fun strokesFileFor(talkId: String, slideNumber: Int): File =
        File(dir(talkId), "slide_$slideNumber.strokes.json")

    /** JSON array of wire-format strokes, timestamps in audio-relative ms. */
    fun saveStrokes(talkId: String, slideNumber: Int, strokesJson: String) {
        dir(talkId).mkdirs()
        strokesFileFor(talkId, slideNumber).writeText(strokesJson)
    }

    fun loadStrokes(talkId: String, slideNumber: Int): String? =
        strokesFileFor(talkId, slideNumber).takeIf { it.exists() }?.readText()

    fun deleteStrokes(talkId: String, slideNumber: Int) {
        strokesFileFor(talkId, slideNumber).delete()
    }

    fun delete(talkId: String, slideNumber: Int) {
        fileFor(talkId, slideNumber).delete()
        deleteStrokes(talkId, slideNumber)
        val f = metaFile(talkId)
        if (f.exists()) {
            try {
                val meta = JSONObject(f.readText())
                meta.remove(slideNumber.toString())
                f.writeText(meta.toString())
            } catch (_: Exception) {}
        }
    }

    fun deleteAll(talkId: String) {
        dir(talkId).deleteRecursively()
    }
}
