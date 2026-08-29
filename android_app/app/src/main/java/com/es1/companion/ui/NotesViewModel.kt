package com.es1.companion.ui

import android.app.Application
import android.media.MediaPlayer
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.es1.companion.data.local.AppDatabase
import com.es1.companion.data.local.NoteEntity
import com.es1.companion.data.local.TagRuleEntity
import com.es1.companion.data.remote.ES1SyncManager
import com.es1.companion.data.remote.SyncResult
import com.es1.companion.data.remote.SyncState
import com.es1.companion.domain.exporter.MarkdownExporter
import com.es1.companion.domain.llm.LLMEngine
import com.es1.companion.domain.stt.STTEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "NotesViewModel"
    private val db = AppDatabase.getDatabase(application, viewModelScope)
    private val noteDao = db.noteDao()
    private val syncManager = ES1SyncManager(application)

    // Domain engines
    private val sttEngine = STTEngine(application, noteDao)
    private val llmEngine = LLMEngine(application, noteDao)
    private val exporter = MarkdownExporter(application, noteDao)

    // Tag filter & search states
    private val _selectedTag = MutableStateFlow("All")
    val selectedTag: StateFlow<String> = _selectedTag.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Notes list according to filter
    val notes: StateFlow<List<NoteEntity>> = _selectedTag.flatMapLatest { tag ->
        if (tag == "All") {
            noteDao.getAllNotes()
        } else {
            noteDao.getNotesByTag(tag)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search results flow
    val searchResults: StateFlow<List<NoteEntity>> = _searchQuery.flatMapLatest { q ->
        if (q.isBlank()) {
            MutableStateFlow(emptyList())
        } else {
            noteDao.searchNotes(q.trim())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Tag Rules
    val tagRules: StateFlow<List<TagRuleEntity>> = noteDao.getAllTagRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Sync State
    val syncState: StateFlow<SyncState> = syncManager.syncState
    private val _showSyncDialog = MutableStateFlow(false)
    val showSyncDialog: StateFlow<Boolean> = _showSyncDialog.asStateFlow()

    // Detail BottomSheet
    private val _selectedNote = MutableStateFlow<NoteEntity?>(null)
    val selectedNote: StateFlow<NoteEntity?> = _selectedNote.asStateFlow()

    // Audio Player State
    private var mediaPlayer: MediaPlayer? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _playingNoteId = MutableStateFlow<String?>(null)
    val playingNoteId: StateFlow<String?> = _playingNoteId.asStateFlow()

    fun selectTag(tag: String) {
        _selectedTag.value = tag
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun openNoteDetail(note: NoteEntity) {
        stopAudio()
        _selectedNote.value = note
    }

    fun closeNoteDetail() {
        stopAudio()
        _selectedNote.value = null
    }

    fun openSyncDialog() {
        _showSyncDialog.value = true
        startSync()
    }

    fun closeSyncDialog() {
        _showSyncDialog.value = false
    }

    fun startSync() {
        viewModelScope.launch {
            val syncResult = syncManager.performSync()
            if (syncResult.success && syncResult.downloadedCount > 0) {
                // Pipeline automatica: STT -> LLM -> Export
                withContext(Dispatchers.IO) {
                    Log.d(TAG, "Running post-sync pipeline...")
                    sttEngine.processAllPending()
                    llmEngine.processAllPending()
                }
            }
        }
    }

    fun saveTagRule(tag: String, prompt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val rule = noteDao.getTagRule(tag)
            if (rule != null) {
                noteDao.updateTagRule(rule.copy(systemPrompt = prompt.trim()))
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Prompt salvato per tag '$tag'", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun reElaborateNote(note: NoteEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                llmEngine.elaborateNote(note.id)
            }
            // Update selected note
            _selectedNote.value = noteDao.getNoteByDeviceNum(note.deviceNoteNum, note.deviceId)
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Nota rielaborata con successo!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun exportMarkdown(note: NoteEntity) {
        viewModelScope.launch {
            val path = exporter.exportNoteEntity(note)
            withContext(Dispatchers.Main) {
                if (path != null) {
                    Toast.makeText(getApplication(), "Esportato in: $path", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(getApplication(), "Errore durante l'esportazione", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            // Elimina file audio locale se esiste
            val audioFile = File(note.audioLocalPath)
            if (audioFile.exists()) audioFile.delete()

            noteDao.deleteNote(note)
            withContext(Dispatchers.Main) {
                if (_selectedNote.value?.id == note.id) {
                    closeNoteDetail()
                }
                Toast.makeText(getApplication(), "Nota eliminata", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun cleanDeviceMemory() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val api = syncManager.getApiService()
                    val response = api.cleanSyncedNotes()
                    response.isSuccessful
                } catch (e: Exception) {
                    false
                }
            }
            withContext(Dispatchers.Main) {
                if (result) {
                    Toast.makeText(getApplication(), "Memoria ES1 pulita con successo!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(getApplication(), "Connettiti alla rete ES1 per pulire la memoria", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Audio Playback
    fun toggleAudio(note: NoteEntity) {
        if (_isPlaying.value && _playingNoteId.value == note.id) {
            pauseAudio()
            return
        }

        stopAudio()
        val file = File(note.audioLocalPath)
        if (!file.exists()) {
            Log.w(TAG, "Audio file not found at ${note.audioLocalPath}")
            Toast.makeText(getApplication(), "File audio non trovato", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    _isPlaying.value = false
                    _playingNoteId.value = null
                }
                start()
            }
            _isPlaying.value = true
            _playingNoteId.value = note.id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play audio", e)
            _isPlaying.value = false
            _playingNoteId.value = null
        }
    }

    fun pauseAudio() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
            }
        }
    }

    fun stopAudio() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (_: Exception) {}
        }
        mediaPlayer = null
        _isPlaying.value = false
        _playingNoteId.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
    }
}
