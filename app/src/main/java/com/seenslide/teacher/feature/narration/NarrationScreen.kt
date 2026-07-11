package com.seenslide.teacher.feature.narration

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage

/**
 * Mode 2 — per-slide narration recording. Flip through the deck, record
 * (or re-record) a voice track for each slide, review locally, then
 * publish the stitched lecture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NarrationScreen(
    viewModel: NarrationViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Keep the screen awake while recording
    val view = LocalView.current
    DisposableEffect(uiState.isRecording) {
        view.keepScreenOn = uiState.isRecording
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
        if (granted) viewModel.startRecording()
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.dismissError()
        }
    }

    // Publish result dialogs
    when (val ps = uiState.publishState) {
        is PublishState.Success -> AlertDialog(
            onDismissRequest = { viewModel.dismissPublishResult() },
            icon = { Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF30A46C)) },
            title = { Text("Published!") },
            text = { Text("Your narrated lecture is live. Students can now play the slides with your voice.") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissPublishResult() }) { Text("OK") }
            },
        )
        is PublishState.Failed -> AlertDialog(
            onDismissRequest = { viewModel.dismissPublishResult() },
            title = { Text("Publish failed") },
            text = { Text(ps.message) },
            confirmButton = {
                TextButton(onClick = { viewModel.publish() }) { Text("Retry") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPublishResult() }) { Text("Cancel") }
            },
        )
        else -> {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record Narration") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            val slide = uiState.currentSlide
            val narratedSec = slide?.let { uiState.narrated[it.slideNumber] }

            // Slide image with narrated badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (slide != null) {
                    AsyncImage(
                        model = viewModel.slideImageUrl(slide.slideNumber),
                        contentDescription = "Slide",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                if (narratedSec != null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xCC30A46C))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.CheckCircle, null,
                            tint = Color.White, modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(formatSec(narratedSec), color = Color.White, fontSize = 11.sp)
                    }
                }
                if (uiState.isRecording) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xCCE5484D))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(7.dp).background(Color.White, CircleShape))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "REC " + formatSec(uiState.recordElapsedSec),
                            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Navigation row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { viewModel.previousSlide() },
                    enabled = uiState.currentIndex > 0 && !uiState.isRecording,
                ) { Icon(Icons.Default.ChevronLeft, "Previous slide") }
                Text(
                    text = "Slide ${uiState.currentIndex + 1} / ${uiState.slides.size}",
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.titleSmall,
                )
                IconButton(
                    onClick = { viewModel.nextSlide() },
                    enabled = uiState.currentIndex < uiState.slides.lastIndex && !uiState.isRecording,
                ) { Icon(Icons.Default.ChevronRight, "Next slide") }
            }

            // Thumbnail strip with narrated ticks
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(uiState.slides) { index, s ->
                    Box(
                        modifier = Modifier
                            .size(width = 72.dp, height = 54.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (index == uiState.currentIndex)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                    ) {
                        AsyncImage(
                            model = viewModel.slideImageUrl(s.slideNumber),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(enabled = !uiState.isRecording) {
                                    viewModel.selectSlide(index)
                                },
                            contentScale = ContentScale.Crop,
                        )
                        if (uiState.narrated.containsKey(s.slideNumber)) {
                            Icon(
                                Icons.Default.CheckCircle, null,
                                tint = Color(0xFF30A46C),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(3.dp)
                                    .size(14.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Record / stop
            Button(
                onClick = {
                    if (uiState.isRecording) {
                        viewModel.stopRecording()
                    } else if (hasAudioPermission) {
                        viewModel.startRecording()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isRecording) Color(0xFFE5484D) else MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    if (uiState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        uiState.isRecording -> "Stop Recording"
                        narratedSec != null -> "Re-record This Slide"
                        else -> "Record This Slide"
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            // Play / delete for the current slide's narration
            if (narratedSec != null && !uiState.isRecording) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (uiState.isPlaying) viewModel.stopPlayback() else viewModel.playNarration()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            if (uiState.isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null, modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (uiState.isPlaying) "Stop" else "Play")
                    }
                    OutlinedButton(onClick = { viewModel.deleteNarration() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete narration", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.weight(1f))

            // Publish
            val ps = uiState.publishState
            if (ps is PublishState.InProgress) {
                LinearProgressIndicator(
                    progress = { if (ps.total > 0) ps.done.toFloat() / ps.total else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Publishing… ${ps.done}/${ps.total} slides uploaded",
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(8.dp))
            } else {
                Button(
                    onClick = { viewModel.publish() },
                    enabled = uiState.narratedCount > 0 && !uiState.isRecording,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30A46C)),
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Publish (${uiState.narratedCount}/${uiState.slides.size} narrated, " +
                            formatSec(uiState.totalNarratedSeconds) + ")"
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private fun formatSec(sec: Double): String {
    val s = sec.toInt()
    return "%d:%02d".format(s / 60, s % 60)
}
