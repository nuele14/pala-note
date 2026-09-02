package com.es1.companion.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.es1.companion.ui.theme.TechFontFamily
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import com.es1.companion.ui.components.NoteDetailBottomSheet
import com.es1.companion.ui.components.SyncModalDialog
import com.es1.companion.ui.components.ProcessingBanner
import com.es1.companion.ui.components.ProcessingQueueBottomSheet
import com.es1.companion.ui.screens.TagRulesScreen
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.rounded.Article
import com.es1.companion.ui.screens.ArticlesScreen
import com.es1.companion.ui.screens.FeedScreen
import com.es1.companion.ui.screens.SearchScreen
import com.es1.companion.ui.screens.SettingsScreen
import com.es1.companion.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    viewModel: NotesViewModel = viewModel()
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val notes by viewModel.notes.collectAsState()
    val articles by viewModel.articles.collectAsState()
    val feeds by viewModel.feeds.collectAsState()
    val queuedArticles by viewModel.queuedArticles.collectAsState()
    val deviceSyncs by viewModel.deviceSyncs.collectAsState()
    val isRefreshingFeeds by viewModel.isRefreshingFeeds.collectAsState()

    val selectedTag by viewModel.selectedTag.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val tagRules by viewModel.tagRules.collectAsState()

    val syncState by viewModel.syncState.collectAsState()
    val showSyncDialog by viewModel.showSyncDialog.collectAsState()

    val selectedNote by viewModel.selectedNote.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val modelDownloadState by viewModel.modelDownloadState.collectAsState()
    val supportedLlmModels = viewModel.supportedLlmModels
    val activeLlmModelId by viewModel.activeLlmModelId.collectAsState()
    val downloadedLlmModelIds by viewModel.downloadedLlmModelIds.collectAsState()
    val llmDownloadState by viewModel.llmDownloadState.collectAsState()

    val currentProcessingJob by viewModel.currentProcessingJob.collectAsState()
    val processingQueue by viewModel.processingQueue.collectAsState()
    val processingPhaseSummary by viewModel.processingPhaseSummary.collectAsState()
    val liveProgress by viewModel.liveProgress.collectAsState()
    val processingHistory by viewModel.processingHistory.collectAsState()
    val showQueueSheet by viewModel.showQueueSheet.collectAsState()

    var showTagRulesScreen by remember { mutableStateOf(false) }

    if (showTagRulesScreen) {
        TagRulesScreen(
            tagRules = tagRules,
            onSaveTagRule = { tag, prompt -> viewModel.saveTagRule(tag, prompt) },
            onResetDefaults = { viewModel.resetDefaultTagRules() },
            onBackClick = { showTagRulesScreen = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = ">_ ES1 // COMPANION",
                        fontFamily = TechFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = "ES1 Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.selectTag(selectedTag) }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column {
                ProcessingBanner(
                    currentJob = currentProcessingJob,
                    totalInQueue = processingQueue.size,
                    phaseSummary = processingPhaseSummary,
                    liveProgress = liveProgress,
                    onClick = { viewModel.openQueueSheet() },
                    onCancelCurrent = { currentProcessingJob?.id?.let { viewModel.cancelProcessingJob(it) } }
                )

                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                NavigationBarItem(
                    selected = (selectedTab == 0),
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Rounded.Notes, contentDescription = "Note") },
                    label = { Text("NOTE", fontFamily = TechFontFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = (selectedTab == 1),
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Rounded.Article, contentDescription = "Articoli") },
                    label = { Text("FEED", fontFamily = TechFontFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = (selectedTab == 2),
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Rounded.Search, contentDescription = "Cerca") },
                    label = { Text("CERCA", fontFamily = TechFontFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = (selectedTab == 3),
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = "Impostazioni") },
                    label = { Text("CONFIG", fontFamily = TechFontFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { viewModel.openSyncDialog() },
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(0.dp)),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = "Sync",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> FeedScreen(
                    notes = notes,
                    selectedTag = selectedTag,
                    onTagSelected = { viewModel.selectTag(it) },
                    onNoteClick = { viewModel.openNoteDetail(it) }
                )
                1 -> ArticlesScreen(
                    articles = articles,
                    feeds = feeds,
                    queuedArticles = queuedArticles,
                    deviceSyncs = deviceSyncs,
                    isRefreshing = isRefreshingFeeds,
                    onRefreshFeeds = { viewModel.refreshRssFeeds() },
                    onQueueArticles = { ids, replace -> viewModel.queueArticlesForSync(ids, replace) },
                    onRemoveFromQueue = { viewModel.removeArticleFromQueue(it) },
                    onClearQueue = { viewModel.clearSyncQueue() },
                    onToggleArticleQueue = { viewModel.toggleArticleQueue(it) },
                    onAddFeed = { title, url, category -> viewModel.addRssFeed(title, url, category) },
                    onEditFeed = { feed, title, url, category -> viewModel.updateRssFeed(feed, title, url, category) },
                    onDeleteFeed = { viewModel.deleteRssFeed(it) },
                    onTestFeedUrl = { viewModel.testFeedUrl(it) },
                    onToggleRead = { viewModel.toggleArticleRead(it) }
                )
                2 -> SearchScreen(
                    query = searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                    results = searchResults,
                    onNoteClick = { viewModel.openNoteDetail(it) }
                )
                3 -> SettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChange = { viewModel.setThemeMode(it) },
                    tagRulesCount = tagRules.size,
                    onOpenTagRules = { showTagRulesScreen = true },
                    supportedLlmModels = supportedLlmModels,
                    activeLlmModelId = activeLlmModelId,
                    downloadedLlmModelIds = downloadedLlmModelIds,
                    llmDownloadState = llmDownloadState,
                    onSetActiveLlmModel = { viewModel.setActiveLlmModel(it) },
                    onDownloadLlmModel = { viewModel.downloadLlmModel(it) },
                    onDeleteLlmModel = { viewModel.deleteLlmModel(it) },
                    onCleanDeviceMemory = { viewModel.cleanDeviceMemory() },
                    modelDownloadState = modelDownloadState,
                    onDownloadModel = { viewModel.downloadWhisperModel() }
                )
            }
        }

        // Note Detail Bottom Sheet
        selectedNote?.let { note ->
            NoteDetailBottomSheet(
                note = note,
                isPlaying = isPlaying,
                onToggleAudio = { viewModel.toggleAudio(note) },
                onExportMarkdown = { viewModel.exportMarkdown(note) },
                onReElaborate = { viewModel.reElaborateNote(note) },
                onDeleteNote = { viewModel.deleteNote(note) },
                onDismiss = { viewModel.closeNoteDetail() }
            )
        }

        // Sync Modal Dialog
        if (showSyncDialog) {
            SyncModalDialog(
                syncState = syncState,
                onDismiss = { viewModel.closeSyncDialog() }
            )
        }

        // Processing Queue Modal BottomSheet
        if (showQueueSheet) {
            ProcessingQueueBottomSheet(
                currentJob = currentProcessingJob,
                pendingJobs = processingQueue,
                phaseSummary = processingPhaseSummary,
                liveProgress = liveProgress,
                history = processingHistory,
                onCancelJob = { viewModel.cancelProcessingJob(it) },
                onCancelAll = { viewModel.cancelAllProcessingJobs() },
                onClearHistory = { viewModel.clearProcessingHistory() },
                onRetryJob = { viewModel.retryJobFromHistory(it) },
                onDismiss = { viewModel.closeQueueSheet() }
            )
        }
    }
}
