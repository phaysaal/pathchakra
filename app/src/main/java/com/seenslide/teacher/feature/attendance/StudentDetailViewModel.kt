package com.seenslide.teacher.feature.attendance

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenslide.teacher.core.database.AttendanceDao
import com.seenslide.teacher.core.database.AttendanceEntity
import com.seenslide.teacher.core.database.SessionDao
import com.seenslide.teacher.core.database.StudentDao
import com.seenslide.teacher.core.database.StudentEntity
import com.seenslide.teacher.core.report.ParentReportGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class StudentDetailUiState(
    val student: StudentEntity? = null,
    val records: List<AttendanceEntity> = emptyList(),
    val className: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class StudentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val studentDao: StudentDao,
    private val attendanceDao: AttendanceDao,
    private val sessionDao: SessionDao,
) : ViewModel() {

    private val studentId: String = savedStateHandle["studentId"] ?: ""

    private val _uiState = MutableStateFlow(StudentDetailUiState())
    val uiState: StateFlow<StudentDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val student = studentDao.getById(studentId)
            val records = attendanceDao.getByStudent(studentId)
            val className = student?.let {
                sessionDao.getById(it.sessionId)?.presenterName ?: it.sessionId
            } ?: ""
            _uiState.update {
                it.copy(student = student, records = records, className = className, isLoading = false)
            }
        }
    }

    fun shareReport(context: Context) {
        val student = _uiState.value.student ?: return
        val records = _uiState.value.records
        val className = _uiState.value.className
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    ParentReportGenerator.generate(context, className, student, records)
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
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
