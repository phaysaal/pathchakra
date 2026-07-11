package com.seenslide.teacher.feature.attendance

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenslide.teacher.core.database.AttendanceDao
import com.seenslide.teacher.core.database.AttendanceSummary
import com.seenslide.teacher.core.database.StudentDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class AttendanceHistoryUiState(
    val dates: List<String> = emptyList(),
    val summaries: List<AttendanceSummary> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class AttendanceHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val studentDao: StudentDao,
    private val attendanceDao: AttendanceDao,
) : ViewModel() {

    val sessionId: String = savedStateHandle["sessionId"] ?: ""

    private val _uiState = MutableStateFlow(AttendanceHistoryUiState())
    val uiState: StateFlow<AttendanceHistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            try {
                val dates = attendanceDao.getDatesBySession(sessionId)
                val summaries = attendanceDao.getAttendanceSummary(sessionId)
                _uiState.update {
                    it.copy(dates = dates, summaries = summaries, isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun exportCsv(context: Context) {
        viewModelScope.launch {
            try {
                val students = studentDao.getBySession(sessionId)
                val dates = attendanceDao.getDatesBySession(sessionId).sorted()
                val allRecords = attendanceDao.getBySession(sessionId)
                val lookup = allRecords.associateBy { "${it.studentId}_${it.date}" }

                val csv = withContext(Dispatchers.IO) {
                    buildString {
                        append("Roll,Name")
                        dates.forEach { append(",$it") }
                        append(",Present,Total,%\n")
                        students.forEach { student ->
                            append("${student.rollNumber},${student.name}")
                            var present = 0
                            var total = 0
                            dates.forEach { date ->
                                val record = lookup["${student.studentId}_$date"]
                                if (record != null) {
                                    total++
                                    if (record.present) {
                                        present++
                                        append(",P")
                                    } else {
                                        append(",A")
                                    }
                                } else {
                                    append(",")
                                }
                            }
                            val pct = if (total > 0) (present * 100) / total else 0
                            append(",$present,$total,$pct%\n")
                        }
                    }
                }

                val file = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "exports")
                    dir.mkdirs()
                    val f = File(dir, "attendance_$sessionId.csv")
                    f.writeText(csv)
                    f
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, null))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
