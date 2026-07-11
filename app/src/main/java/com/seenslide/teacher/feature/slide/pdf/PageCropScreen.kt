package com.seenslide.teacher.feature.slide.pdf

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.seenslide.teacher.R
import kotlin.math.max
import kotlin.math.min

/**
 * Crop screen: shows the PDF page with a draggable rectangle
 * so the teacher can select the interesting region.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageCropScreen(
    bitmap: Bitmap,
    isUploading: Boolean,
    onCropConfirm: (leftFrac: Float, topFrac: Float, rightFrac: Float, bottomFrac: Float) -> Unit,
    onUseFullPage: () -> Unit,
    onBack: () -> Unit,
) {
    // Selection rect in pixel coordinates of the composable image area
    var selectionStart by remember { mutableStateOf<Offset?>(null) }
    var selectionEnd by remember { mutableStateOf<Offset?>(null) }
    var imageAreaSize by remember { mutableStateOf(IntSize.Zero) }

    val hasSelection = selectionStart != null && selectionEnd != null

    fun resetSelection() {
        selectionStart = null
        selectionEnd = null
    }

    fun normalizedRect(): Rect? {
        val s = selectionStart ?: return null
        val e = selectionEnd ?: return null
        val w = imageAreaSize.width.toFloat().coerceAtLeast(1f)
        val h = imageAreaSize.height.toFloat().coerceAtLeast(1f)
        return Rect(
            left = (min(s.x, e.x) / w).coerceIn(0f, 1f),
            top = (min(s.y, e.y) / h).coerceIn(0f, 1f),
            right = (max(s.x, e.x) / w).coerceIn(0f, 1f),
            bottom = (max(s.y, e.y) / h).coerceIn(0f, 1f),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crop_page_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isUploading) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (hasSelection) {
                        IconButton(onClick = ::resetSelection, enabled = !isUploading) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reset_selection))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Instruction card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CropFree,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.crop_page_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Text(
                        text = stringResource(R.string.crop_page_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
            }

            // Image with crop overlay
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspectRatio)
                            .onSizeChanged { imageAreaSize = it },
                    ) {
                        // The page bitmap
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )

                        // Touch handler + overlay
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            selectionStart = offset
                                            selectionEnd = offset
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            selectionEnd = change.position
                                        },
                                    )
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        // Tap resets selection
                                        selectionStart = null
                                        selectionEnd = null
                                    }
                                },
                        ) {
                            val s = selectionStart
                            val e = selectionEnd
                            if (s != null && e != null) {
                                val left = min(s.x, e.x).coerceIn(0f, size.width)
                                val top = min(s.y, e.y).coerceIn(0f, size.height)
                                val right = max(s.x, e.x).coerceIn(0f, size.width)
                                val bottom = max(s.y, e.y).coerceIn(0f, size.height)

                                // Dim everything outside selection
                                val dimColor = Color.Black.copy(alpha = 0.5f)

                                // Top strip
                                drawRect(dimColor, Offset.Zero, Size(size.width, top))
                                // Bottom strip
                                drawRect(dimColor, Offset(0f, bottom), Size(size.width, size.height - bottom))
                                // Left strip (between top and bottom)
                                drawRect(dimColor, Offset(0f, top), Size(left, bottom - top))
                                // Right strip (between top and bottom)
                                drawRect(dimColor, Offset(right, top), Size(size.width - right, bottom - top))

                                // Dashed border around selection
                                val borderPath = Path().apply {
                                    addRect(Rect(left, top, right, bottom))
                                }
                                drawPath(
                                    path = borderPath,
                                    color = Color.White,
                                    style = Stroke(
                                        width = 3f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                                    ),
                                )

                                // Corner handles
                                val handleRadius = 8f
                                listOf(
                                    Offset(left, top),
                                    Offset(right, top),
                                    Offset(left, bottom),
                                    Offset(right, bottom),
                                ).forEach { corner ->
                                    drawCircle(Color.White, handleRadius + 2f, corner)
                                    drawCircle(Color(0xFF6750A4), handleRadius, corner)
                                }
                            }
                        }
                    }

                    if (isUploading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }
            }

            // Status text
            if (!hasSelection) {
                Text(
                    text = stringResource(R.string.crop_drag_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        val r = normalizedRect()
                        if (r != null && r.width > 0.02f && r.height > 0.02f) {
                            onCropConfirm(r.left, r.top, r.right, r.bottom)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    enabled = hasSelection && !isUploading,
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.Crop, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.crop_and_save))
                }

                OutlinedButton(
                    onClick = onUseFullPage,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    enabled = !isUploading,
                ) {
                    Icon(Icons.Default.Fullscreen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.use_full_page))
                }
            }
        }
    }
}
