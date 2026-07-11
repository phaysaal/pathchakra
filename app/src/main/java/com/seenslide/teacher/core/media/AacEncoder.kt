package com.seenslide.teacher.core.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encodes raw PCM (16-bit LE mono) to ADTS AAC-LC with MediaCodec.
 *
 * ADTS because every frame is self-describing: each encoded chunk is an
 * independently decodable .aac stream, which is exactly what the chunked
 * voice upload needs — the server merges chunks by decoding each with
 * ffmpeg, and browser live listeners feed whole chunks to decodeAudioData.
 * At 32 kbps mono this is ~25x smaller than the raw PCM we used to
 * upload — the difference between unusable and fine on rural 2G/3G.
 *
 * A fresh codec per call keeps chunk boundaries clean (no cross-chunk
 * encoder state) at the cost of ~10 ms setup per chunk, which is nothing
 * against a 1-3 s cadence.
 */
@Singleton
class AacEncoder @Inject constructor() {

    companion object {
        private const val TAG = "AacEncoder"
        private const val BITRATE = 32_000

        // ADTS sampling_frequency_index table (subset we might meet)
        private val FREQ_INDEX = mapOf(
            96000 to 0, 88200 to 1, 64000 to 2, 48000 to 3, 44100 to 4,
            32000 to 5, 24000 to 6, 22050 to 7, 16000 to 8, 12000 to 9,
            11025 to 10, 8000 to 11,
        )
    }

    /**
     * @param pcm 16-bit little-endian PCM
     * @return ADTS AAC bytes, or null if encoding failed (caller may fall
     *         back to uploading the raw PCM)
     */
    fun encode(pcm: ByteArray, sampleRate: Int = 44100, channels: Int = 1): ByteArray? {
        if (pcm.isEmpty()) return ByteArray(0)
        val freqIndex = FREQ_INDEX[sampleRate] ?: return null

        var codec: MediaCodec? = null
        return try {
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            }
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).also {
                it.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                it.start()
            }

            val out = ByteArrayOutputStream(pcm.size / 20)
            val info = MediaCodec.BufferInfo()
            val bytesPerSec = 2L * channels * sampleRate
            var inOffset = 0
            var inputDone = false

            while (true) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buf = codec.getInputBuffer(inIndex)!!
                        buf.clear()
                        val len = minOf(buf.capacity(), pcm.size - inOffset)
                        if (len <= 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            buf.put(pcm, inOffset, len)
                            val ptsUs = inOffset * 1_000_000L / bytesPerSec
                            codec.queueInputBuffer(inIndex, 0, len, ptsUs, 0)
                            inOffset += len
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                if (outIndex >= 0) {
                    val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    if (info.size > 0 && !isConfig) {
                        val frame = ByteArray(info.size)
                        codec.getOutputBuffer(outIndex)!!.apply {
                            position(info.offset)
                            get(frame)
                        }
                        out.write(adtsHeader(frame.size + 7, freqIndex, channels))
                        out.write(frame)
                    }
                    val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    codec.releaseOutputBuffer(outIndex, false)
                    if (eos) break
                }
            }
            out.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "AAC encode failed: ${e.message}")
            null
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {}
        }
    }

    /** Standard 7-byte ADTS header, AAC-LC, no CRC. */
    private fun adtsHeader(frameLength: Int, freqIndex: Int, channels: Int): ByteArray {
        val profile = 2 // AAC-LC
        return byteArrayOf(
            0xFF.toByte(),
            0xF9.toByte(),
            (((profile - 1) shl 6) or (freqIndex shl 2) or (channels shr 2)).toByte(),
            (((channels and 3) shl 6) or (frameLength shr 11)).toByte(),
            ((frameLength and 0x7FF) shr 3).toByte(),
            (((frameLength and 7) shl 5) or 0x1F).toByte(),
            0xFC.toByte(),
        )
    }
}
