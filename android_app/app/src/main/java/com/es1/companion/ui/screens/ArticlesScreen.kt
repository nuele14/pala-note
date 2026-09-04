package com.es1.companion.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DeviceUnknown
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import com.es1.companion.ui.theme.TechFontFamily
import com.es1.companion.ui.theme.BodyFontFamily

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.es1.companion.data.local.ArticleDeviceSyncEntity
import com.es1.companion.data.local.ArticleEntity
import com.es1.companion.data.local.RssFeedEntity
import com.es1.companion.domain.rss.FeedValidationResult
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

enum class ArticleSortOrder(val label: String, val iconLabel: String) {
    NEWEST_FIRST("Data: Più recenti", "⬇️ Più recenti"),
    OLDEST_FIRST("Data: Meno recenti", "⬆️ Meno recenti")
}

enum class ArticleDateFilter(val label: String) {
    ALL("Tutte le date"),
    TODAY("Oggi"),
    LAST_7_DAYS("Ultimi 7 gg"),
    LAST_30_DAYS("Ultimo mese")
}

enum class ArticleStatusFilter(val label: String) {
    ALL("Tutti"),
    QUEUED("In coda ED1"),
    SYNCED("Sincronizzati"),
    NOT_SYNCED("Da preparare")
}

fun parseArticleTimestamp(article: ArticleEntity): Long {
    val pub = article.pubDate
    if (!pub.isNullOrBlank()) {
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (pattern in formats) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US)
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                val d = fmt.parse(pub.trim())
                if (d != null) return d.time
            } catch (_: Exception) {}
        }
    }
    try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val d = sdf.parse(article.createdUtc)
        if (d != null) return d.time
    } catch (_: Exception) {}
    return 0L
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticlesScreen(
    articles: List<ArticleEntity>,
    feeds: List<RssFeedEntity>,
    queuedArticles: List<ArticleEntity>,
    deviceSyncs: List<ArticleDeviceSyncEntity>,
    isRefreshing: Boolean,
    onRefreshFeeds: () -> Unit,
    onQueueArticles: (List<String>, Boolean) -> Unit,
    onRemoveFromQueue: (String) -> Unit,
    onClearQueue: () -> Unit,
    onToggleArticleQueue: (ArticleEntity) -> Unit,
    onAddFeed: (String, String, String) -> Unit,
    onEditFeed: (RssFeedEntity, String, String, String) -> Unit,
    onDeleteFeed: (RssFeedEntity) -> Unit,
    onTestFeedUrl: suspend (String) -> FeedValidationResult,
    onToggleRead: (ArticleEntity) -> Unit,
    onEnsureFullArticle: (suspend (ArticleEntity) -> ArticleEntity)? = null,
    modifier: Modifier = Modifier
) {
    var selectedArticle by remember { mutableStateOf<ArticleEntity?>(null) }
    var showFeedsDialog by remember { mutableStateOf(false) }
    var editingFeed by remember { mutableStateOf<RssFeedEntity?>(null) }
    var showAddFeedDialog by remember { mutableStateOf(false) }
    var showQueuedSheet by remember { mutableStateOf(false) }
    var showConflictDialog by remember { mutableStateOf(false) }

    // Search, Filter & Sort states
    var searchQuery by remember { mutableStateOf("") }
    var selectedFeedId by remember { mutableStateOf<String?>("ALL") }
    var dateFilter by remember { mutableStateOf(ArticleDateFilter.ALL) }
    var statusFilter by remember { mutableStateOf(ArticleStatusFilter.ALL) }
    var sortOrder by remember { mutableStateOf(ArticleSortOrder.NEWEST_FIRST) }

    // Checkbox selections for batch actions
    val selectedArticleIds = remember { mutableStateListOf<String>() }

    // Map of articleId -> list of synced devices
    val syncedDevicesMap = remember(deviceSyncs) {
        deviceSyncs.groupBy { it.articleId }
    }

    // Filter & sort logic
    val now = System.currentTimeMillis()
    val oneDayMs = 24 * 60 * 60 * 1000L
    val sevenDaysMs = 7 * oneDayMs
    val thirtyDaysMs = 30 * oneDayMs

    val filteredArticles = remember(articles, searchQuery, selectedFeedId, dateFilter, statusFilter, sortOrder, syncedDevicesMap) {
        articles
            .filter { art ->
                // Search query filter
                if (searchQuery.isBlank()) true
                else art.title.contains(searchQuery, ignoreCase = true) ||
                        art.rawSummary.contains(searchQuery, ignoreCase = true) ||
                        art.feedTitle.contains(searchQuery, ignoreCase = true)
            }
            .filter { art ->
                // Feed source filter
                if (selectedFeedId == null || selectedFeedId == "ALL") true
                else art.feedId == selectedFeedId
            }
            .filter { art ->
                // Date range filter
                val artTime = parseArticleTimestamp(art)
                when (dateFilter) {
                    ArticleDateFilter.ALL -> true
                    ArticleDateFilter.TODAY -> artTime > 0 && (now - artTime) <= oneDayMs
                    ArticleDateFilter.LAST_7_DAYS -> artTime > 0 && (now - artTime) <= sevenDaysMs
                    ArticleDateFilter.LAST_30_DAYS -> artTime > 0 && (now - artTime) <= thirtyDaysMs
                }
            }
            .filter { art ->
                // Status filter
                val isSynced = (syncedDevicesMap[art.id]?.isNotEmpty() == true)
                when (statusFilter) {
                    ArticleStatusFilter.ALL -> true
                    ArticleStatusFilter.QUEUED -> art.queuedForSync
                    ArticleStatusFilter.SYNCED -> isSynced
                    ArticleStatusFilter.NOT_SYNCED -> !art.queuedForSync && !isSynced
                }
            }
            .sortedWith { a, b ->
                val timeA = parseArticleTimestamp(a)
                val timeB = parseArticleTimestamp(b)
                if (sortOrder == ArticleSortOrder.NEWEST_FIRST) {
                    timeB.compareTo(timeA)
                } else {
                    timeA.compareTo(timeB)
                }
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Top action bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "📰 Articoli RSS",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${filteredArticles.size} di ${articles.size} articoli • ${queuedArticles.size} in coda per ED1",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Button Coda ED1 with Badge
                IconButton(onClick = { showQueuedSheet = true }) {
                    BadgedBox(
                        badge = {
                            if (queuedArticles.isNotEmpty()) {
                                Badge(containerColor = Color(0xFFFF9800), contentColor = Color.White) {
                                    Text("${queuedArticles.size}")
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Inbox,
                            contentDescription = "Coda ED1",
                            tint = if (queuedArticles.isNotEmpty()) Color(0xFFFF9800) else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(onClick = { showFeedsDialog = true }) {
                    Icon(
                        imageVector = Icons.Rounded.RssFeed,
                        contentDescription = "Gestisci Feed",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onRefreshFeeds) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = "Aggiorna Feed",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cerca articoli...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Cancella", modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Row 1: Source Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = (selectedFeedId == "ALL"),
                onClick = { selectedFeedId = "ALL" },
                label = { Text("Tutte le fonti", fontSize = 11.sp) }
            )

            feeds.forEach { feed ->
                FilterChip(
                    selected = (selectedFeedId == feed.id),
                    onClick = { selectedFeedId = feed.id },
                    label = { Text(feed.title, fontSize = 11.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Row 2: Date Sorting, Date Range & Status Filter Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sort Order Toggle Button
            FilterChip(
                selected = true,
                onClick = {
                    sortOrder = if (sortOrder == ArticleSortOrder.NEWEST_FIRST) {
                        ArticleSortOrder.OLDEST_FIRST
                    } else {
                        ArticleSortOrder.NEWEST_FIRST
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Sort,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                },
                label = {
                    Text(
                        text = sortOrder.iconLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )

            Box(modifier = Modifier.width(1.dp).height(18.dp).background(Color.LightGray))

            // Date Range Filters
            ArticleDateFilter.values().forEach { dFilter ->
                FilterChip(
                    selected = (dateFilter == dFilter),
                    onClick = { dateFilter = dFilter },
                    leadingIcon = if (dFilter != ArticleDateFilter.ALL) {
                        { Icon(Icons.Rounded.DateRange, contentDescription = null, modifier = Modifier.size(13.dp)) }
                    } else null,
                    label = { Text(dFilter.label, fontSize = 11.sp) }
                )
            }

            Box(modifier = Modifier.width(1.dp).height(18.dp).background(Color.LightGray))

            // Status Filters
            ArticleStatusFilter.values().forEach { sFilter ->
                FilterChip(
                    selected = (statusFilter == sFilter),
                    onClick = { statusFilter = sFilter },
                    label = { Text(sFilter.label, fontSize = 11.sp) }
                )
            }
        }

        // Selection & Batch Preparation Action Header
        if (filteredArticles.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = selectedArticleIds.size == filteredArticles.size && filteredArticles.isNotEmpty(),
                        onCheckedChange = { checked ->
                            if (checked) {
                                selectedArticleIds.clear()
                                selectedArticleIds.addAll(filteredArticles.map { it.id })
                            } else {
                                selectedArticleIds.clear()
                            }
                        }
                    )
                    Text(
                        text = if (selectedArticleIds.isNotEmpty()) "${selectedArticleIds.size} selezionati" else "Seleziona tutti",
                        fontSize = 12.sp,
                        fontWeight = if (selectedArticleIds.isNotEmpty()) FontWeight.Bold else FontWeight.Normal
                    )
                }

                if (selectedArticleIds.isNotEmpty()) {
                    Button(
                        onClick = {
                            if (queuedArticles.isNotEmpty()) {
                                showConflictDialog = true
                            } else {
                                onQueueArticles(selectedArticleIds.toList(), false)
                                selectedArticleIds.clear()
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.PlaylistAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Prepara per ED1 (${selectedArticleIds.size})", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (filteredArticles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Article,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(54.dp)
                    )
                    Text(
                        text = "Nessun articolo corrispondente",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Prova a modificare i filtri o premi aggiorna per scaricare nuovi articoli.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredArticles, key = { it.id }) { article ->
                    val isChecked = article.id in selectedArticleIds
                    val syncedList = syncedDevicesMap[article.id] ?: emptyList()

                    ArticleCardAsync(
                        article = article,
                        isChecked = isChecked,
                        syncedDevices = syncedList,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (article.id !in selectedArticleIds) selectedArticleIds.add(article.id)
                            } else {
                                selectedArticleIds.remove(article.id)
                            }
                        },
                        onClick = { selectedArticle = article }
                    )
                }
            }
        }
    }

    // Conflict Dialog when adding to an existing non-empty queue
    if (showConflictDialog) {
        AlertDialog(
            onDismissRequest = { showConflictDialog = false },
            title = { Text("Lista Sincronizzazione ED1") },
            text = {
                Text(
                    text = "Ci sono già ${queuedArticles.size} articoli in attesa di sincronizzazione con ED1. Come desideri procedere per i ${selectedArticleIds.size} articoli selezionati?",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onQueueArticles(selectedArticleIds.toList(), false)
                        selectedArticleIds.clear()
                        showConflictDialog = false
                    }
                ) {
                    Text("Aggiungi alla lista")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            onQueueArticles(selectedArticleIds.toList(), true)
                            selectedArticleIds.clear()
                            showConflictDialog = false
                        }
                    ) {
                        Text("Sostituisci lista", color = Color(0xFFE65100))
                    }
                    TextButton(onClick = { showConflictDialog = false }) {
                        Text("Annulla")
                    }
                }
            }
        )
    }

    // Queued Articles Management BottomSheet
    if (showQueuedSheet) {
        QueuedArticlesBottomSheet(
            queuedArticles = queuedArticles,
            onRemove = { onRemoveFromQueue(it.id) },
            onClearAll = { onClearQueue() },
            onDismiss = { showQueuedSheet = false }
        )
    }

    // Article Detail & Reader Sheet with HTML Web Preview
    selectedArticle?.let { article ->
        val syncedList = syncedDevicesMap[article.id] ?: emptyList()
        ArticlePreviewBottomSheet(
            article = article,
            syncedDevices = syncedList,
            onToggleQueue = { onToggleArticleQueue(article) },
            onEnsureFullArticle = onEnsureFullArticle,
            onDismiss = { selectedArticle = null }
        )
    }

    // Feeds Manager Dialog
    if (showFeedsDialog) {
        AlertDialog(
            onDismissRequest = { showFeedsDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sorgenti Feed RSS")
                    IconButton(onClick = { showFeedsDialog = false }) {
                        Icon(imageVector = Icons.Rounded.Close, contentDescription = "Chiudi")
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    feeds.forEach { feed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(text = feed.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(text = feed.category, fontSize = 9.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                                Text(
                                    text = feed.url,
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(onClick = { editingFeed = feed }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Modifica",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = { onDeleteFeed(feed) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Elimina",
                                        tint = Color(0xFFEF5350),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showAddFeedDialog = true }) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nuovo Feed")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedsDialog = false }) {
                    Text("Fatto")
                }
            }
        )
    }

    // Add Feed Dialog
    if (showAddFeedDialog) {
        FeedEditorDialog(
            feed = null,
            onTest = onTestFeedUrl,
            onSave = { title, url, category ->
                onAddFeed(title, url, category)
                showAddFeedDialog = false
            },
            onDismiss = { showAddFeedDialog = false }
        )
    }

    // Edit Feed Dialog
    editingFeed?.let { feed ->
        FeedEditorDialog(
            feed = feed,
            onTest = onTestFeedUrl,
            onSave = { title, url, category ->
                onEditFeed(feed, title, url, category)
                editingFeed = null
            },
            onDismiss = { editingFeed = null }
        )
    }
}

@Composable
fun ArticleCardAsync(
    article: ArticleEntity,
    isChecked: Boolean,
    syncedDevices: List<ArticleDeviceSyncEntity>,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (article.queuedForSync) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.padding(top = 2.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF37474F))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = article.feedTitle,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    // Sync & Queue Badges
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (article.queuedForSync) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFFF3E0))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.HourglassTop,
                                    contentDescription = null,
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "In coda ED1",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE65100)
                                )
                            }
                        }

                        if (syncedDevices.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFE8F5E9))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(12.dp)
                                )
                                val devLabel = syncedDevices.joinToString(", ") { it.deviceName ?: it.deviceId }
                                Text(
                                    text = "Su $devLabel",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = article.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (article.rawSummary.isNotBlank()) {
                    Text(
                        text = article.rawSummary,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!article.pubDate.isNullOrBlank() || !article.author.isNullOrBlank()) {
                    Text(
                        text = listOfNotNull(article.author, article.pubDate).joinToString(" • "),
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueuedArticlesBottomSheet(
    queuedArticles: List<ArticleEntity>,
    onRemove: (ArticleEntity) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "📥 Lista di Lettura ED1",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${queuedArticles.size} articoli pronti per la sincronizzazione Wi-Fi",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                if (queuedArticles.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Icon(imageVector = Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Svuota", color = Color(0xFFEF5350))
                    }
                }
            }

            HorizontalDivider()

            if (queuedArticles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Rounded.Inbox, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                        Text("Nessun articolo in coda per ED1", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Seleziona gli articoli con le checkbox e premi 'Prepara per ED1'.", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(queuedArticles, key = { it.id }) { article ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = article.feedTitle,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = article.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { onRemove(article) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Rimuovi dalla coda",
                                    tint = Color(0xFFEF5350),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "ℹ️ Gli articoli in questa lista verranno trasferiti automaticamente all'ED1 non appena avvierai la sincronizzazione Wi-Fi.",
                fontSize = 11.sp,
                color = Color.Gray,
                lineHeight = 15.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticlePreviewBottomSheet(
    article: ArticleEntity,
    syncedDevices: List<ArticleDeviceSyncEntity>,
    onToggleQueue: () -> Unit,
    onEnsureFullArticle: (suspend (ArticleEntity) -> ArticleEntity)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedPreviewTab by remember { mutableIntStateOf(0) } // 0 = Full HTML Reader, 1 = Markdown E-Paper, 2 = Live Web
    var currentArticle by remember(article.id) { mutableStateOf(article) }
    var isExtractingFull by remember(article.id) { mutableStateOf(false) }

    // Auto-estrazione in background se l'articolo ha solo il sommario/preview
    LaunchedEffect(article.id) {
        if (!currentArticle.isFullContent || currentArticle.fullHtmlContent.isNullOrBlank() || currentArticle.markdownContent.length < 350) {
            isExtractingFull = true
            try {
                if (onEnsureFullArticle != null) {
                    val full = onEnsureFullArticle(currentArticle)
                    currentArticle = full
                }
            } catch (_: Exception) {}
            isExtractingFull = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header con titolo, metadati e azioni rapide
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "[ ${currentArticle.feedTitle.uppercase()} ]",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = TechFontFamily,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val statusLabel = if (currentArticle.isFullContent) "COMPLETO" else "PREVIEW RSS"
                        val statusColor = if (currentArticle.isFullContent) MaterialTheme.colorScheme.primary else Color.Gray
                        Box(
                            modifier = Modifier
                                .border(1.dp, statusColor, RoundedCornerShape(0.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = statusLabel,
                                fontSize = 9.sp,
                                fontFamily = TechFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }

                        if (syncedDevices.isNotEmpty()) {
                            Text(
                                text = "✅ Su ${syncedDevices.joinToString { it.deviceName ?: it.deviceId }}",
                                fontSize = 10.sp,
                                fontFamily = TechFontFamily,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = currentArticle.title,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TechFontFamily,
                        fontSize = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pulsante per forzare la ri-estrazione del testo integrale
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                isExtractingFull = true
                                try {
                                    if (onEnsureFullArticle != null) {
                                        val full = onEnsureFullArticle(currentArticle.copy(isFullContent = false, fullHtmlContent = null))
                                        currentArticle = full
                                    }
                                } catch (_: Exception) {}
                                isExtractingFull = false
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Ricarica articolo completo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Apri browser esterno
                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentArticle.link))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = "Apri nel browser",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Indicatore di estrazione in tempo reale dal web
            if (isExtractingFull) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = "// ESTRAZIONE ARTICOLO INTEGRALE DAL WEB IN CORSO...",
                        fontFamily = TechFontFamily,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Tab Selector a 3 opzioni: HTML Reader Completo, E-Paper Markdown, Web Live
            SecondaryTabRow(
                selectedTabIndex = selectedPreviewTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = (selectedPreviewTab == 0),
                    onClick = { selectedPreviewTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.Article, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("Articolo Completo", fontSize = 11.sp, fontFamily = TechFontFamily, fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = (selectedPreviewTab == 1),
                    onClick = { selectedPreviewTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("ES1 E-Paper", fontSize = 11.sp, fontFamily = TechFontFamily, fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = (selectedPreviewTab == 2),
                    onClick = { selectedPreviewTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("Web Originale", fontSize = 11.sp, fontFamily = TechFontFamily, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            // Contenuto dei Tab
            when (selectedPreviewTab) {
                0 -> {
                    // Tab 0: Reader Mode HTML pulito con il testo completo dell'articolo
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(0.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        val htmlToDisplay = currentArticle.fullHtmlContent
                        if (!htmlToDisplay.isNullOrBlank()) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        settings.javaScriptEnabled = false
                                        settings.domStorageEnabled = false
                                        settings.loadWithOverviewMode = true
                                        settings.useWideViewPort = true
                                        webViewClient = WebViewClient()
                                        loadDataWithBaseURL(currentArticle.link, htmlToDisplay, "text/html", "UTF-8", null)
                                    }
                                },
                                update = { webView ->
                                    webView.loadDataWithBaseURL(currentArticle.link, htmlToDisplay, "text/html", "UTF-8", null)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (isExtractingFull) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(32.dp), color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = "// CARICAMENTO TESTO COMPLETO...",
                                        fontSize = 11.sp,
                                        fontFamily = TechFontFamily,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else {
                            // Fallback con webview live se l'estrazione non ha prodotto html
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.loadWithOverviewMode = true
                                        settings.useWideViewPort = true
                                        webViewClient = WebViewClient()
                                        loadUrl(currentArticle.link)
                                    }
                                },
                                update = { webView ->
                                    if (webView.url != currentArticle.link) {
                                        webView.loadUrl(currentArticle.link)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                1 -> {
                    // Tab 1: Formato Markdown E-Paper (il testo integrale inviato all'ES1)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(0.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "// TESTO INTEGRALE MARKDOWN (TRANSFER ES1)",
                            fontSize = 11.sp,
                            fontFamily = TechFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = currentArticle.markdownContent.ifBlank { currentArticle.rawSummary },
                            fontSize = 13.sp,
                            fontFamily = BodyFontFamily,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                2 -> {
                    // Tab 2: Web live della pagina originale
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(0.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    webViewClient = WebViewClient()
                                    loadUrl(currentArticle.link)
                                }
                            },
                            update = { webView ->
                                if (webView.url != currentArticle.link) {
                                    webView.loadUrl(currentArticle.link)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Bottom Action: Aggiungi/Rimuovi dalla coda per l'ES1
            if (currentArticle.queuedForSync) {
                OutlinedButton(
                    onClick = onToggleQueue,
                    shape = RoundedCornerShape(0.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF5350)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RIMUOVI DALLA CODA ED1", fontFamily = TechFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF5350))
                }
            } else {
                Button(
                    onClick = onToggleQueue,
                    shape = RoundedCornerShape(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Rounded.PlaylistAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PREPARA PER TRASFERIMENTO ED1", fontFamily = TechFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun FeedEditorDialog(
    feed: RssFeedEntity?,
    onTest: suspend (String) -> FeedValidationResult,
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var title by remember { mutableStateOf(feed?.title ?: "") }
    var url by remember { mutableStateOf(feed?.url ?: "") }
    var category by remember { mutableStateOf(feed?.category ?: "Tech") }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<FeedValidationResult?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (feed != null) "Modifica Feed RSS" else "Nuovo Feed RSS") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        testResult = null
                    },
                    label = { Text("URL RSS Feed") },
                    placeholder = { Text("https://news.ycombinator.com/rss") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titolo Canale") },
                    placeholder = { Text("Es. Hacker News") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Categoria") },
                    placeholder = { Text("Tech, News, Blog...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Button Testa Canale
                OutlinedButton(
                    onClick = {
                        if (url.isNotBlank()) {
                            coroutineScope.launch {
                                isTesting = true
                                val res = onTest(url)
                                isTesting = false
                                testResult = res
                                if (res.success && title.isBlank() && !res.detectedTitle.isNullOrBlank()) {
                                    title = res.detectedTitle
                                }
                            }
                        }
                    },
                    enabled = url.isNotBlank() && !isTesting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verifica in corso...")
                    } else {
                        Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Testa Canale RSS")
                    }
                }

                // Test result visual card
                testResult?.let { result ->
                    if (result.success) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Canale agganciato con successo!",
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                Text(
                                    text = "Rilevati ${result.articleCount} articoli recenti da '${result.detectedTitle}'",
                                    fontSize = 11.sp,
                                    color = Color(0xFF1B5E20)
                                )
                            }
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFC62828),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = result.errorMessage ?: "Errore di validazione del feed",
                                    color = Color(0xFFC62828),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (url.isNotBlank()) {
                        val finalTitle = title.ifBlank { testResult?.detectedTitle ?: "Feed RSS" }
                        onSave(finalTitle.trim(), url.trim(), category.trim())
                    }
                },
                enabled = url.isNotBlank()
            ) {
                Text(if (feed != null) "Salva Modifiche" else "Aggiungi")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        }
    )
}
