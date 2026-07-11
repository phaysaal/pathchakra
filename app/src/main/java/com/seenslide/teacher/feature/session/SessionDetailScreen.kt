package com.seenslide.teacher.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.seenslide.teacher.R
import com.seenslide.teacher.core.network.model.TalkResponse
import com.seenslide.teacher.core.ui.OfflineBanner
import com.seenslide.teacher.core.ui.QrCodeImage
import com.seenslide.teacher.core.ui.ShimmerTalkCards
import androidx.compose.material3.pulltorefresh.PullToRefreshBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    viewModel: SessionDetailViewModel,
    onTalkClick: (sessionId: String, talkId: String) -> Unit,
    onStudents: (sessionId: String) -> Unit,
    onAttendance: (sessionId: String) -> Unit,
    onAttendanceHistory: (sessionId: String) -> Unit,
    onNotes: (sessionId: String) -> Unit = {},
    onBack: () -> Unit,
    onDeleteSession: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showOverflowMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    val duplicatedMessage = stringResource(R.string.lesson_duplicated)
    LaunchedEffect(uiState.duplicatedTalkTitle) {
        if (uiState.duplicatedTalkTitle != null) {
            snackbarHostState.showSnackbar(duplicatedMessage)
            viewModel.dismissDuplicated()
        }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.loadSession()
        onPauseOrDispose {}
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(uiState.session?.presenterName ?: viewModel.sessionId) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showRenameDialog() }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.rename_class),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more_actions),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_class)) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.showDeleteConfirm()
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
            uiState.isLoading && uiState.session == null && uiState.talks.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ShimmerTalkCards()
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    OfflineBanner(isOffline = uiState.isOffline)
                    PullToRefreshBox(
                        isRefreshing = uiState.isLoading,
                        onRefresh = { viewModel.loadSession() },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                    item {
                        ClassOverviewCard(
                            className = uiState.session?.presenterName ?: viewModel.sessionId,
                            classCode = viewModel.sessionId,
                            lessonCount = uiState.talks.size,
                        )
                    }

                    item {
                        ShareSectionCard(
                            classCode = viewModel.sessionId,
                            onShowQr = { viewModel.showQrCode() },
                            onShare = {
                                val className = uiState.session?.presenterName ?: viewModel.sessionId
                                val shareText = context.getString(R.string.share_class_invite, className, viewModel.sessionId)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_class)))
                            },
                        )
                    }

                    item {
                        AttendanceSectionCard(
                            onStudents = { onStudents(viewModel.sessionId) },
                            onAttendance = { onAttendance(viewModel.sessionId) },
                            onHistory = { onAttendanceHistory(viewModel.sessionId) },
                            onNotes = { onNotes(viewModel.sessionId) },
                        )
                    }

                    item {
                        LessonSectionHeader(
                            lessonCount = uiState.talks.size,
                            onAddLesson = { viewModel.showCreateTalkDialog() },
                        )
                    }

                    if (uiState.talks.isEmpty()) {
                        item {
                            EmptyLessonState(
                                onAddLesson = { viewModel.showCreateTalkDialog() },
                            )
                        }
                    } else {
                        items(uiState.talks, key = { it.talkId }) { talk ->
                            TalkCard(
                                talk = talk,
                                onClick = { onTalkClick(viewModel.sessionId, talk.talkId) },
                                onRename = { viewModel.showRenameTalkDialog(talk) },
                                onDuplicate = { viewModel.duplicateTalk(talk) },
                                onDelete = { viewModel.showDeleteTalkConfirm(talk) },
                            )
                        }
                    }
                }
                    }
                }
            }
        }
    }

    if (uiState.showRename) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRenameDialog() },
            title = { Text(stringResource(R.string.rename_class)) },
            text = {
                OutlinedTextField(
                    value = uiState.renameText,
                    onValueChange = { viewModel.onRenameTextChanged(it) },
                    label = { Text(stringResource(R.string.class_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.submitRename() },
                    enabled = uiState.renameText.isNotBlank(),
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRenameDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirm() },
            title = { Text(stringResource(R.string.delete_class)) },
            text = { Text(stringResource(R.string.delete_class_confirm)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSession(onDeleted = onDeleteSession) }) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirm() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (uiState.showQrCode) {
        Dialog(onDismissRequest = { viewModel.dismissQrCode() }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.class_code),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = viewModel.sessionId,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.share_join_instruction),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    QrCodeImage(
                        content = "https://seenslide.com/${viewModel.sessionId}",
                        size = 200.dp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.scan_to_join),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { viewModel.dismissQrCode() }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }

    if (uiState.showCreateTalk) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCreateTalkDialog() },
            title = { Text(stringResource(R.string.new_lesson)) },
            text = {
                OutlinedTextField(
                    value = uiState.newTalkTitle,
                    onValueChange = { viewModel.onNewTalkTitleChanged(it) },
                    label = { Text(stringResource(R.string.lesson_title)) },
                    placeholder = { Text(stringResource(R.string.lesson_title_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createTalk { talkId ->
                            onTalkClick(viewModel.sessionId, talkId)
                        }
                    },
                    enabled = uiState.newTalkTitle.isNotBlank() && !uiState.isCreatingTalk,
                ) {
                    if (uiState.isCreatingTalk) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text(stringResource(R.string.add_lesson))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCreateTalkDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (uiState.showRenameTalk != null) {
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

    if (uiState.showDeleteTalkConfirm != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteTalkConfirm() },
            title = { Text(stringResource(R.string.delete_lesson)) },
            text = {
                Text(
                    stringResource(
                        R.string.delete_lesson_confirm,
                        uiState.showDeleteTalkConfirm!!.title,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteTalk() }) {
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

    if (uiState.isDuplicating) {
        Dialog(onDismissRequest = {}) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        text = stringResource(R.string.duplicating_lesson),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassOverviewCard(
    className: String,
    classCode: String,
    lessonCount: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.class_overview),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = className,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoPill(
                    label = stringResource(R.string.class_code),
                    value = classCode,
                )
                InfoPill(
                    label = stringResource(R.string.lessons_count),
                    value = lessonCount.toString(),
                )
            }
            Text(
                text = stringResource(R.string.class_overview_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun InfoPill(
    label: String,
    value: String,
) {
    Column(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ShareSectionCard(
    classCode: String,
    onShowQr: () -> Unit,
    onShare: () -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.share_with_students),
        hint = stringResource(R.string.share_join_instruction),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.class_code),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = classCode,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.share_code_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                modifier = Modifier.align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onShowQr) {
                    Icon(Icons.Default.QrCode, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.show_qr_code))
                }
                OutlinedButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.share_class))
                }
            }
        }
    }
}

@Composable
private fun LessonSectionHeader(
    lessonCount: Int,
    onAddLesson: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.lessons_section_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.lessons_section_subtitle, lessonCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onAddLesson) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.add_lesson))
        }
    }
}

@Composable
private fun EmptyLessonState(
    onAddLesson: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.Book,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.no_lessons_yet),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.no_lessons_hint),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAddLesson) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.create_first_lesson))
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    hint: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                content()
            },
        )
    }
}

@Composable
private fun AttendanceSectionCard(
    onStudents: () -> Unit,
    onAttendance: () -> Unit,
    onHistory: () -> Unit,
    onNotes: () -> Unit = {},
) {
    SectionCard(
        title = stringResource(R.string.attendance_section),
        hint = stringResource(R.string.attendance_section_hint),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onStudents,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.students))
            }
            OutlinedButton(
                onClick = onAttendance,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.roll_call))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onHistory,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.attendance_history))
            }
            OutlinedButton(
                onClick = onNotes,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.notes))
            }
        }
    }
}

@Composable
private fun TalkCard(
    talk: TalkResponse,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(12.dp),
            ) {
                Icon(
                    Icons.Default.Book,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = talk.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.lesson_card_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Photo,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.x_slides, talk.slideCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_actions),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename_lesson)) },
                        onClick = {
                            showMenu = false
                            onRename()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.duplicate_lesson)) },
                        onClick = {
                            showMenu = false
                            onDuplicate()
                        },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_lesson)) },
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
    }
}
