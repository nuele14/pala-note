package com.es1.companion.ui

import androidx.compose.foundation.layout.Box
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
import com.es1.companion.ui.components.NoteDetailBottomSheet
import com.es1.companion.ui.components.SyncModalDialog
import com.es1.companion.ui.screens.FeedScreen
import com.es1.companion.ui.screens.SearchScreen
import com.es1.companion.ui.screens.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    viewModel: NotesViewModel = viewModel()
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val notes by viewModel.notes.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val tagRules by viewModel.tagRules.collectAsState()

    val syncState by viewModel.syncState.collectAsState()
    val showSyncDialog by viewModel.showSyncDialog.collectAsState()

    val selectedNote by viewModel.selectedNote.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ES1",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
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
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = (selectedTab == 0),
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Rounded.Notes, contentDescription = "Note") },
                    label = { Text("Note") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = (selectedTab == 1),
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Rounded.Search, contentDescription = "Cerca") },
                    label = { Text("Cerca") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = (selectedTab == 2),
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = "Impostazioni") },
                    label = { Text("Impostazioni") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { viewModel.openSyncDialog() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
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
                1 -> SearchScreen(
                    query = searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                    results = searchResults,
                    onNoteClick = { viewModel.openNoteDetail(it) }
                )
                2 -> SettingsScreen(
                    tagRules = tagRules,
                    onSaveTagRule = { tag, prompt -> viewModel.saveTagRule(tag, prompt) }
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
    }
}
