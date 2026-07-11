package com.seenslide.teacher.feature.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenslide.teacher.core.database.NoteDao
import com.seenslide.teacher.core.database.NoteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class NotesUiState(
    val notes: List<NoteEntity> = emptyList(),
    val showEditor: Boolean = false,
    val editingNote: NoteEntity? = null,
    val editorText: String = "",
    val showDeleteConfirm: NoteEntity? = null,
    val deletedNote: NoteEntity? = null,
    val error: String? = null,
)

@HiltViewModel
class NotesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val noteDao: NoteDao,
) : ViewModel() {

    val sessionId: String = savedStateHandle["sessionId"] ?: ""

    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            noteDao.observeBySession(sessionId).collect { notes ->
                _uiState.update { it.copy(notes = notes) }
            }
        }
    }

    fun showAddEditor() {
        _uiState.update { it.copy(showEditor = true, editingNote = null, editorText = "") }
    }

    fun showEditEditor(note: NoteEntity) {
        _uiState.update { it.copy(showEditor = true, editingNote = note, editorText = note.content) }
    }

    fun dismissEditor() {
        _uiState.update { it.copy(showEditor = false, editingNote = null) }
    }

    fun onEditorTextChanged(text: String) {
        _uiState.update { it.copy(editorText = text) }
    }

    fun saveNote() {
        val text = _uiState.value.editorText.trim()
        if (text.isBlank()) return
        val existing = _uiState.value.editingNote
        _uiState.update { it.copy(showEditor = false, editingNote = null) }
        viewModelScope.launch {
            try {
                if (existing != null) {
                    noteDao.upsert(existing.copy(content = text, updatedAt = System.currentTimeMillis()))
                } else {
                    noteDao.upsert(
                        NoteEntity(
                            noteId = UUID.randomUUID().toString(),
                            sessionId = sessionId,
                            content = text,
                        ),
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun showDeleteConfirm(note: NoteEntity) {
        _uiState.update { it.copy(showDeleteConfirm = note) }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = null) }
    }

    fun deleteNote() {
        val note = _uiState.value.showDeleteConfirm ?: return
        _uiState.update { it.copy(showDeleteConfirm = null, deletedNote = note) }
        viewModelScope.launch {
            try {
                noteDao.delete(note.noteId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun undoDelete() {
        val note = _uiState.value.deletedNote ?: return
        _uiState.update { it.copy(deletedNote = null) }
        viewModelScope.launch {
            try {
                noteDao.upsert(note)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun dismissDeletedSnackbar() {
        _uiState.update { it.copy(deletedNote = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
