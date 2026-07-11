package com.seenslide.teacher.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.seenslide.teacher.BuildConfig
import com.seenslide.teacher.core.auth.PinStore
import com.seenslide.teacher.core.ui.theme.ThemeMode
import com.seenslide.teacher.core.ui.theme.ThemeStore
import com.seenslide.teacher.core.database.AttendanceDao
import com.seenslide.teacher.core.database.AttendanceEntity
import com.seenslide.teacher.core.database.OStudiDatabase
import com.seenslide.teacher.core.database.SessionDao
import com.seenslide.teacher.core.database.StudentDao
import com.seenslide.teacher.core.database.StudentEntity
import com.seenslide.teacher.core.network.auth.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val phoneNumber: String = "",
    val userName: String = "",
    val appVersion: String = BuildConfig.VERSION_NAME,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showLogoutConfirm: Boolean = false,
    val showThemeDialog: Boolean = false,
    val cacheCleared: Boolean = false,
    val backupDone: Boolean = false,
    val restoreDone: Boolean = false,
    val autoBackupEnabled: Boolean = false,
    val lastAutoBackup: String? = null,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val tokenStore: TokenStore,
    private val pinStore: PinStore,
    private val database: OStudiDatabase,
    private val imageLoader: ImageLoader,
    private val sessionDao: SessionDao,
    private val studentDao: StudentDao,
    private val attendanceDao: AttendanceDao,
    private val themeStore: ThemeStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        _uiState.value = _uiState.value.copy(
            phoneNumber = pinStore.getStoredPhone() ?: "",
        )
        viewModelScope.launch {
            tokenStore.userName.collect { name ->
                _uiState.value = _uiState.value.copy(userName = name ?: "")
            }
        }
        viewModelScope.launch {
            themeStore.themeMode.collect { mode ->
                _uiState.value = _uiState.value.copy(themeMode = mode)
            }
        }
    }

    fun showThemeDialog() {
        _uiState.value = _uiState.value.copy(showThemeDialog = true)
    }

    fun dismissThemeDialog() {
        _uiState.value = _uiState.value.copy(showThemeDialog = false)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themeStore.setThemeMode(mode)
            _uiState.value = _uiState.value.copy(showThemeDialog = false)
        }
    }

    fun showLogoutConfirm() {
        _uiState.value = _uiState.value.copy(showLogoutConfirm = true)
    }

    fun dismissLogoutConfirm() {
        _uiState.value = _uiState.value.copy(showLogoutConfirm = false)
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            tokenStore.clearAuth()
            pinStore.clear()
            withContext(Dispatchers.IO) {
                database.clearAllTables()
            }
            onLoggedOut()
        }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearImageCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()
            }
            _uiState.value = _uiState.value.copy(cacheCleared = true)
        }
    }

    fun dismissCacheCleared() {
        _uiState.value = _uiState.value.copy(cacheCleared = false)
    }

    fun exportBackup(context: Context) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    val sessions = sessionDao.getAll()
                    val root = JSONObject()

                    val studentsArray = JSONArray()
                    val attendanceArray = JSONArray()

                    for (session in sessions) {
                        val students = studentDao.getBySession(session.sessionId)
                        for (s in students) {
                            studentsArray.put(JSONObject().apply {
                                put("studentId", s.studentId)
                                put("sessionId", s.sessionId)
                                put("name", s.name)
                                put("rollNumber", s.rollNumber)
                            })
                        }
                        val records = attendanceDao.getBySession(session.sessionId)
                        for (a in records) {
                            attendanceArray.put(JSONObject().apply {
                                put("attendanceId", a.attendanceId)
                                put("studentId", a.studentId)
                                put("sessionId", a.sessionId)
                                put("talkId", a.talkId)
                                put("date", a.date)
                                put("present", a.present)
                            })
                        }
                    }

                    root.put("students", studentsArray)
                    root.put("attendance", attendanceArray)
                    root.put("exportedAt", System.currentTimeMillis())

                    val dir = File(context.cacheDir, "backups")
                    dir.mkdirs()
                    val file = File(dir, "pathchakra_backup.json")
                    file.writeText(root.toString(2))
                    file
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    json,
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, null))
                _uiState.value = _uiState.value.copy(backupDone = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun restoreBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw Exception("Cannot read file")
                    val text = inputStream.bufferedReader().readText()
                    inputStream.close()
                    val root = JSONObject(text)

                    val studentsArray = root.getJSONArray("students")
                    for (i in 0 until studentsArray.length()) {
                        val obj = studentsArray.getJSONObject(i)
                        studentDao.upsert(
                            StudentEntity(
                                studentId = obj.getString("studentId"),
                                sessionId = obj.getString("sessionId"),
                                name = obj.getString("name"),
                                rollNumber = obj.optInt("rollNumber", 0),
                            ),
                        )
                    }

                    val attendanceArray = root.getJSONArray("attendance")
                    for (i in 0 until attendanceArray.length()) {
                        val obj = attendanceArray.getJSONObject(i)
                        attendanceDao.upsert(
                            AttendanceEntity(
                                attendanceId = obj.getString("attendanceId"),
                                studentId = obj.getString("studentId"),
                                sessionId = obj.getString("sessionId"),
                                talkId = obj.optString("talkId", ""),
                                date = obj.getString("date"),
                                present = obj.getBoolean("present"),
                            ),
                        )
                    }
                }
                _uiState.value = _uiState.value.copy(restoreDone = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun dismissBackupDone() {
        _uiState.value = _uiState.value.copy(backupDone = false)
    }

    fun dismissRestoreDone() {
        _uiState.value = _uiState.value.copy(restoreDone = false)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
