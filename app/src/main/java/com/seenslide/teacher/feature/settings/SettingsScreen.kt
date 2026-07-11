package com.seenslide.teacher.feature.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seenslide.teacher.R
import com.seenslide.teacher.core.locale.LocaleHelper
import com.seenslide.teacher.core.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val restorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.restoreBackup(context, it) }
    }

    val cacheMessage = stringResource(R.string.cache_cleared)
    LaunchedEffect(uiState.cacheCleared) {
        if (uiState.cacheCleared) {
            snackbarHostState.showSnackbar(cacheMessage)
            viewModel.dismissCacheCleared()
        }
    }
    val backupMessage = stringResource(R.string.backup_exported)
    LaunchedEffect(uiState.backupDone) {
        if (uiState.backupDone) {
            snackbarHostState.showSnackbar(backupMessage)
            viewModel.dismissBackupDone()
        }
    }
    val restoreMessage = stringResource(R.string.backup_restored)
    LaunchedEffect(uiState.restoreDone) {
        if (uiState.restoreDone) {
            snackbarHostState.showSnackbar(restoreMessage)
            viewModel.dismissRestoreDone()
        }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Account section
            SectionLabel(stringResource(R.string.account_section))

            if (uiState.phoneNumber.isNotEmpty()) {
                SettingsRow(
                    icon = Icons.Default.Phone,
                    label = stringResource(R.string.phone_number),
                    value = uiState.phoneNumber,
                )
            }
            if (uiState.userName.isNotEmpty()) {
                SettingsRow(
                    icon = Icons.Default.Person,
                    label = stringResource(R.string.your_name_optional),
                    value = uiState.userName,
                )
            }
            SettingsAction(
                icon = Icons.Default.Language,
                label = stringResource(R.string.choose_language),
                onClick = { showLanguageDialog = true },
            )
            SettingsAction(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = stringResource(R.string.logout),
                onClick = { viewModel.showLogoutConfirm() },
                tint = MaterialTheme.colorScheme.error,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Appearance section
            SectionLabel(stringResource(R.string.appearance_section))
            SettingsAction(
                icon = Icons.Default.DarkMode,
                label = stringResource(R.string.dark_mode),
                value = when (uiState.themeMode) {
                    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                    ThemeMode.DARK -> stringResource(R.string.theme_dark)
                },
                onClick = { viewModel.showThemeDialog() },
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Storage section
            SectionLabel(stringResource(R.string.storage_section))
            SettingsAction(
                icon = Icons.Default.DeleteSweep,
                label = stringResource(R.string.clear_cache),
                onClick = { viewModel.clearImageCache() },
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Data section
            SectionLabel(stringResource(R.string.data_section))
            SettingsAction(
                icon = Icons.Default.AutoMode,
                label = stringResource(R.string.auto_backup),
                value = stringResource(R.string.auto_backup_hint),
                onClick = {},
            )
            SettingsAction(
                icon = Icons.Default.Backup,
                label = stringResource(R.string.export_backup),
                onClick = { viewModel.exportBackup(context) },
            )
            SettingsAction(
                icon = Icons.Default.Restore,
                label = stringResource(R.string.import_backup),
                onClick = { restorePicker.launch(arrayOf("application/json")) },
            )

            Spacer(modifier = Modifier.height(8.dp))

            // About section
            SectionLabel(stringResource(R.string.about_section))
            SettingsRow(
                icon = Icons.Default.Info,
                label = stringResource(R.string.app_version),
                value = uiState.appVersion,
            )
        }
    }

    if (uiState.showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLogoutConfirm() },
            title = { Text(stringResource(R.string.logout)) },
            text = { Text(stringResource(R.string.logout_confirm)) },
            confirmButton = {
                TextButton(onClick = { viewModel.logout(onLoggedOut) }) {
                    Text(
                        stringResource(R.string.logout),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissLogoutConfirm() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (uiState.showThemeDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissThemeDialog() },
            title = { Text(stringResource(R.string.dark_mode)) },
            text = {
                Column {
                    ThemeOption(
                        label = stringResource(R.string.theme_system),
                        selected = uiState.themeMode == ThemeMode.SYSTEM,
                        onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ThemeOption(
                        label = stringResource(R.string.theme_light),
                        selected = uiState.themeMode == ThemeMode.LIGHT,
                        onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ThemeOption(
                        label = stringResource(R.string.theme_dark),
                        selected = uiState.themeMode == ThemeMode.DARK,
                        onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissThemeDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showLanguageDialog) {
        val currentLang = LocaleHelper.getLanguage(context)
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.choose_language)) },
            text = {
                Column {
                    LanguageOption(
                        label = stringResource(R.string.language_english),
                        selected = currentLang == "en",
                        onClick = {
                            LocaleHelper.setLanguage(context, "en")
                            showLanguageDialog = false
                            (context as? Activity)?.recreate()
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LanguageOption(
                        label = stringResource(R.string.language_bangla),
                        selected = currentLang == "bn",
                        onClick = {
                            LocaleHelper.setLanguage(context, "bn")
                            showLanguageDialog = false
                            (context as? Activity)?.recreate()
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LanguageOption(
                        label = stringResource(R.string.language_french),
                        selected = currentLang == "fr",
                        onClick = {
                            LocaleHelper.setLanguage(context, "fr")
                            showLanguageDialog = false
                            (context as? Activity)?.recreate()
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Card(
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
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun SettingsAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    value: String? = null,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = tint,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = tint,
                modifier = Modifier.weight(1f),
            )
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun LanguageOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}
