package com.seenslide.teacher.feature.slide.maker

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Rectangle
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.seenslide.teacher.core.slidedoc.ShapeKind
import com.seenslide.teacher.core.slidedoc.SlideDocRenderer
import com.seenslide.teacher.core.slidedoc.SlideElement

private val BACKGROUND_COLORS = listOf(
    "#FFFFFF", "#FFFDE7", "#E3F2FD", "#E8F5E9",
    "#FFF3E0", "#F3E5F5", "#FFEBEE", "#212121",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlideMakerScreen(
    viewModel: SlideMakerViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showGenerateDialog by remember { mutableStateOf(false) }
    var showImproveDialog by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.addImageFromUri(uri) }

    LaunchedEffect(state.saveSuccess, state.savedLocally) {
        if (state.saveSuccess || state.savedLocally) onDone()
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Slide") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showGenerateDialog = true },
                        enabled = !state.isSaving && !state.isAiBusy,
                    ) {
                        Icon(Icons.Default.AutoAwesome, "AI generate deck")
                    }
                    IconButton(
                        onClick = { viewModel.save() },
                        enabled = !state.isSaving && !state.isAiBusy,
                    ) {
                        if (state.isSaving || state.isAiBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Check, "Save")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .aspectRatio(state.doc.aspect)
                    .border(1.dp, MaterialTheme.colorScheme.outline)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { viewModel.selectElement(null) })
                    },
            ) {
                SlideDocRenderer(
                    doc = state.doc,
                    modifier = Modifier.fillMaxSize(),
                    imageResolver = { src -> viewModel.resolveImage(src) },
                )
                SelectionOverlay(
                    state = state,
                    onTap = { id ->
                        viewModel.selectElement(id)
                        val el = state.doc.elements.find { it.id == id }
                        if (el != null && el.type == com.seenslide.teacher.core.slidedoc.SlideElementType.TEXT) {
                            viewModel.openTextEditor(el)
                        }
                    },
                    onSelect = viewModel::selectElement,
                    onMove = viewModel::moveElement,
                    onResize = viewModel::resizeElement,
                )
            }

            Spacer(Modifier.height(4.dp))
            ToolRow(
                onAddText = viewModel::addTextBox,
                onAddRect = { viewModel.addShape(ShapeKind.RECT) },
                onAddCircle = { viewModel.addShape(ShapeKind.CIRCLE) },
                onAddImage = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onImprove = { showImproveDialog = true },
                canImprove = state.doc.elements.isNotEmpty() && !state.isAiBusy,
                onDelete = viewModel::deleteSelected,
                canDelete = state.selectedId != null,
            )
            state.aiStatus?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            BackgroundPicker(current = state.doc.background.color ?: "#FFFFFF") {
                viewModel.setBackgroundColor(it)
            }
            if (state.doc.elements.isEmpty()) {
                TemplatePicker(onPick = viewModel::applyTemplate)
            }
        }
    }

    state.editingText?.let { target ->
        TextEditDialog(
            initial = target.content ?: "",
            onDismiss = viewModel::dismissTextEditor,
            onConfirm = { viewModel.updateTextContent(target.id, it) },
        )
    }

    if (showGenerateDialog) {
        AiPromptDialog(
            title = "Generate deck with AI",
            label = "Topic",
            confirmLabel = "Generate",
            onDismiss = { showGenerateDialog = false },
            onConfirm = { prompt ->
                showGenerateDialog = false
                viewModel.generateDeck(prompt)
            },
        )
    }
    if (showImproveDialog) {
        AiPromptDialog(
            title = "Improve this slide",
            label = "How should we change it?",
            confirmLabel = "Rewrite",
            onDismiss = { showImproveDialog = false },
            onConfirm = { prompt ->
                showImproveDialog = false
                viewModel.improveCurrentSlide(prompt)
            },
        )
    }
}

@Composable
private fun SelectionOverlay(
    state: SlideMakerUiState,
    onTap: (String) -> Unit,
    onSelect: (String?) -> Unit,
    onMove: (String, Float, Float) -> Unit,
    onResize: (String, Float, Float) -> Unit,
) {
    // Tap hit boxes with drag-to-move; the selected element gets a resize
    // handle at the bottom-right corner. Pixel deltas are converted to
    // normalized coords using the overlay's pixel dimensions.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val slideWpx = with(density) { maxWidth.toPx() }
        val slideHpx = with(density) { maxHeight.toPx() }
        val w = maxWidth
        val h = maxHeight

        state.doc.elements.forEach { el ->
            val selected = el.id == state.selectedId
            Box(
                modifier = Modifier
                    .offset(x = w * el.x, y = h * el.y)
                    .width(w * el.w)
                    .height(h * el.h)
                    .then(
                        if (selected)
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary)
                        else Modifier
                    )
                    .pointerInput(el.id) {
                        detectTapGestures(onTap = { onTap(el.id) })
                    }
                    .pointerInput(el.id, slideWpx, slideHpx) {
                        detectDragGestures(
                            onDragStart = { onSelect(el.id) },
                            onDrag = { change, drag ->
                                change.consume()
                                if (slideWpx > 0f && slideHpx > 0f) {
                                    onMove(el.id, drag.x / slideWpx, drag.y / slideHpx)
                                }
                            },
                        )
                    },
            )
            if (selected) {
                val handleSize = 20.dp
                Box(
                    modifier = Modifier
                        .offset(
                            x = w * (el.x + el.w) - handleSize / 2,
                            y = h * (el.y + el.h) - handleSize / 2,
                        )
                        .size(handleSize)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .pointerInput(el.id, slideWpx, slideHpx) {
                            detectDragGestures(
                                onDrag = { change, drag ->
                                    change.consume()
                                    if (slideWpx > 0f && slideHpx > 0f) {
                                        onResize(el.id, drag.x / slideWpx, drag.y / slideHpx)
                                    }
                                },
                            )
                        },
                )
            }
        }
    }
}

@Composable
private fun ToolRow(
    onAddText: () -> Unit,
    onAddRect: () -> Unit,
    onAddCircle: () -> Unit,
    onAddImage: () -> Unit,
    onImprove: () -> Unit,
    canImprove: Boolean,
    onDelete: () -> Unit,
    canDelete: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onAddText) { Icon(Icons.Default.Title, "Add text") }
        IconButton(onClick = onAddImage) { Icon(Icons.Default.Image, "Add image") }
        IconButton(onClick = onAddRect) { Icon(Icons.Default.Rectangle, "Add rectangle") }
        IconButton(onClick = onAddCircle) { Icon(Icons.Default.Circle, "Add circle") }
        IconButton(onClick = onImprove, enabled = canImprove) {
            Icon(
                Icons.Default.AutoAwesome,
                "Improve with AI",
                tint = if (canImprove) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            )
        }
        Spacer(Modifier.fillMaxWidth().weight(1f))
        IconButton(onClick = onDelete, enabled = canDelete) {
            Icon(
                Icons.Default.Delete, "Delete",
                tint = if (canDelete) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            )
        }
    }
}

@Composable
private fun AiPromptDialog(
    title: String,
    label: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun TemplatePicker(onPick: (SlideTemplate) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Templates", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SlideTemplates.all.size) { idx ->
                val tpl = SlideTemplates.all[idx]
                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .widthIn(min = 96.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp),
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(8.dp),
                        )
                        .clickable { onPick(tpl) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tpl.label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun BackgroundPicker(current: String, onPick: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Background", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(BACKGROUND_COLORS.size) { idx ->
                val hex = BACKGROUND_COLORS[idx]
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(parseUiColor(hex), CircleShape)
                        .border(
                            if (hex == current) 3.dp else 1.dp,
                            if (hex == current) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            CircleShape,
                        )
                        .clickable { onPick(hex) },
                )
            }
        }
    }
}

@Composable
private fun TextEditDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit text") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions.Default,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun parseUiColor(hex: String): Color {
    val clean = hex.removePrefix("#")
    val value = when (clean.length) {
        6 -> 0xFF000000 or clean.toLong(16)
        8 -> clean.toLong(16)
        else -> 0xFF000000
    }
    return Color(value)
}
