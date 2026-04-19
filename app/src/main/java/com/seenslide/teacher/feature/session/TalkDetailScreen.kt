package com.seenslide.teacher.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil.compose.AsyncImage
import com.seenslide.teacher.R
import com.seenslide.teacher.core.network.model.SlideInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalkDetailScreen(
    viewModel: TalkDetailViewModel,
    onTakePhoto: (sessionId: String, talkId: String) -> Unit,
    onOpenCanvas: (sessionId: String, talkId: String) -> Unit,
    onOpenPdf: (sessionId: String, talkId: String) -> Unit,
    onGoLive: (sessionId: String, talkId: String) -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddSlideSheet by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.loadTalkAndSlides()
        onPauseOrDispose {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(uiState.talkTitle.ifEmpty { stringResource(R.string.slides) })
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More",
                                tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rename_lesson)) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.showRenameTalkDialog()
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_lesson)) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.showDeleteTalkConfirm()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error)
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSlideSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_slide)) },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Go Live button
            Button(
                onClick = { onGoLive(viewModel.sessionId, viewModel.talkId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1B5E20),
                ),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.go_live),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            // Slides section
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.slides.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.no_slides_yet),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.no_slides_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    Text(
                        text = stringResource(R.string.x_slides, uiState.slides.size),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.slides, key = { it.slideNumber }) { slide ->
                            SlideThumbCard(
                                slide = slide,
                                imageUrl = viewModel.slideImageUrl(slide.slideNumber),
                                onClick = { viewModel.viewSlide(slide) },
                                onDelete = { viewModel.showDeleteSlideConfirm(slide) },
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Dialogs ---

    // Slide viewer (full screen)
    if (uiState.viewingSlide != null) {
        Dialog(
            onDismissRequest = { viewModel.dismissSlideViewer() },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { viewModel.dismissSlideViewer() },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = viewModel.slideFullImageUrl(uiState.viewingSlide!!.slideNumber),
                    contentDescription = "Slide ${uiState.viewingSlide!!.slideNumber}",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                )
                // Close button
                IconButton(
                    onClick = { viewModel.dismissSlideViewer() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
                // Slide number
                Text(
                    text = "${uiState.viewingSlide!!.slideNumber} / ${uiState.slides.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                )
            }
        }
    }

    // Delete slide confirm
    if (uiState.showDeleteSlideConfirm != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteSlideConfirm() },
            title = { Text(stringResource(R.string.delete_slide)) },
            text = {
                Text(stringResource(R.string.delete_slide_confirm, uiState.showDeleteSlideConfirm!!.slideNumber))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSlide() }) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteSlideConfirm() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Rename talk dialog
    if (uiState.showRenameTalk) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRenameTalkDialog() },
            title = { Text(stringResource(R.string.rename_lesson)) },
            text = {
                OutlinedTextField(
                    value = uiState.renameTalkText,
                    onValueChange = { viewModel.onRenameTalkTextChanged(it) },
                    label = { Text(stringResource(R.string.lesson_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.submitRenameTalk() },
                    enabled = uiState.renameTalkText.isNotBlank(),
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRenameTalkDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Delete talk confirm
    if (uiState.showDeleteTalkConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteTalkConfirm() },
            title = { Text(stringResource(R.string.delete_lesson)) },
            text = { Text(stringResource(R.string.delete_lesson_confirm, uiState.talkTitle)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteTalk(onDeleted = onBack) },
                ) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteTalkConfirm() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Add slide bottom sheet
    if (showAddSlideSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSlideSheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.add_slide),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(16.dp))

                AddSlideOption(
                    icon = Icons.Default.AddAPhoto,
                    label = stringResource(R.string.take_photo),
                    subtitle = stringResource(R.string.take_photo_subtitle),
                    onClick = {
                        showAddSlideSheet = false
                        onTakePhoto(viewModel.sessionId, viewModel.talkId)
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))

                AddSlideOption(
                    icon = Icons.Default.Draw,
                    label = stringResource(R.string.blank_canvas),
                    subtitle = stringResource(R.string.blank_canvas_subtitle),
                    onClick = {
                        showAddSlideSheet = false
                        onOpenCanvas(viewModel.sessionId, viewModel.talkId)
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))

                AddSlideOption(
                    icon = Icons.Default.PictureAsPdf,
                    label = stringResource(R.string.from_pdf),
                    subtitle = stringResource(R.string.from_pdf_subtitle),
                    onClick = {
                        showAddSlideSheet = false
                        onOpenPdf(viewModel.sessionId, viewModel.talkId)
                    },
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun AddSlideOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SlideThumbCard(
    slide: SlideInfo,
    imageUrl: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Slide ${slide.slideNumber}",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f),
                contentScale = ContentScale.Crop,
            )
            // Slide number badge
            Text(
                text = "${slide.slideNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.extraSmall,
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            // Overflow menu
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Slide options",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_slide)) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null,
                                tint = MaterialTheme.colorScheme.error)
                        },
                    )
                }
            }
        }
    }
}
