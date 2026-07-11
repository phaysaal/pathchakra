package com.seenslide.teacher.feature.narration

import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenslide.teacher.BuildConfig
import com.seenslide.teacher.core.data.SlideRepository
import com.seenslide.teacher.core.media.AacEncoder
import com.seenslide.teacher.core.media.AudioChunkRecorder
import com.seenslide.teacher.core.media.NarrationStore
import com.seenslide.teacher.core.network.api.VoiceApi
import com.seenslide.teacher.core.network.model.MarkerItem
import com.seenslide.teacher.core.network.model.SlideInfo
import com.seenslide.teacher.core.network.model.SyncMarkersRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import javax.inject.Inject

sealed class PublishState {
    data object Idle : PublishState()
    data class InProgress(val done: Int, val total: Int) : PublishState()
    data object Success : PublishState()
    data class Failed(val message: String) : PublishState()
}

data class NarrationUiState(
    val slides: List<SlideInfo> = emptyList(),
    val currentIndex: Int = 0,
    val isRecording: Boolean = false,
    val recordElapsedSec: Double = 0.0,
    val isPlaying: Boolean = false,
    /** slide_number → narration duration seconds */
    val narrated: Map<Int, Double> = emptyMap(),
    val publishState: PublishState = PublishState.Idle,
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    val currentSlide: SlideInfo? get() = slides.getOrNull(currentIndex)
    val narratedCount: Int get() = slides.count { narrated.containsKey(it.slideNumber) }
    val totalNarratedSeconds: Double
        get() = slides.sumOf { narrated[it.slideNumber] ?: 0.0 }
}

/**
 * Mode 2 — record a lecture slide-by-slide, offline, at your own pace.
 *
 * Each slide's narration is an independent local AAC file; re-record to
 * replace. Publish stitches them WITHOUT re-encoding: each file uploads
 * as an ordered chunk, markers land at the cumulative start of each
 * narrated slide, and the server merge produces one seekable recording —
 * indistinguishable to viewers from a live-recorded talk.
 */
@HiltViewModel
class NarrationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val slideRepository: SlideRepository,
    private val recorder: AudioChunkRecorder,
    private val aacEncoder: AacEncoder,
    private val narrationStore: NarrationStore,
    private val voiceApi: VoiceApi,
) : ViewModel() {

    companion object {
        private const val TAG = "Narration"
    }

    val sessionId: String = savedStateHandle["sessionId"] ?: ""
    val talkId: String = savedStateHandle["talkId"] ?: ""

    private val _uiState = MutableStateFlow(NarrationUiState())
    val uiState = _uiState.asStateFlow()

    private var tickerJob: Job? = null
    private var player: MediaPlayer? = null

    init {
        viewModelScope.launch {
            try {
                val slides = slideRepository.getSlides(talkId).sortedBy { it.slideNumber }
                _uiState.value = _uiState.value.copy(
                    slides = slides,
                    narrated = narrationStore.durations(talkId),
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load slides: ${e.message}",
                )
            }
        }
    }

    fun slideImageUrl(slideNumber: Int): String {
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        return "$base/api/cloud/talk/$talkId/slides/$slideNumber"
    }

    fun selectSlide(index: Int) {
        if (_uiState.value.isRecording) return // finish/stop first
        stopPlayback()
        if (index in _uiState.value.slides.indices) {
            _uiState.value = _uiState.value.copy(currentIndex = index)
        }
    }

    fun nextSlide() = selectSlide(_uiState.value.currentIndex + 1)
    fun previousSlide() = selectSlide(_uiState.value.currentIndex - 1)

    // --- Recording ---

    fun startRecording() {
        if (_uiState.value.isRecording) return
        stopPlayback()
        try {
            if (!recorder.start()) {
                _uiState.value = _uiState.value.copy(error = "Could not open microphone")
                return
            }
        } catch (e: SecurityException) {
            _uiState.value = _uiState.value.copy(error = "Microphone permission missing")
            return
        }
        _uiState.value = _uiState.value.copy(isRecording = true, recordElapsedSec = 0.0)
        tickerJob = viewModelScope.launch {
            while (isActive && recorder.isRecording) {
                _uiState.value = _uiState.value.copy(recordElapsedSec = recorder.elapsedSeconds)
                delay(250)
            }
        }
    }

    fun stopRecording() {
        if (!_uiState.value.isRecording) return
        tickerJob?.cancel()
        tickerJob = null
        val slide = _uiState.value.currentSlide
        val pcm = recorder.stop()
        val duration = recorder.elapsedSeconds
        _uiState.value = _uiState.value.copy(isRecording = false)

        if (slide == null || pcm == null || pcm.isEmpty() || duration < 0.3) {
            _uiState.value = _uiState.value.copy(error = "Recording too short")
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            val aac = aacEncoder.encode(pcm, recorder.sampleRate, 1)
            if (aac == null || aac.isEmpty()) {
                _uiState.value = _uiState.value.copy(error = "Audio encoding failed")
                return@launch
            }
            narrationStore.save(talkId, slide.slideNumber, aac, duration)
            _uiState.value = _uiState.value.copy(
                narrated = narrationStore.durations(talkId),
            )
            Log.d(TAG, "Narration saved: slide ${slide.slideNumber}, ${duration}s, ${aac.size} bytes")
        }
    }

    fun deleteNarration() {
        val slide = _uiState.value.currentSlide ?: return
        stopPlayback()
        narrationStore.delete(talkId, slide.slideNumber)
        _uiState.value = _uiState.value.copy(narrated = narrationStore.durations(talkId))
    }

    // --- Local playback (review before publishing) ---

    fun playNarration() {
        val slide = _uiState.value.currentSlide ?: return
        val file = narrationStore.fileFor(talkId, slide.slideNumber)
        if (!file.exists()) return
        stopPlayback()
        try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    _uiState.value = _uiState.value.copy(isPlaying = false)
                }
                prepare()
                start()
            }
            _uiState.value = _uiState.value.copy(isPlaying = true)
        } catch (e: Exception) {
            Log.w(TAG, "Playback failed: ${e.message}")
            _uiState.value = _uiState.value.copy(error = "Playback failed", isPlaying = false)
        }
    }

    fun stopPlayback() {
        player?.let {
            try { it.stop(); it.release() } catch (_: Exception) {}
        }
        player = null
        if (_uiState.value.isPlaying) {
            _uiState.value = _uiState.value.copy(isPlaying = false)
        }
    }

    // --- Publish ---

    /**
     * Upload all narrated slides (in slide order) as one recording:
     * chunk i = slide i's AAC file, marker at each slide's cumulative
     * start, bulk marker sync, then finalize. Slides without narration
     * are skipped — they simply don't appear on the playback timeline.
     */
    fun publish() {
        val state = _uiState.value
        if (state.isRecording || state.publishState is PublishState.InProgress) return
        val narratedSlides = state.slides.filter { state.narrated.containsKey(it.slideNumber) }
        if (narratedSlides.isEmpty()) {
            _uiState.value = state.copy(error = "Record narration for at least one slide first")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                publishState = PublishState.InProgress(0, narratedSlides.size)
            )
            try {
                val start = voiceApi.startRecording(
                    sessionId = sessionId,
                    audioFormat = "aac",
                    talkId = talkId,
                    sampleRate = recorder.sampleRate,
                    channels = 1,
                )
                val rid = start.recordingId

                val markers = mutableListOf<MarkerItem>()
                var cumulative = 0.0
                narratedSlides.forEachIndexed { i, slide ->
                    val file = narrationStore.fileFor(talkId, slide.slideNumber)
                    val part = MultipartBody.Part.createFormData(
                        "file",
                        "chunk_%04d.aac".format(i + 1),
                        file.asRequestBody("audio/aac".toMediaType()),
                    )
                    voiceApi.uploadChunk(rid, part, chunkIndex = i + 1)
                    markers.add(MarkerItem(slide.slideNumber, cumulative))
                    cumulative += _uiState.value.narrated[slide.slideNumber] ?: 0.0
                    _uiState.value = _uiState.value.copy(
                        publishState = PublishState.InProgress(i + 1, narratedSlides.size)
                    )
                }

                voiceApi.syncMarkers(rid, SyncMarkersRequest(markers))
                voiceApi.stopRecording(rid, cumulative)

                _uiState.value = _uiState.value.copy(publishState = PublishState.Success)
                Log.d(TAG, "Published: ${narratedSlides.size} slides, ${cumulative}s, recording=$rid")
            } catch (e: Exception) {
                Log.e(TAG, "Publish failed", e)
                _uiState.value = _uiState.value.copy(
                    publishState = PublishState.Failed(e.message ?: "Upload failed")
                )
            }
        }
    }

    fun dismissPublishResult() {
        _uiState.value = _uiState.value.copy(publishState = PublishState.Idle)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        stopPlayback()
        if (recorder.isRecording) recorder.stop()
        super.onCleared()
    }
}
