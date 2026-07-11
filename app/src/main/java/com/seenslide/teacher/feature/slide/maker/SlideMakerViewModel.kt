package com.seenslide.teacher.feature.slide.maker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenslide.teacher.core.data.SlideQueuedLocallyException
import com.seenslide.teacher.core.data.SlideRepository
import com.seenslide.teacher.core.network.api.SlideAiApi
import com.seenslide.teacher.core.network.model.GenerateDeckRequest
import com.seenslide.teacher.core.network.model.ImproveSlideRequest
import com.seenslide.teacher.core.slidedoc.ShapeKind
import com.seenslide.teacher.core.slidedoc.SlideBackground
import com.seenslide.teacher.core.slidedoc.SlideDoc
import com.seenslide.teacher.core.slidedoc.SlideElement
import com.seenslide.teacher.core.slidedoc.SlideElementType
import com.seenslide.teacher.core.ui.ErrorClassifier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject

data class SlideMakerUiState(
    val doc: SlideDoc = SlideDoc(),
    val selectedId: String? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val savedLocally: Boolean = false,
    val error: String? = null,
    val editingText: SlideElement? = null,
    val isAiBusy: Boolean = false,
    val aiStatus: String? = null,
)

@HiltViewModel
class SlideMakerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val slideRepository: SlideRepository,
    private val slideAiApi: SlideAiApi,
    private val errorClassifier: ErrorClassifier,
) : ViewModel() {

    val sessionId: String = savedStateHandle["sessionId"] ?: ""
    val talkId: String = savedStateHandle["talkId"] ?: ""
    private val replaceSlideArg: Int = savedStateHandle["replaceSlide"] ?: -1
    private val replaceSlide: Int? = replaceSlideArg.takeIf { it > 0 }

    private val _uiState = MutableStateFlow(SlideMakerUiState())
    val uiState: StateFlow<SlideMakerUiState> = _uiState

    /**
     * Local image cache for in-progress slides. Keys are "local:<uuid>" strings
     * referenced from [SlideElement.src]. Bitmaps live in memory until save;
     * the rasterizer reads them by id to produce the final image.
     */
    private val localImages = mutableMapOf<String, Bitmap>()

    fun resolveImage(src: String): Bitmap? = localImages[src]

    fun setBackgroundColor(hex: String) {
        mutateDoc { it.copy(background = SlideBackground(type = "color", color = hex)) }
    }

    /**
     * Replace the current doc with a fresh copy of [template]. Only valid when
     * the slide is empty (no elements and default background); the UI hides
     * the template picker once the user starts editing so we don't clobber
     * in-progress work.
     */
    fun applyTemplate(template: SlideTemplate) {
        _uiState.value = _uiState.value.copy(
            doc = template.build(),
            selectedId = null,
            editingText = null,
        )
    }

    fun addTextBox() {
        val newEl = SlideElement(
            type = SlideElementType.TEXT,
            x = 0.1f, y = 0.4f, w = 0.8f, h = 0.15f,
            content = "Tap to edit",
            fontSize = 0.08f,
            color = "#000000",
            align = "center",
            z = nextZ(),
        )
        mutateDoc { it.copy(elements = it.elements + newEl) }
        _uiState.value = _uiState.value.copy(selectedId = newEl.id, editingText = newEl)
    }

    fun addImageFromUri(uri: Uri) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } ?: run {
                _uiState.value = _uiState.value.copy(error = "Could not load image")
                return@launch
            }
            val id = "local:${UUID.randomUUID()}"
            localImages[id] = bmp

            val aspect = bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f)
            val slideAspect = _uiState.value.doc.aspect
            val w = 0.6f
            val h = (w * slideAspect) / aspect
            val newEl = SlideElement(
                type = SlideElementType.IMAGE,
                x = (1f - w) / 2f, y = (1f - h) / 2f,
                w = w, h = h.coerceIn(0.1f, 0.9f),
                src = id, fit = "contain",
                z = nextZ(),
            )
            mutateDoc { it.copy(elements = it.elements + newEl) }
            _uiState.value = _uiState.value.copy(selectedId = newEl.id)
        }
    }

    fun addShape(kind: String) {
        val newEl = SlideElement(
            type = SlideElementType.SHAPE,
            x = 0.2f, y = 0.3f, w = 0.6f, h = 0.3f,
            kind = kind,
            fill = if (kind == ShapeKind.LINE || kind == ShapeKind.ARROW) null else "#FF9800",
            stroke = "#000000",
            strokeWidth = 0.004f,
            z = nextZ(),
        )
        mutateDoc { it.copy(elements = it.elements + newEl) }
        _uiState.value = _uiState.value.copy(selectedId = newEl.id)
    }

    fun selectElement(id: String?) {
        _uiState.value = _uiState.value.copy(selectedId = id)
    }

    /**
     * Translate the element by a delta in normalized coordinates (fraction of
     * slide width/height). Clamps to the slide edges.
     */
    fun moveElement(id: String, dxN: Float, dyN: Float) {
        mutateDoc { doc ->
            doc.copy(elements = doc.elements.map { el ->
                if (el.id != id) el
                else el.copy(
                    x = (el.x + dxN).coerceIn(0f, (1f - el.w).coerceAtLeast(0f)),
                    y = (el.y + dyN).coerceIn(0f, (1f - el.h).coerceAtLeast(0f)),
                )
            })
        }
    }

    /**
     * Resize by extending the bottom-right corner. Minimum size is 5% of the
     * slide in each dimension; maximum stays within the slide bounds.
     */
    fun resizeElement(id: String, dwN: Float, dhN: Float) {
        mutateDoc { doc ->
            doc.copy(elements = doc.elements.map { el ->
                if (el.id != id) el
                else el.copy(
                    w = (el.w + dwN).coerceIn(0.05f, (1f - el.x).coerceAtLeast(0.05f)),
                    h = (el.h + dhN).coerceIn(0.05f, (1f - el.y).coerceAtLeast(0.05f)),
                )
            })
        }
    }

    fun openTextEditor(element: SlideElement) {
        if (element.type == SlideElementType.TEXT) {
            _uiState.value = _uiState.value.copy(editingText = element)
        }
    }

    fun dismissTextEditor() {
        _uiState.value = _uiState.value.copy(editingText = null)
    }

    fun updateTextContent(id: String, content: String) {
        mutateDoc { doc ->
            doc.copy(elements = doc.elements.map { if (it.id == id) it.copy(content = content) else it })
        }
        _uiState.value = _uiState.value.copy(editingText = null)
    }

    fun deleteSelected() {
        val id = _uiState.value.selectedId ?: return
        mutateDoc { it.copy(elements = it.elements.filter { e -> e.id != id }) }
        _uiState.value = _uiState.value.copy(selectedId = null)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun save(widthPx: Int = 1920, heightPx: Int = 1080) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                val bytes = withContext(Dispatchers.Default) {
                    rasterize(_uiState.value.doc, widthPx, heightPx, ::resolveImage)
                }
                slideRepository.uploadSlide(sessionId, talkId, bytes, replaceSlide)
                _uiState.value = _uiState.value.copy(isSaving = false, saveSuccess = true)
            } catch (e: SlideQueuedLocallyException) {
                _uiState.value = _uiState.value.copy(isSaving = false, savedLocally = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = errorClassifier.classify(e),
                )
            }
        }
    }

    /**
     * Ask the LLM for a full deck about [topic] and upload each slide to the
     * current talk. The maker screen closes once uploads are in flight — the
     * generated slides appear in the talk's slide list as they finish.
     */
    fun generateDeck(topic: String, numSlides: Int = 6, language: String = "en") {
        if (topic.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAiBusy = true, aiStatus = "Generating slides\u2026")
            try {
                val response = slideAiApi.generateDeck(
                    GenerateDeckRequest(topic = topic, numSlides = numSlides, language = language)
                )
                if (response.slides.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isAiBusy = false, aiStatus = null, error = "AI returned no slides",
                    )
                    return@launch
                }
                _uiState.value = _uiState.value.copy(aiStatus = "Uploading slides\u2026")
                response.slides.forEachIndexed { idx, doc ->
                    val bytes = withContext(Dispatchers.Default) {
                        rasterize(doc, 1920, 1080, ::resolveImage)
                    }
                    // First slide can replace the current one; the rest append.
                    val replace = if (idx == 0) replaceSlide else null
                    slideRepository.uploadSlide(sessionId, talkId, bytes, replace)
                }
                _uiState.value = _uiState.value.copy(
                    isAiBusy = false, aiStatus = null, saveSuccess = true,
                )
            } catch (e: SlideQueuedLocallyException) {
                _uiState.value = _uiState.value.copy(
                    isAiBusy = false, aiStatus = null, savedLocally = true,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAiBusy = false, aiStatus = null, error = errorClassifier.classify(e),
                )
            }
        }
    }

    /**
     * Ask the LLM to rewrite the current slide given [instruction]. The image
     * element cache is cleared — the LLM only produces text/shape elements so
     * any stale local image references wouldn't resolve anyway.
     */
    fun improveCurrentSlide(instruction: String, language: String = "en") {
        if (instruction.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAiBusy = true, aiStatus = "Rewriting slide\u2026")
            try {
                val response = slideAiApi.improveSlide(
                    ImproveSlideRequest(
                        doc = _uiState.value.doc,
                        instruction = instruction,
                        language = language,
                    )
                )
                _uiState.value = _uiState.value.copy(
                    doc = response.doc,
                    selectedId = null,
                    editingText = null,
                    isAiBusy = false,
                    aiStatus = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAiBusy = false, aiStatus = null, error = errorClassifier.classify(e),
                )
            }
        }
    }

    private fun mutateDoc(transform: (SlideDoc) -> SlideDoc) {
        _uiState.value = _uiState.value.copy(doc = transform(_uiState.value.doc))
    }

    private fun nextZ(): Int = (_uiState.value.doc.elements.maxOfOrNull { it.z } ?: 0) + 1
}

/**
 * Rasterize a [SlideDoc] to a JPEG byte array. Runs off the main thread.
 */
private fun rasterize(
    doc: SlideDoc,
    widthPx: Int,
    heightPx: Int,
    imageResolver: (String) -> Bitmap?,
): ByteArray {
    val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    SlideDocRasterizer.draw(canvas, doc, widthPx.toFloat(), heightPx.toFloat(), imageResolver)
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
    bmp.recycle()
    return out.toByteArray()
}
