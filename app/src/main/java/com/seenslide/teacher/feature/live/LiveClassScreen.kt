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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.seenslide.teacher.R
import com.seenslide.teacher.core.drawing.DrawingCanvas
import com.seenslide.teacher.core.drawing.DrawingToolbar
import com.seenslide.teacher.core.network.websocket.WsConnectionState

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LiveClassScreen(
    viewModel: LiveClassViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Live teaching must survive the screen-timeout: if the display
    // sleeps, Android suspends the process and the voice stream dies.
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

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
            viewModel.startVoiceStreaming()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.dismissError()
        }
    }

    if (uiState.showEndConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissEndConfirmation,
            title = { Text(stringResource(R.string.end_class)) },
            text = { Text(stringResource(R.string.end_class_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.endClass()
                        onBack()
                    },
                ) {
                    Text(
                        stringResource(R.string.end_class),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
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
                title = { Text(stringResource(R.string.live_class)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.isLive) viewModel.requestEndClass() else onBack()
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (uiState.isLive) Color(0xFF153D2A) else MaterialTheme.colorScheme.surface,
                    titleContentColor = if (uiState.isLive) Color.White else MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = if (uiState.isLive) Color.White else MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LiveStatusCard(uiState = uiState)

            LiveSlideStage(
                modifier = Modifier.weight(1f),
                uiState = uiState,
                viewModel = viewModel,
            )

            if (uiState.isLive) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            text = stringResource(R.string.live_drawing_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
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
                }
            }

            LiveControlsCard(
                uiState = uiState,
                onGoLive = viewModel::goLive,
                onEndClass = viewModel::requestEndClass,
                onToggleVoice = {
                    if (uiState.isVoiceStreaming) {
                        viewModel.stopVoiceStreaming()
                    } else if (hasAudioPermission) {
                        viewModel.startVoiceStreaming()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onPreviousSlide = viewModel::previousSlide,
                onNextSlide = viewModel::nextSlide,
            )
        }
    }
}

@Composable
private fun LiveStatusCard(
    uiState: LiveClassUiState,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (uiState.isLive) Color(0xFFE6F4EA) else MaterialTheme.colorScheme.surface,
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
                StatusPill(
                    label = if (uiState.isLive) {
                        stringResource(R.string.live_now)
                    } else {
                        stringResource(R.string.ready_to_go_live)
                    },
                    active = uiState.isLive,
                )
                if (uiState.wsState == WsConnectionState.RECONNECTING) {
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusPill(
                        label = stringResource(R.string.reconnecting),
                        active = false,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (uiState.isVoiceStreaming) {
                    StatusPill(
                        label = stringResource(R.string.voice_on),
                        active = true,
                    )
                } else {
                    StatusPill(
                        label = stringResource(R.string.voice_off),
                        active = false,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LiveInfoBlock(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.current_slide_label),
                    value = "${uiState.currentSlideIndex + 1}/${uiState.slides.size.coerceAtLeast(1)}",
                )
                LiveInfoBlock(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.live_mode_label),
                    value = if (uiState.isRecordingStrokes) {
                        stringResource(R.string.recording_status)
                    } else {
                        stringResource(R.string.editing_status)
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    active: Boolean,
) {
    Card(
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) Color(0xFFDCEFD8) else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (active) {
                Icon(
                    Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    tint = Color(0xFFB3261E),
                    modifier = Modifier.size(12.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) Color(0xFF153D2A) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LiveInfoBlock(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LiveSlideStage(
    modifier: Modifier = Modifier,
    uiState: LiveClassUiState,
    viewModel: LiveClassViewModel,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
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
                text = if (uiState.slides.isEmpty()) {
                    stringResource(R.string.no_slides_draw)
                } else {
                    stringResource(R.string.live_stage_hint)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
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
                            contentDescription = stringResource(
                                R.string.page_x_of_y,
                                uiState.currentSlideIndex + 1,
                                uiState.slides.size,
                            ),
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

                    if (uiState.isLive) {
                        DrawingCanvas(
                            modifier = Modifier.fillMaxSize(),
                            drawingState = viewModel.drawingState,
                            recordingStartTime = if (uiState.isRecordingStrokes) {
                                viewModel.strokeRecorder.recordingStartTime
                            } else {
                                0L
                            },
                            onStrokeStarted = viewModel::onStrokeStarted,
                            onStrokePointAdded = viewModel::onStrokePointAdded,
                            onStrokeCompleted = viewModel::onStrokeCompleted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveControlsCard(
    uiState: LiveClassUiState,
    onGoLive: () -> Unit,
    onEndClass: () -> Unit,
    onToggleVoice: () -> Unit,
    onPreviousSlide: () -> Unit,
    onNextSlide: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!uiState.isLive) {
                Text(
                    text = stringResource(R.string.live_teaching_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onGoLive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.go_live))
                }
            } else {
                Text(
                    text = stringResource(R.string.live_controls_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalButton(
                        onClick = onPreviousSlide,
                        enabled = uiState.currentSlideIndex > 0,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.previous_slide))
                    }
                    FilledTonalButton(
                        onClick = onNextSlide,
                        enabled = uiState.currentSlideIndex < uiState.slides.size - 1,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.next_slide))
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalButton(
                        onClick = onToggleVoice,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (uiState.isVoiceStreaming) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                        ),
                    ) {
                        Icon(
                            if (uiState.isVoiceStreaming) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (uiState.isVoiceStreaming) {
                                stringResource(R.string.turn_voice_off)
                            } else {
                                stringResource(R.string.turn_voice_on)
                            },
                        )
                    }
                    FilledTonalButton(
                        onClick = onEndClass,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.end_class))
                    }
                }
            }
        }
    }
}
