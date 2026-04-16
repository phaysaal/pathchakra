package com.seenslide.teacher.feature.slide.editor

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.seenslide.teacher.R
import com.seenslide.teacher.core.drawing.DrawTool
import com.seenslide.teacher.core.drawing.DrawElement
import com.seenslide.teacher.core.drawing.DrawingCanvas
import com.seenslide.teacher.core.drawing.DrawingToolbar
import com.seenslide.teacher.core.drawing.TextStroke

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SlideEditorScreen(
    viewModel: SlideEditorViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            Toast.makeText(context, context.getString(R.string.slide_saved), Toast.LENGTH_SHORT).show()
            viewModel.onSaveSuccessHandled()
            onBack()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    // Text input dialog
    if (uiState.showTextDialog) {
        TextInputDialog(
            onDismiss = { viewModel.dismissTextDialog() },
            onConfirm = { text, x, y ->
                val textElement = DrawElement.TextElement(
                    TextStroke(
                        text = text,
                        x = x,
                        y = y,
                        color = uiState.currentColor,
                        tPlaced = if (viewModel.strokeRecorder.isRecording) {
                            System.currentTimeMillis() - viewModel.strokeRecorder.recordingStartTime
                        } else 0L,
                    ),
                )
                viewModel.drawingState.addElement(textElement)
                viewModel.onStrokeCompleted(textElement)
                viewModel.dismissTextDialog()
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.slide_editor))
                        if (uiState.isRecording) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Default.FiberManualRecord,
                                contentDescription = "Recording",
                                tint = Color.Red,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    // Record toggle
                    IconButton(
                        onClick = {
                            if (uiState.isRecording) viewModel.stopRecording()
                            else viewModel.startRecording()
                        },
                    ) {
                        Icon(
                            if (uiState.isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = if (uiState.isRecording) "Stop recording" else "Start recording",
                            tint = if (uiState.isRecording) Color.Red else MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    // Save button
                    IconButton(
                        onClick = {
                            // Capture canvas to bitmap
                            val bitmap = captureCanvasBitmap(view, viewModel)
                            if (bitmap != null) {
                                viewModel.saveSlideAsImage(bitmap)
                            }
                        },
                        enabled = !uiState.isSaving,
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Save, contentDescription = "Save slide")
                        }
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
                .padding(padding),
        ) {
            // Canvas area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                DrawingCanvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .background(Color.White),
                    drawingState = viewModel.drawingState,
                    backgroundBitmap = viewModel.backgroundBitmap,
                    recordingStartTime = if (uiState.isRecording) viewModel.strokeRecorder.recordingStartTime else 0L,
                    onStrokeStarted = viewModel::onStrokeStarted,
                    onStrokePointAdded = viewModel::onStrokePointAdded,
                    onStrokeCompleted = viewModel::onStrokeCompleted,
                )
            }

            // Toolbar at bottom
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
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }
}

/**
 * Captures the drawing canvas content as a Bitmap for upload.
 * Uses the Android View drawing cache approach.
 */
private fun captureCanvasBitmap(
    view: android.view.View,
    viewModel: SlideEditorViewModel,
): Bitmap? {
    // Create a bitmap the size of a standard slide
    val width = 1920
    val height = 1440 // 4:3
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // Draw white background
    canvas.drawColor(android.graphics.Color.WHITE)

    // Draw background image if present
    viewModel.backgroundBitmap?.let { bg ->
        val srcRect = android.graphics.Rect(0, 0, bg.width, bg.height)
        val dstRect = android.graphics.Rect(0, 0, width, height)
        canvas.drawBitmap(bg, srcRect, dstRect, null)
    }

    // Draw all elements using Android Canvas (not Compose)
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
                val wMul = if (s.tool == DrawTool.HIGHLIGHTER) 3f else 1f
                paint.strokeWidth = s.width * (width / 1000f) * wMul

                val path = android.graphics.Path()
                val pts = s.points
                if (pts.size >= 2) {
                    path.moveTo(pts[0].x * width, pts[0].y * height)
                    for (i in 1 until pts.size) {
                        val p0 = pts[i - 1]
                        val p1 = pts[i]
                        val midX = (p0.x + p1.x) / 2f * width
                        val midY = (p0.y + p1.y) / 2f * height
                        path.quadTo(p0.x * width, p0.y * height, midX, midY)
                    }
                    path.lineTo(pts.last().x * width, pts.last().y * height)
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
                        minOf(s.startX, s.endX) * width, minOf(s.startY, s.endY) * height,
                        maxOf(s.startX, s.endX) * width, maxOf(s.startY, s.endY) * height, paint,
                    )
                    DrawTool.CIRCLE -> canvas.drawOval(
                        minOf(s.startX, s.endX) * width, minOf(s.startY, s.endY) * height,
                        maxOf(s.startX, s.endX) * width, maxOf(s.startY, s.endY) * height, paint,
                    )
                    DrawTool.LINE, DrawTool.ARROW -> canvas.drawLine(
                        s.startX * width, s.startY * height,
                        s.endX * width, s.endY * height, paint,
                    )
                    else -> {}
                }
            }
            is DrawElement.TextElement -> {
                val t = element.textStroke
                val textPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = t.color.toInt()
                    textSize = t.fontSize * height
                    style = android.graphics.Paint.Style.FILL
                }
                canvas.drawText(t.text, t.x * width, t.y * height + textPaint.textSize, textPaint)
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
