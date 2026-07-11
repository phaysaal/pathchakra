package com.seenslide.teacher.feature.attendance

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenslide.teacher.core.data.SyncManager
import com.seenslide.teacher.core.database.AttendanceDao
import com.seenslide.teacher.core.database.AttendanceEntity
import com.seenslide.teacher.core.database.StudentDao
import com.seenslide.teacher.core.database.StudentEntity
import org.json.JSONArray
import org.json.JSONObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

data class AttendanceUiState(
    val students: List<StudentEntity> = emptyList(),
    val presentSet: Set<String> = emptySet(), // studentIds marked present
    val date: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val studentDao: StudentDao,
    private val attendanceDao: AttendanceDao,
    private val syncManager: SyncManager,
) : ViewModel() {

    val sessionId: String = savedStateHandle["sessionId"] ?: ""

    private val _uiState = MutableStateFlow(AttendanceUiState())
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                val students = studentDao.getBySession(sessionId)
                val date = _uiState.value.date
                val existing = attendanceDao.getBySessionAndDate(sessionId, date)
                val presentSet = existing.filter { it.present }.map { it.studentId }.toSet()
                _uiState.update { it.copy(students = students, presentSet = presentSet) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun togglePresent(studentId: String) {
        _uiState.update { state ->
            val newSet = if (studentId in state.presentSet) {
                state.presentSet - studentId
            } else {
                state.presentSet + studentId
            }
            state.copy(presentSet = newSet)
        }
    }

    fun markAllPresent() {
        _uiState.update { state ->
            state.copy(presentSet = state.students.map { it.studentId }.toSet())
        }
    }

    fun markAllAbsent() {
        _uiState.update { it.copy(presentSet = emptySet()) }
    }

    fun changeDate(date: String) {
        _uiState.update { it.copy(date = date) }
        loadData()
    }

    fun saveAttendance() {
        val state = _uiState.value
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val records = state.students.map { student ->
                    AttendanceEntity(
                        attendanceId = "${student.studentId}_${state.date}",
                        studentId = student.studentId,
                        sessionId = sessionId,
                        date = state.date,
                        present = student.studentId in state.presentSet,
                    )
                }
                attendanceDao.upsertAll(records)
                val payload = JSONArray(records.map { r ->
                    JSONObject().apply {
                        put("studentId", r.studentId)
                        put("date", r.date)
                        put("present", r.present)
                    }
                }).toString()
                syncManager.logChange("attendance", "upsert", "${sessionId}_${state.date}", sessionId, payload)
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun dismissSaved() {
        _uiState.update { it.copy(saved = false) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
