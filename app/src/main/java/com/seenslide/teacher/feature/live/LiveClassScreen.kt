package com.seenslide.teacher.feature.live

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.seenslide.teacher.R
import com.seenslide.teacher.core.drawing.DrawingCanvas
import com.seenslide.teacher.core.drawing.DrawingToolbar

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LiveClassScreen(
    viewModel: LiveClassViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Audio permission
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            viewModel.startVoiceStreaming()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    // End class confirmation
    if (uiState.showEndConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissEndConfirmation,
            title = { Text(stringResource(R.string.end_class)) },
            text = { Text(stringResource(R.string.end_class_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.endClass()
                    onBack()
                }) { Text(stringResource(R.string.end_class), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissEndConfirmation) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.live_class))
                        if (uiState.isLive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Default.FiberManualRecord,
                                contentDescription = "Live",
                                tint = Color.Red,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "LIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Red,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isLive) viewModel.requestEndClass() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (uiState.isLive) Color(0xFF1B5E20) else MaterialTheme.colorScheme.surface,
                    titleContentColor = if (uiState.isLive) Color.White else MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = if (uiState.isLive) Color.White else MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Slide display with drawing overlay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f),
                ) {
                    if (uiState.slides.isNotEmpty()) {
                        val currentSlide = uiState.slides[uiState.currentSlideIndex]
                        AsyncImage(
                            model = viewModel.slideImageUrl(currentSlide.slideNumber),
                            contentDescription = "Slide ${currentSlide.slideNumber}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.no_slides_draw),
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    // Drawing overlay (always active during live)
                    if (uiState.isLive) {
                        DrawingCanvas(
                            modifier = Modifier.fillMaxSize(),
                            drawingState = viewModel.drawingState,
                            recordingStartTime = if (uiState.isRecordingStrokes) {
                                viewModel.strokeRecorder.recordingStartTime
                            } else 0L,
                            onStrokeStarted = viewModel::onStrokeStarted,
                            onStrokePointAdded = viewModel::onStrokePointAdded,
                            onStrokeCompleted = viewModel::onStrokeCompleted,
                        )
                    }
                }
            }

            // Drawing toolbar (only when live)
            if (uiState.isLive) {
                DrawingToolbar(
                    currentTool = viewModel.drawingState.currentTool,
                    currentColor = viewModel.drawingState.currentColor,
                    currentWidth = viewModel.drawingState.currentWidth,
                    canUndo = viewModel.drawingState.canUndo,
                    canRedo = viewModel.drawingState.canRedo,
                    onToolSelected = { viewModel.drawingState.currentTool = it },
                    onColorSelected = { viewModel.drawingState.currentColor = it },
                    onWidthChanged = { viewModel.drawingState.currentWidth = it },
                    onUndo = { viewModel.drawingState.undo() },
                    onRedo = { viewModel.drawingState.redo() },
                )
            }

            // Controls bar
            ControlsBar(
                uiState = uiState,
                onGoLive = viewModel::goLive,
                onEndClass = viewModel::requestEndClass,
                onToggleVoice = {
                    if (uiState.isVoiceStreaming) {
                        viewModel.stopVoiceStreaming()
                    } else {
                        if (hasAudioPermission) {
                            viewModel.startVoiceStreaming()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                onPreviousSlide = viewModel::previousSlide,
                onNextSlide = viewModel::nextSlide,
            )
        }
    }
}

@Composable
private fun ControlsBar(
    uiState: LiveClassUiState,
    onGoLive: () -> Unit,
    onEndClass: () -> Unit,
    onToggleVoice: () -> Unit,
    onPreviousSlide: () -> Unit,
    onNextSlide: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .navigationBarsPadding()
            .padding(12.dp),
    ) {
        if (!uiState.isLive) {
            // Not live yet — show Go Live button
            Button(
                onClick = onGoLive,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1B5E20),
                ),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.go_live),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        } else {
            // Live controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Previous slide
                IconButton(
                    onClick = onPreviousSlide,
                    enabled = uiState.currentSlideIndex > 0,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous slide",
                        modifier = Modifier.size(32.dp),
                    )
                }

                // Slide counter
                Text(
                    text = "${uiState.currentSlideIndex + 1} / ${uiState.slides.size.coerceAtLeast(1)}",
                    style = MaterialTheme.typography.titleMedium,
                )

                // Next slide
                IconButton(
                    onClick = onNextSlide,
                    enabled = uiState.currentSlideIndex < uiState.slides.size - 1,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next slide",
                        modifier = Modifier.size(32.dp),
                    )
                }

                // Voice toggle
                FilledTonalButton(
                    onClick = onToggleVoice,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (uiState.isVoiceStreaming) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                    ),
                ) {
                    Icon(
                        if (uiState.isVoiceStreaming) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = if (uiState.isVoiceStreaming) "Mute" else "Unmute",
                    )
                }

                // End class
                FilledTonalButton(
                    onClick = onEndClass,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.end))
                }
            }
        }
    }
}
