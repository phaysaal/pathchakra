package com.seenslide.teacher.feature.attendance

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenslide.teacher.core.data.SyncManager
import com.seenslide.teacher.core.database.StudentDao
import com.seenslide.teacher.core.database.StudentEntity
import org.json.JSONObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class StudentRosterUiState(
    val students: List<StudentEntity> = emptyList(),
    val showAddDialog: Boolean = false,
    val showBulkDialog: Boolean = false,
    val showEditDialog: StudentEntity? = null,
    val showDeleteConfirm: StudentEntity? = null,
    val dialogName: String = "",
    val dialogRoll: String = "",
    val bulkText: String = "",
    val bulkAddedCount: Int? = null,
    val deletedStudent: StudentEntity? = null,
    val error: String? = null,
)

@HiltViewModel
class StudentRosterViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val studentDao: StudentDao,
    private val syncManager: SyncManager,
) : ViewModel() {

    val sessionId: String = savedStateHandle["sessionId"] ?: ""

    private val _uiState = MutableStateFlow(StudentRosterUiState())
    val uiState: StateFlow<StudentRosterUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            studentDao.observeBySession(sessionId).collect { students ->
                _uiState.update { it.copy(students = students) }
            }
        }
    }

    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true, dialogName = "", dialogRoll = "") }
    }

    fun dismissAddDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun showEditDialog(student: StudentEntity) {
        _uiState.update {
            it.copy(
                showEditDialog = student,
                dialogName = student.name,
                dialogRoll = if (student.rollNumber > 0) student.rollNumber.toString() else "",
            )
        }
    }

    fun dismissEditDialog() {
        _uiState.update { it.copy(showEditDialog = null) }
    }

    fun showDeleteConfirm(student: StudentEntity) {
        _uiState.update { it.copy(showDeleteConfirm = student) }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = null) }
    }

    fun onDialogNameChanged(name: String) {
        _uiState.update { it.copy(dialogName = name) }
    }

    fun onDialogRollChanged(roll: String) {
        _uiState.update { it.copy(dialogRoll = roll.filter { c -> c.isDigit() }) }
    }

    fun addStudent() {
        val name = _uiState.value.dialogName.trim()
        if (name.isBlank()) return
        val roll = _uiState.value.dialogRoll.toIntOrNull() ?: 0
        _uiState.update { it.copy(showAddDialog = false) }
        viewModelScope.launch {
            try {
                val student = StudentEntity(
                    studentId = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    name = name,
                    rollNumber = roll,
                )
                studentDao.upsert(student)
                syncManager.logChange("student", "create", student.studentId, sessionId, student.toJson())
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun updateStudent() {
        val student = _uiState.value.showEditDialog ?: return
        val name = _uiState.value.dialogName.trim()
        if (name.isBlank()) return
        val roll = _uiState.value.dialogRoll.toIntOrNull() ?: 0
        _uiState.update { it.copy(showEditDialog = null) }
        viewModelScope.launch {
            try {
                studentDao.update(student.studentId, name, roll)
                val payload = JSONObject().apply {
                    put("name", name)
                    put("rollNumber", roll)
                }.toString()
                syncManager.logChange("student", "update", student.studentId, sessionId, payload)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteStudent() {
        val student = _uiState.value.showDeleteConfirm ?: return
        _uiState.update { it.copy(showDeleteConfirm = null, deletedStudent = student) }
        viewModelScope.launch {
            try {
                studentDao.delete(student.studentId)
                syncManager.logChange("student", "delete", student.studentId, sessionId, "")
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun undoDelete() {
        val student = _uiState.value.deletedStudent ?: return
        _uiState.update { it.copy(deletedStudent = null) }
        viewModelScope.launch {
            try {
                studentDao.upsert(student)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun dismissDeletedSnackbar() {
        _uiState.update { it.copy(deletedStudent = null) }
    }

    fun showBulkDialog() {
        _uiState.update { it.copy(showBulkDialog = true, bulkText = "") }
    }

    fun dismissBulkDialog() {
        _uiState.update { it.copy(showBulkDialog = false) }
    }

    fun onBulkTextChanged(text: String) {
        _uiState.update { it.copy(bulkText = text) }
    }

    fun bulkAddStudents() {
        val text = _uiState.value.bulkText
        val names = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (names.isEmpty()) return
        val existingCount = _uiState.value.students.size
        _uiState.update { it.copy(showBulkDialog = false) }
        viewModelScope.launch {
            try {
                val entities = names.mapIndexed { index, name ->
                    StudentEntity(
                        studentId = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        name = name,
                        rollNumber = existingCount + index + 1,
                    )
                }
                entities.forEach { student ->
                    studentDao.upsert(student)
                    syncManager.logChange("student", "create", student.studentId, sessionId, student.toJson())
                }
                _uiState.update { it.copy(bulkAddedCount = names.size) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun dismissBulkAdded() {
        _uiState.update { it.copy(bulkAddedCount = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}

private fun StudentEntity.toJson(): String = JSONObject().apply {
    put("studentId", studentId)
    put("sessionId", sessionId)
    put("name", name)
    put("rollNumber", rollNumber)
}.toString()
