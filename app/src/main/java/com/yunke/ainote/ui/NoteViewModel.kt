package com.yunke.ainote.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yunke.ainote.data.AppDatabase
import com.yunke.ainote.data.FolderEntity
import com.yunke.ainote.data.NoteEntity
import com.yunke.ainote.data.NoteRepository
import com.yunke.ainote.data.RevisionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteViewModel(app: Application) : AndroidViewModel(app) {
    private val repository: NoteRepository

    val notes: StateFlow<List<NoteEntity>>
    val trash: StateFlow<List<NoteEntity>>
    val folders: StateFlow<List<FolderEntity>>

    init {
        val db = AppDatabase.getInstance(app)
        repository = NoteRepository(db.noteDao())
        notes = repository.notes.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList(),
        )
        trash = repository.trash.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList(),
        )
        folders = repository.folders.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList(),
        )
    }

    fun save(rawText: String, existing: NoteEntity?) {
        viewModelScope.launch { repository.save(rawText, existing) }
    }

    fun saveTyped(type: String, title: String, body: String, itemsJson: String?, existing: NoteEntity?) {
        viewModelScope.launch { repository.saveTyped(type, title, body, itemsJson, existing) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun restore(id: String) {
        viewModelScope.launch { repository.restore(id) }
    }

    fun permanentDelete(id: String) {
        viewModelScope.launch { repository.permanentDelete(id) }
    }

    fun togglePin(note: NoteEntity) {
        viewModelScope.launch { repository.togglePin(note) }
    }

    fun revisions(noteId: String): Flow<List<RevisionEntity>> =
        repository.revisions(noteId)
}
