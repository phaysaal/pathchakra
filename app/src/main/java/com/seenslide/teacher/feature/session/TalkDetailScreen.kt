package com.seenslide.teacher.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import coil.compose.AsyncImage
import com.seenslide.teacher.R
import com.seenslide.teacher.core.network.model.SlideInfo
import com.seenslide.teacher.core.ui.OfflineBanner
import com.seenslide.teacher.core.ui.ShimmerSlideGrid
import com.seenslide.teacher.core.ui.UploadQueueBanner
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalkDetailScreen(
    viewModel: TalkDetailViewModel,
    onTakePhoto: (sessionId: String, talkId: String) -> Unit,
    onOpenCanvas: (sessionId: String, talkId: String) -> Unit,
    onOpenSlideMaker: (sessionId: String, talkId: String) -> Unit,
    onOpenPdf: (sessionId: String, talkId: String) -> Unit,
    onGoLive: (sessionId: String, talkId: String) -> Unit,
    onRecordNarration: (sessionId: String, talkId: String) -> Unit,
    onReplaceWithPhoto: (sessionId: String, talkId: String, slideNumber: Int) -> Unit,
    onReplaceWithCanvas: (sessionId: String, talkId: String, slideNumber: Int) -> Unit,
    onReplaceWithPdf: (sessionId: String, talkId: String, slideNumber: Int) -> Unit,
    onEditSlide: (sessionId: String, talkId: String, slideNumber: Int) -> Unit,
    onBack: () -> Unit,
    onDeleteTalk: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var showOverflowMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val uploadCompleteMsg = stringResource(R.string.upload_complete)
    val scope = rememberCoroutineScope()
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.uploadGalleryImages(uris)
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(uiState.uploadCompletedSlide) {
        if (uiState.uploadCompletedSlide != null) {
            scope.launch { snackbarHostState.showSnackbar(uploadCompleteMsg) }
            viewModel.dismissUploadCompleted()
        }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.loadTalkAndSlides()
        onPauseOrDispose {}
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
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
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
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
    ) { padding ->
        when {
            uiState.isLoading && uiState.slides.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ShimmerSlideGrid()
                    Spacer(modifier = Modifier.height(12.dp))
                    ShimmerSlideGrid()
                }
            }

            uiState.error != null && uiState.slides.isEmpty() -> {
                LessonErrorState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    message = uiState.error ?: stringResource(R.string.error_generic),
                    onRetry = viewModel::loadTalkAndSlides,
                )
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    OfflineBanner(isOffline = uiState.isOffline)
                    UploadQueueBanner(pendingCount = uiState.pendingUploadCount)
                    PullToRefreshBox(
                        isRefreshing = uiState.isLoading,
                        onRefresh = { viewModel.loadTalkAndSlides() },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                    item {
                        LessonWorkspaceCard(
                            title = uiState.talkTitle.ifEmpty { stringResource(R.string.new_lesson) },
                            slideCount = uiState.slides.size,
                        )
                    }

                    item {
                        SectionCard(
                            title = stringResource(R.string.slide_creation_tools),
                            subtitle = stringResource(R.string.slide_creation_tools_hint),
                        ) {
                            ActionTile(
                                icon = Icons.Default.AddAPhoto,
                                title = stringResource(R.string.take_photo),
                                subtitle = stringResource(R.string.camera_slide_subtitle),
                                onClick = { onTakePhoto(viewModel.sessionId, viewModel.talkId) },
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            ActionTile(
                                icon = Icons.Default.Draw,
                                title = stringResource(R.string.blank_canvas),
                                subtitle = stringResource(R.string.blank_whiteboard_slide),
                                onClick = { onOpenCanvas(viewModel.sessionId, viewModel.talkId) },
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            ActionTile(
                                icon = Icons.Default.TextFields,
                                title = stringResource(R.string.slide_maker_title),
                                subtitle = stringResource(R.string.slide_maker_subtitle),
                                onClick = { onOpenSlideMaker(viewModel.sessionId, viewModel.talkId) },
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            ActionTile(
                                icon = Icons.Default.PictureAsPdf,
                                title = stringResource(R.string.from_pdf),
                                subtitle = stringResource(R.string.pdf_slide_subtitle),
                                onClick = { onOpenPdf(viewModel.sessionId, viewModel.talkId) },
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            ActionTile(
                                icon = Icons.Default.PhotoLibrary,
                                title = stringResource(R.string.from_gallery),
                                subtitle = stringResource(R.string.gallery_slide_subtitle),
                                onClick = {
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            )
                        }
                    }

                    item {
                        SectionCard(
                            title = stringResource(R.string.live_teaching),
                            subtitle = stringResource(R.string.live_teaching_hint),
                        ) {
                            Button(
                                onClick = { onGoLive(viewModel.sessionId, viewModel.talkId) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1B5E20),
                                ),
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.open_live_class),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onRecordNarration(viewModel.sessionId, viewModel.talkId) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0D47A1),
                                ),
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.record_narration),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }

                    item {
                        SectionHeader(
                            title = stringResource(R.string.slide_library),
                            subtitle = if (uiState.slides.isEmpty()) {
                                stringResource(R.string.no_slides_hint)
                            } else {
                                stringResource(R.string.tap_slide_to_open)
                            },
                        )
                    }

                    item {
                        if (uiState.slides.isEmpty()) {
                            EmptySlideLibrary(
                                onTakePhoto = { onTakePhoto(viewModel.sessionId, viewModel.talkId) },
                                onOpenCanvas = { onOpenCanvas(viewModel.sessionId, viewModel.talkId) },
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(gridHeightFor(uiState.slides.size)),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                userScrollEnabled = false,
                            ) {
                                items(uiState.slides, key = { "${it.slideNumber}_${uiState.imageCacheBust}" }) { slide ->
                                    SlideThumbCard(
                                        slide = slide,
                                        imageUrl = viewModel.slideImageUrl(slide.slideNumber, uiState.imageCacheBust),
                                        isFirst = slide.slideNumber == uiState.slides.first().slideNumber,
                                        isLast = slide.slideNumber == uiState.slides.last().slideNumber,
                                        onClick = { viewModel.viewSlide(slide) },
                                        onEdit = {
                                            onEditSlide(viewModel.sessionId, viewModel.talkId, slide.slideNumber)
                                        },
                                        onDelete = { viewModel.showDeleteSlideConfirm(slide) },
                                        onReplacePhoto = {
                                            onReplaceWithPhoto(viewModel.sessionId, viewModel.talkId, slide.slideNumber)
                                        },
                                        onReplaceCanvas = {
                                            onReplaceWithCanvas(viewModel.sessionId, viewModel.talkId, slide.slideNumber)
                                        },
                                        onReplacePdf = {
                                            onReplaceWithPdf(viewModel.sessionId, viewModel.talkId, slide.slideNumber)
                                        },
                                        onMoveUp = { viewModel.moveSlideUp(slide) },
                                        onMoveDown = { viewModel.moveSlideDown(slide) },
                                    )
                                }
                            }
                        }
                    }
                }
                    }
                }
            }
        }
    }

    if (uiState.viewingSlide != null) {
        val initialPage = uiState.slides.indexOfFirst {
            it.slideNumber == uiState.viewingSlide!!.slideNumber
        }.coerceAtLeast(0)
        val pagerState = rememberPagerState(initialPage = initialPage) { uiState.slides.size }

        Dialog(
            onDismissRequest = { viewModel.dismissSlideViewer() },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val slide = uiState.slides[page]
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { viewModel.dismissSlideViewer() },
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = viewModel.slideFullImageUrl(slide.slideNumber, uiState.imageCacheBust),
                            contentDescription = "Slide ${slide.slideNumber}",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = {
                            val slide = uiState.slides[pagerState.currentPage]
                            viewModel.dismissSlideViewer()
                            onEditSlide(viewModel.sessionId, viewModel.talkId, slide.slideNumber)
                        },
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.draw_on_photo),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    IconButton(onClick = { viewModel.dismissSlideViewer() }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                Text(
                    text = "${pagerState.currentPage + 1} / ${uiState.slides.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                )
            }
        }
    }

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

    if (uiState.showDeleteTalkConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteTalkConfirm() },
            title = { Text(stringResource(R.string.delete_lesson)) },
            text = { Text(stringResource(R.string.delete_lesson_confirm, uiState.talkTitle)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteTalk(onDeleted = onDeleteTalk) },
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

    val galleryProgress = uiState.galleryUploadProgress
    if (galleryProgress != null) {
        Dialog(onDismissRequest = {}) {
            Card {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        text = if (galleryProgress.total == 1) {
                            stringResource(R.string.uploading_photo)
                        } else {
                            stringResource(
                                R.string.uploading_gallery_progress,
                                galleryProgress.current,
                                galleryProgress.total,
                            )
                        },
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LessonWorkspaceCard(
    title: String,
    slideCount: Int,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.lesson_workspace),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.lesson_workspace_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
            Surface(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = stringResource(R.string.x_slides, slideCount),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = {
                SectionHeader(title = title, subtitle = subtitle)
                content()
            },
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
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
private fun EmptySlideLibrary(
    onTakePhoto: () -> Unit,
    onOpenCanvas: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Default.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(58.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = stringResource(R.string.no_slides_yet),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.no_slides_hint),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.take_photo))
            }
            TextButton(onClick = onOpenCanvas) {
                Text(stringResource(R.string.create_first_slide))
            }
        }
    }
}

@Composable
private fun LessonErrorState(
    modifier: Modifier,
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.lesson_problem_loading),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun SlideThumbCard(
    slide: SlideInfo,
    imageUrl: String,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReplacePhoto: () -> Unit,
    onReplaceCanvas: () -> Unit,
    onReplacePdf: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Slide ${slide.slideNumber}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f),
                    contentScale = ContentScale.Crop,
                )
                Text(
                    text = "${slide.slideNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.55f),
                            shape = MaterialTheme.shapes.extraSmall,
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
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
                            text = { Text(stringResource(R.string.draw_on_photo)) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.replace_with_photo)) },
                            onClick = {
                                showMenu = false
                                onReplacePhoto()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.replace_with_canvas)) },
                            onClick = {
                                showMenu = false
                                onReplaceCanvas()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Draw, contentDescription = null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.replace_with_pdf)) },
                            onClick = {
                                showMenu = false
                                onReplacePdf()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_slide)) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Slide ${slide.slideNumber}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (!isFirst) {
                    IconButton(
                        onClick = onMoveUp,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = stringResource(R.string.move_up),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!isLast) {
                    IconButton(
                        onClick = onMoveDown,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = stringResource(R.string.move_down),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun gridHeightFor(itemCount: Int): androidx.compose.ui.unit.Dp {
    val rows = ((itemCount + 1) / 2).coerceAtLeast(1)
    return (rows * 212).dp
}
