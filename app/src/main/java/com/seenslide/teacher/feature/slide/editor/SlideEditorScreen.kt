package com.seenslide.teacher.feature.slide.editor

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.seenslide.teacher.R
import com.seenslide.teacher.core.drawing.DrawElement
import com.seenslide.teacher.core.drawing.DrawTool
import com.seenslide.teacher.core.drawing.DrawingCanvas
import com.seenslide.teacher.core.drawing.DrawingToolbar
import com.seenslide.teacher.core.drawing.FloatingVerticalToolbar
import com.seenslide.teacher.core.drawing.TextStroke

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SlideEditorScreen(
    viewModel: SlideEditorViewModel,
    onSlideSaved: (Int, String?) -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showExitConfirm by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            viewModel.startRecordingWithVoice()
        } else {
            viewModel.startRecordingStrokesOnly()
        }
    }

    fun attemptBack() {
        if (uiState.hasUnsavedChanges || uiState.isRecording) {
            showExitConfirm = true
        } else {
            onBack()
        }
    }

    BackHandler {
        attemptBack()
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            val msg = if (uiState.savedLocally) {
                context.getString(R.string.slide_saved_locally)
            } else {
                context.getString(R.string.slide_saved)
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            val uploadedSlide = uiState.uploadedSlide
            viewModel.onSaveSuccessHandled()
            if (uploadedSlide != null) {
                onSlideSaved(uploadedSlide.slideNumber, uploadedSlide.slideId)
            } else {
                onBack()
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.dismissError()
        }
    }

    if (uiState.showTextDialog) {
        TextInputDialog(
            onDismiss = { viewModel.dismissTextDialog() },
            onConfirm = { text, x, y ->
                viewModel.addTextElement(
                    DrawElement.TextElement(
                        TextStroke(
                            text = text,
                            x = x,
                            y = y,
                            color = uiState.currentColor,
                            tPlaced = if (viewModel.strokeRecorder.isRecording) {
                                System.currentTimeMillis() - viewModel.strokeRecorder.recordingStartTime
                            } else {
                                0L
                            },
                        ),
                    ),
                )
            },
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(stringResource(R.string.leave_editor_title)) },
            text = { Text(stringResource(R.string.leave_editor_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirm = false
                        if (uiState.isRecording) {
                            viewModel.stopRecording()
                        }
                        onBack()
                    },
                ) {
                    Text(stringResource(R.string.leave_without_saving))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Fullscreen drawing mode
    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
            ) {
                DrawingCanvas(
                    modifier = Modifier.fillMaxSize(),
                    drawingState = viewModel.drawingState,
                    backgroundBitmap = viewModel.backgroundBitmap,
                    zoomable = true,
                    showGuides = viewModel.backgroundBitmap == null,
                    recordingStartTime = if (uiState.isRecording) {
                        viewModel.strokeRecorder.recordingStartTime
                    } else {
                        0L
                    },
                    onStrokeStarted = viewModel::onStrokeStarted,
                    onStrokePointAdded = viewModel::onStrokePointAdded,
                    onStrokeCompleted = viewModel::onStrokeCompleted,
                )

                // Floating vertical toolbar on the right
                FloatingVerticalToolbar(
                    currentTool = uiState.currentTool,
                    currentColor = uiState.currentColor,
                    canUndo = uiState.canUndo,
                    canRedo = uiState.canRedo,
                    onToolSelected = { tool ->
                        if (tool == DrawTool.TEXT) {
                            viewModel.showTextDialog()
                        } else {
                            viewModel.onToolSelected(tool)
                        }
                    },
                    onColorSelected = viewModel::onColorSelected,
                    onUndo = viewModel::onUndo,
                    onRedo = viewModel::onRedo,
                    onExitFullscreen = { isFullscreen = false },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp),
                )
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.clear_canvas)) },
            text = { Text(stringResource(R.string.clear_canvas_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        viewModel.clearCanvas()
                    },
                ) {
                    Text(
                        stringResource(R.string.clear_canvas),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.slide_editor)) },
                navigationIcon = {
                    IconButton(onClick = ::attemptBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (uiState.isRecording) {
                                viewModel.stopRecording()
                            } else if (hasAudioPermission) {
                                viewModel.startRecordingWithVoice()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    ) {
                        Icon(
                            if (uiState.isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = if (uiState.isRecording) {
                                stringResource(R.string.stop_recording)
                            } else {
                                stringResource(R.string.start_recording)
                            },
                            tint = if (uiState.isRecording) Color.Red else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EditorStatusCard(
                statusLabel = editorStatusLabel(uiState),
                sourceLabel = sourceLabel(viewModel.mode),
                toolLabel = toolLabel(uiState.currentTool),
                strokeWidth = uiState.currentWidth,
                hasUnsavedChanges = uiState.hasUnsavedChanges,
                isSaving = uiState.isSaving,
                onClear = { showClearConfirm = true },
                onSave = {
                    captureCanvasBitmap(viewModel)?.let { bitmap ->
                        viewModel.saveSlideAsImage(bitmap)
                    }
                },
            )

            WhiteboardFrame(
                modifier = Modifier.weight(1f),
                hasBackground = viewModel.backgroundBitmap != null,
            ) {
                DrawingCanvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .background(Color.White),
                    drawingState = viewModel.drawingState,
                    backgroundBitmap = viewModel.backgroundBitmap,
                    zoomable = true,
                    showGuides = viewModel.backgroundBitmap == null,
                    recordingStartTime = if (uiState.isRecording) {
                        viewModel.strokeRecorder.recordingStartTime
                    } else {
                        0L
                    },
                    onStrokeStarted = viewModel::onStrokeStarted,
                    onStrokePointAdded = viewModel::onStrokePointAdded,
                    onStrokeCompleted = viewModel::onStrokeCompleted,
                )
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.editor_toolbar_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    DrawingToolbar(
                        currentTool = uiState.currentTool,
                        currentColor = uiState.currentColor,
                        currentWidth = uiState.currentWidth,
                        canUndo = uiState.canUndo,
                        canRedo = uiState.canRedo,
                        onToolSelected = { tool ->
                            if (tool == DrawTool.TEXT) {
                                viewModel.showTextDialog()
                            } else {
                                viewModel.onToolSelected(tool)
                            }
                        },
                        onColorSelected = viewModel::onColorSelected,
                        onWidthChanged = viewModel::onWidthChanged,
                        onUndo = viewModel::onUndo,
                        onRedo = viewModel::onRedo,
                        onExpandFullscreen = { isFullscreen = true },
                        modifier = Modifier.navigationBarsPadding(),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorStatusCard(
    statusLabel: String,
    sourceLabel: String,
    toolLabel: String,
    strokeWidth: Float,
    hasUnsavedChanges: Boolean,
    isSaving: Boolean,
    onClear: () -> Unit,
    onSave: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusBadge(label = statusLabel)
                Spacer(modifier = Modifier.width(8.dp))
                SourceBadge(label = sourceLabel)
                Spacer(modifier = Modifier.weight(1f))
                if (hasUnsavedChanges) {
                    Text(
                        text = stringResource(R.string.unsaved_changes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.ready_to_save),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.current_tool_label, toolLabel),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.stroke_width_label, strokeWidth.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.clear_canvas))
                }
                Button(
                    onClick = onSave,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isSaving) {
                            stringResource(R.string.saving_status)
                        } else {
                            stringResource(R.string.save_slide)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun SourceBadge(label: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WhiteboardFrame(
    modifier: Modifier = Modifier,
    hasBackground: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (hasBackground) {
                    stringResource(R.string.draw_on_slide_hint)
                } else {
                    stringResource(R.string.blank_board_hint)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
                content = content,
            )
        }
    }
}

@Composable
private fun editorStatusLabel(uiState: SlideEditorUiState): String {
    return when {
        uiState.isSaving -> stringResource(R.string.saving_status)
        uiState.isRecording -> stringResource(R.string.recording_status)
        else -> stringResource(R.string.editing_status)
    }
}

@Composable
private fun sourceLabel(mode: String): String {
    return when {
        mode.startsWith("photo:") -> stringResource(R.string.photo_slide_source)
        mode.startsWith("pdf") -> stringResource(R.string.pdf_slide_source)
        else -> stringResource(R.string.blank_slide_source)
    }
}

@Composable
private fun toolLabel(tool: DrawTool): String {
    return when (tool) {
        DrawTool.PEN -> stringResource(R.string.pen_tool)
        DrawTool.HIGHLIGHTER -> stringResource(R.string.highlighter_tool)
        DrawTool.ERASER -> stringResource(R.string.eraser_tool)
        DrawTool.RECT -> stringResource(R.string.rectangle_tool)
        DrawTool.CIRCLE -> stringResource(R.string.circle_tool)
        DrawTool.LINE -> stringResource(R.string.line_tool)
        DrawTool.ARROW -> stringResource(R.string.arrow_tool)
        DrawTool.LASER -> stringResource(R.string.laser_tool)
        DrawTool.TEXT -> stringResource(R.string.text_tool)
    }
}

/**
 * Captures the slide canvas content as a bitmap for upload.
 */
private fun captureCanvasBitmap(
    viewModel: SlideEditorViewModel,
): Bitmap? {
    val width = 1920
    val height = 1440
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    canvas.drawColor(android.graphics.Color.WHITE)

    viewModel.backgroundBitmap?.let { bg ->
        // Fit letterboxed (match the on-screen canvas), never stretch — so
        // the exported 4:3 slide preserves the source image's proportions.
        val s = minOf(width.toFloat() / bg.width, height.toFloat() / bg.height)
        val dw = bg.width * s
        val dh = bg.height * s
        val left = (width - dw) / 2f
        val top = (height - dh) / 2f
        val srcRect = android.graphics.Rect(0, 0, bg.width, bg.height)
        val dstRect = android.graphics.RectF(left, top, left + dw, top + dh)
        canvas.drawBitmap(bg, srcRect, dstRect, null)
    }

    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        style = android.graphics.Paint.Style.STROKE
    }

    for (element in viewModel.drawingState.elements) {
        when (element) {
            is DrawElement.FreehandElement -> {
                val s = element.stroke
                paint.color = s.color.toInt()
                paint.alpha = if (s.tool == DrawTool.HIGHLIGHTER) 102 else 255
                val widthMultiplier = if (s.tool == DrawTool.HIGHLIGHTER) 3f else 1f
                paint.strokeWidth = s.width * (width / 1000f) * widthMultiplier

                val path = android.graphics.Path()
                val points = s.points
                if (points.size >= 2) {
                    path.moveTo(points[0].x * width, points[0].y * height)
                    for (i in 1 until points.size) {
                        val p0 = points[i - 1]
                        val p1 = points[i]
                        val midX = (p0.x + p1.x) / 2f * width
                        val midY = (p0.y + p1.y) / 2f * height
                        path.quadTo(p0.x * width, p0.y * height, midX, midY)
                    }
                    path.lineTo(points.last().x * width, points.last().y * height)
                    canvas.drawPath(path, paint)
                }
            }

            is DrawElement.ShapeElement -> {
                val s = element.shape
                paint.color = s.color.toInt()
                paint.alpha = 255
                paint.strokeWidth = s.width * (width / 1000f)
                when (s.tool) {
                    DrawTool.RECT -> canvas.drawRect(
                        minOf(s.startX, s.endX) * width,
                        minOf(s.startY, s.endY) * height,
                        maxOf(s.startX, s.endX) * width,
                        maxOf(s.startY, s.endY) * height,
                        paint,
                    )

                    DrawTool.CIRCLE -> canvas.drawOval(
                        minOf(s.startX, s.endX) * width,
                        minOf(s.startY, s.endY) * height,
                        maxOf(s.startX, s.endX) * width,
                        maxOf(s.startY, s.endY) * height,
                        paint,
                    )

                    DrawTool.LINE -> canvas.drawLine(
                        s.startX * width,
                        s.startY * height,
                        s.endX * width,
                        s.endY * height,
                        paint,
                    )

                    DrawTool.ARROW -> {
                        canvas.drawLine(
                            s.startX * width,
                            s.startY * height,
                            s.endX * width,
                            s.endY * height,
                            paint,
                        )
                    }

                    else -> Unit
                }
            }

            is DrawElement.TextElement -> {
                val text = element.textStroke
                val textPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = text.color.toInt()
                    textSize = text.fontSize * height
                }
                canvas.drawText(
                    text.text,
                    text.x * width,
                    text.y * height,
                    textPaint,
                )
            }
        }
    }

    return bitmap
}

@Composable
private fun TextInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (text: String, x: Float, y: Float) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_text)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.type_here)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text, 0.1f, 0.5f) },
                enabled = text.isNotBlank(),
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
