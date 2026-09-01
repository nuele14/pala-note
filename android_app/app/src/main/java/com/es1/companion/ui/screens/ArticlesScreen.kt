package com.es1.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.es1.companion.data.local.ArticleEntity
import com.es1.companion.data.local.RssFeedEntity
import com.es1.companion.domain.rss.FeedValidationResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticlesScreen(
    articles: List<ArticleEntity>,
    feeds: List<RssFeedEntity>,
    isRefreshing: Boolean,
    onRefreshFeeds: () -> Unit,
    onPushArticle: (ArticleEntity) -> Unit,
    onPushAllArticles: () -> Unit,
    onAddFeed: (String, String, String) -> Unit,
    onEditFeed: (RssFeedEntity, String, String, String) -> Unit,
    onDeleteFeed: (RssFeedEntity) -> Unit,
    onTestFeedUrl: suspend (String) -> FeedValidationResult,
    onToggleRead: (ArticleEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedArticle by remember { mutableStateOf<ArticleEntity?>(null) }
    var showFeedsDialog by remember { mutableStateOf(false) }
    var editingFeed by remember { mutableStateOf<RssFeedEntity?>(null) }
    var showAddFeedDialog by remember { mutableStateOf(false) }

    val pendingPushCount = articles.count { !it.isPushedToDevice }

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
                    text = "${articles.size} articoli • $pendingPushCount pronti per ED1",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

                if (pendingPushCount > 0) {
                    Button(
                        onClick = onPushAllArticles,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Invia a ED1", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (articles.isEmpty()) {
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
                        text = "Nessun articolo scaricato",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Premi aggiorna per scaricare gli articoli da Hacker News, Antirez e altri feed RSS.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRefreshFeeds, shape = RoundedCornerShape(10.dp)) {
                        Text("Scarica Articoli RSS", fontSize = 13.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(articles, key = { it.id }) { article ->
                    ArticleCard(
                        article = article,
                        onClick = { selectedArticle = article },
                        onPush = { onPushArticle(article) }
                    )
                }
            }
        }
    }

    // Article Detail & Reader Sheet
    selectedArticle?.let { article ->
        ArticleDetailBottomSheet(
            article = article,
            onPush = { onPushArticle(article) },
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

@Composable
fun ArticleCard(
    article: ArticleEntity,
    onClick: () -> Unit,
    onPush: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = article.feedTitle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                if (article.isPushedToDevice) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Inviato",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Su ED1",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onPush,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Invia", fontSize = 11.sp)
                    }
                }
            }

            Text(
                text = article.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (article.rawSummary.isNotBlank()) {
                Text(
                    text = article.rawSummary,
                    fontSize = 13.sp,
                    color = Color.Gray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailBottomSheet(
    article: ArticleEntity,
    onPush: () -> Unit,
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
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = article.feedTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (article.isPushedToDevice) {
                    Text(text = "✅ Inviato su ED1", fontSize = 12.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text = article.title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            if (!article.author.isNullOrBlank() || !article.pubDate.isNullOrBlank()) {
                Text(
                    text = listOfNotNull(article.author, article.pubDate).joinToString(" • "),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            HorizontalDivider()

            // Formatted Markdown reading area
            Text(
                text = article.markdownContent.ifBlank { article.rawSummary },
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onPush,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (article.isPushedToDevice) "Reinvia a ED1" else "Invia ad ED1 (E-Paper)")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
