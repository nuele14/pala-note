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

import android.content.Context
import com.es1.companion.data.local.ArticleDeviceSyncEntity
import com.es1.companion.data.local.ArticleEntity
import com.es1.companion.data.local.RssFeedEntity
import com.es1.companion.domain.rss.RssManager
import com.es1.companion.ui.theme.ThemeMode

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "NotesViewModel"
    private val db = AppDatabase.getDatabase(application, viewModelScope)
    private val noteDao = db.noteDao()
    private val rssDao = db.rssDao()
    private val syncManager = ES1SyncManager(application)

    // Domain engines
    private val sttEngine = STTEngine(application, noteDao)
    private val llmEngine = LLMEngine(application, noteDao)
    private val exporter = MarkdownExporter(application, noteDao)
    val rssManager = RssManager(application, rssDao)

    // RSS Feeds & Articles Flows
    val articles: StateFlow<List<ArticleEntity>> = rssDao.getAllArticles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val feeds: StateFlow<List<RssFeedEntity>> = rssDao.getAllFeeds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queuedArticles: StateFlow<List<ArticleEntity>> = rssDao.getQueuedArticlesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deviceSyncs: StateFlow<List<ArticleDeviceSyncEntity>> = rssDao.getAllDeviceSyncsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshingFeeds = MutableStateFlow(false)
    val isRefreshingFeeds: StateFlow<Boolean> = _isRefreshingFeeds.asStateFlow()

    // Theme Mode with persistence
    private val prefs = application.getSharedPreferences("es1_settings", Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(
        try {
            ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

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

    // Whisper Model State
    val modelDownloadState = sttEngine.modelManager.downloadState

    fun downloadWhisperModel() {
        viewModelScope.launch {
            val ok = sttEngine.modelManager.downloadModel()
            withContext(Dispatchers.Main) {
                if (ok) {
                    Toast.makeText(getApplication(), "Modello Whisper On-Device pronto!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(getApplication(), "Errore nel download del modello Whisper", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Gemma LLM Model State
    val gemmaDownloadState = llmEngine.modelManager.downloadState

    fun downloadGemmaModel() {
        viewModelScope.launch {
            val ok = llmEngine.modelManager.downloadModel()
            withContext(Dispatchers.Main) {
                if (ok) {
                    Toast.makeText(getApplication(), "Modello Gemma 2B On-Device pronto!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(getApplication(), "Errore nel download del modello Gemma", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun startSync(targetBleMac: String? = null) {
        viewModelScope.launch {
            val syncResult = syncManager.performSync(targetBleMac = targetBleMac) { deviceId ->
                rssManager.pushAllQueuedArticles(deviceId = deviceId)
            }
            if (syncResult.success) {
                withContext(Dispatchers.IO) {
                    Log.d(TAG, "Running on-device post-sync pipeline...")
                    // Pipeline note vocali: STT -> LLM
                    sttEngine.processAllPending()
                    llmEngine.processAllPending()
                }
            }
        }
    }

    fun selectBleDeviceAndSync(device: com.es1.companion.data.remote.ble.BleDeviceItem) {
        syncManager.bleManager.saveDevice(device.address, device.name)
        startSync(targetBleMac = device.address)
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
            try {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Avvio rielaborazione nota #${note.deviceNoteNum}...", Toast.LENGTH_SHORT).show()
                }

                val isModelReady = sttEngine.modelManager.isModelReady()
                if (!isModelReady) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "Modello Whisper non scaricato. Scaricalo dalla tab Impostazioni (connesso a Internet)!", Toast.LENGTH_LONG).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "Trascrizione Whisper on-device in corso...", Toast.LENGTH_SHORT).show()
                    }
                    withContext(Dispatchers.IO) {
                        sttEngine.transcribeNote(note.id)
                    }
                }

                // Elaborazione intelligente (LLM / Regole Tag)
                withContext(Dispatchers.IO) {
                    llmEngine.elaborateNote(note.id)
                }

                // Ricarica la nota aggiornata nel bottom sheet
                val freshNote = withContext(Dispatchers.IO) {
                    noteDao.getNoteByIdDirect(note.id) ?: noteDao.getNoteByDeviceNum(note.deviceNoteNum, note.deviceId)
                }
                _selectedNote.value = freshNote

                withContext(Dispatchers.Main) {
                    if (freshNote?.transcriptionText != null && freshNote.elaboratedMarkdown != null) {
                        Toast.makeText(getApplication(), "Nota #${note.deviceNoteNum} rielaborata con successo!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(getApplication(), "Rielaborazione completata.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error during reElaborateNote", t)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Errore rielaborazione: ${t.localizedMessage ?: t.message}", Toast.LENGTH_LONG).show()
                }
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

    fun refreshRssFeeds() {
        viewModelScope.launch {
            _isRefreshingFeeds.value = true
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Aggiornamento feed RSS in corso...", Toast.LENGTH_SHORT).show()
            }
            val count = rssManager.fetchAllFeeds()
            _isRefreshingFeeds.value = false
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "$count articoli sincronizzati!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun pushArticleToDevice(article: ArticleEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Invio articolo '${article.title}' a ED1...", Toast.LENGTH_SHORT).show()
            }
            val ok = rssManager.pushArticleToDevice(article)
            withContext(Dispatchers.Main) {
                if (ok) {
                    Toast.makeText(getApplication(), "Articolo inviato a ED1 con successo!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(getApplication(), "Errore nell'invio a ED1 (192.168.4.1)", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun queueArticlesForSync(articleIds: List<String>, replaceExisting: Boolean = false, targetDeviceId: String = "ALL") {
        viewModelScope.launch(Dispatchers.IO) {
            if (articleIds.isEmpty()) return@launch
            if (replaceExisting) {
                rssDao.clearSyncQueue()
            }
            rssDao.queueArticlesForSync(articleIds, targetDeviceId)
            withContext(Dispatchers.Main) {
                val mode = if (replaceExisting) "sostituiti" else "aggiunti"
                Toast.makeText(getApplication(), "${articleIds.size} articoli $mode nella lista per ED1!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun removeArticleFromQueue(articleId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            rssDao.removeFromSyncQueue(articleId)
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Articolo rimosso dalla lista per ED1", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun clearSyncQueue() {
        viewModelScope.launch(Dispatchers.IO) {
            rssDao.clearSyncQueue()
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Lista di sincronizzazione ED1 svuotata", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleArticleQueue(article: ArticleEntity, targetDeviceId: String = "ALL") {
        viewModelScope.launch(Dispatchers.IO) {
            if (article.queuedForSync) {
                rssDao.removeFromSyncQueue(article.id)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Articolo rimosso dalla lista ED1", Toast.LENGTH_SHORT).show()
                }
            } else {
                rssDao.queueArticlesForSync(listOf(article.id), targetDeviceId)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Articolo aggiunto alla lista ED1!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun addRssFeed(title: String, url: String, category: String = "Custom") {
        viewModelScope.launch(Dispatchers.IO) {
            val feed = RssFeedEntity(
                title = title.trim(),
                url = url.trim(),
                category = category.trim()
            )
            rssDao.insertFeed(feed)
            rssManager.fetchFeedArticles(feed)
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Feed '$title' aggiunto!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateRssFeed(feed: RssFeedEntity, newTitle: String, newUrl: String, newCategory: String = "Custom") {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = feed.copy(
                title = newTitle.trim(),
                url = newUrl.trim(),
                category = newCategory.trim()
            )
            rssDao.updateFeed(updated)
            rssManager.fetchFeedArticles(updated)
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Feed '${updated.title}' aggiornato!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    suspend fun testFeedUrl(url: String): com.es1.companion.domain.rss.FeedValidationResult {
        return rssManager.validateFeedUrl(url)
    }

    fun deleteRssFeed(feed: RssFeedEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            rssDao.deleteFeed(feed)
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Feed rimosso", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleArticleRead(article: ArticleEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            rssDao.markArticleRead(article.id, !article.isRead)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
    }
}
