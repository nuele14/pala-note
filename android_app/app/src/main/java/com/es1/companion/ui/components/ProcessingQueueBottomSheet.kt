package com.es1.companion.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.es1.companion.domain.queue.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingQueueBottomSheet(
    currentJob: ProcessingJob?,
    pendingJobs: List<ProcessingJob>,
    phaseSummary: String?,
    liveProgress: LiveJobProgress?,
    history: List<ProcessingJobHistory>,
    onCancelJob: (String) -> Unit,
    onCancelAll: () -> Unit,
    onClearHistory: () -> Unit,
    onRetryJob: (ProcessingJobHistory) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var inspectingHistoryItem by remember { mutableStateOf<ProcessingJobHistory?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Intestazione con Tab Bar
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        val count = (if (currentJob != null) 1 else 0) + pendingJobs.filter { it.id != currentJob?.id }.size
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("In Corso & Coda", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                            if (count > 0) {
                                Badge { Text("$count") }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Storico Recenti", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                            if (history.isNotEmpty()) {
                                Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text("${history.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                )
            }

            // CONTENUTO TAB 0: CODA ATTIVA
            if (selectedTab == 0) {
                QueueTabContent(
                    currentJob = currentJob,
                    pendingJobs = pendingJobs,
                    phaseSummary = phaseSummary,
                    liveProgress = liveProgress,
                    onCancelJob = onCancelJob,
                    onCancelAll = onCancelAll
                )
            } else {
                // CONTENUTO TAB 1: STORICO
                HistoryTabContent(
                    history = history,
                    onClearHistory = onClearHistory,
                    onItemClick = { inspectingHistoryItem = it }
                )
            }
        }
    }

    // Dialog Dettagli Storico
    inspectingHistoryItem?.let { item ->
        JobDetailDialog(
            historyItem = item,
            onRetry = {
                onRetryJob(item)
                inspectingHistoryItem = null
            },
            onDismiss = { inspectingHistoryItem = null }
        )
    }
}

@Composable
private fun QueueTabContent(
    currentJob: ProcessingJob?,
    pendingJobs: List<ProcessingJob>,
    phaseSummary: String?,
    liveProgress: LiveJobProgress?,
    onCancelJob: (String) -> Unit,
    onCancelAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Tasto Cancella Tutti
        if (currentJob != null || pendingJobs.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onCancelAll,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(imageVector = Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancella Tutti i Job", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Descrizione fase corrente
        if (phaseSummary != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = phaseSummary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Sezione JOB IN CORSO
        if (currentJob != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "IN ESECUZIONE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = currentJob.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val badgeText = if (currentJob.type == JobType.TRANSCRIPTION) "🎙️ Whisper STT" else "🧠 LiteRT-LM (Qwen)"
                                    Text(
                                        text = "$badgeText • Tag: ${currentJob.noteTag}",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onCancelJob(currentJob.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Interrompi",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Metriche in tempo reale
                        if (liveProgress != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    if (currentJob.type == JobType.SYNTHESIS && liveProgress.tokensGenerated > 0) {
                                        Text(
                                            text = "⚡ ${liveProgress.tokensGenerated} token generati • ${String.format(Locale.US, "%.1f", liveProgress.tokensPerSec)} tok/s • ${String.format(Locale.US, "%.1f", liveProgress.elapsedSec)}s",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else if (currentJob.type == JobType.TRANSCRIPTION && liveProgress.audioDurationSec > 0f) {
                                        Text(
                                            text = "⏱️ Audio ${String.format(Locale.US, "%.1f", liveProgress.audioDurationSec)}s • ${String.format(Locale.US, "%.1f", liveProgress.elapsedSec)}s trascorsi",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (liveProgress.snippet.isNotBlank()) {
                                        Text(
                                            text = "“${liveProgress.snippet}...”",
                                            fontSize = 10.sp,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            color = Color.Gray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sezione IN CODA
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val pendingFiltered = pendingJobs.filter { it.id != currentJob?.id }
            Text(
                text = "IN CODA (${pendingFiltered.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )

            if (pendingFiltered.isEmpty() && currentJob == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nessuna elaborazione in corso o in coda.",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            } else if (pendingFiltered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nessun altro job in coda.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pendingFiltered, key = { it.id }) { job ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val icon = if (job.type == JobType.TRANSCRIPTION) {
                                        Icons.Rounded.RecordVoiceOver
                                    } else {
                                        Icons.Rounded.Psychology
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = job.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val badgeText = if (job.type == JobType.TRANSCRIPTION) "In attesa trascrizione" else "In attesa sintesi AI"
                                        Text(
                                            text = "$badgeText • Tag: ${job.noteTag}",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onCancelJob(job.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Rimuovi",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTabContent(
    history: List<ProcessingJobHistory>,
    onClearHistory: () -> Unit,
    onItemClick: (ProcessingJobHistory) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ULTIMI EVENTI (${history.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )

            if (history.isNotEmpty()) {
                TextButton(
                    onClick = onClearHistory,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Svuota Storico", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nessuna elaborazione recente registrata.",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        } else {
            val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(item) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when (item.status) {
                                JobStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                                else -> MaterialTheme.colorScheme.surface
                            }
                        ),
                        border = if (item.status == JobStatus.FAILED) BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                val statusIcon = when (item.status) {
                                    JobStatus.COMPLETED -> Icons.Rounded.CheckCircle
                                    JobStatus.FAILED -> Icons.Rounded.Error
                                    JobStatus.CANCELLED -> Icons.Rounded.Cancel
                                    else -> Icons.Rounded.Info
                                }
                                val iconTint = when (item.status) {
                                    JobStatus.COMPLETED -> Color(0xFF2E7D32)
                                    JobStatus.FAILED -> MaterialTheme.colorScheme.error
                                    JobStatus.CANCELLED -> Color.Gray
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                Icon(
                                    imageVector = statusIcon,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(22.dp)
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    val opName = if (item.type == JobType.TRANSCRIPTION) "Trascrizione" else "Sintesi AI"
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "$opName nota #${item.noteNum}",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "(${item.noteTag})",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }

                                    val timeStr = timeFormat.format(Date(item.timestamp))
                                    val durationSec = String.format(Locale.US, "%.1f", item.durationMs / 1000f)
                                    val metricDetail = when {
                                        item.tokensGenerated != null && item.tokensGenerated > 0 -> "${item.tokensGenerated} tok (${String.format(Locale.US, "%.1f", item.tokensPerSec ?: 0f)} tok/s)"
                                        item.audioDurationSec != null && item.audioDurationSec > 0f -> "Audio ${String.format(Locale.US, "%.1f", item.audioDurationSec)}s"
                                        else -> null
                                    }
                                    val fullDetail = listOfNotNull(item.engineName, "${durationSec}s", metricDetail, timeStr).joinToString(" • ")

                                    Text(
                                        text = fullDetail,
                                        fontSize = 10.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "Dettagli",
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JobDetailDialog(
    historyItem: ProcessingJobHistory,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val icon = when (historyItem.status) {
                    JobStatus.COMPLETED -> Icons.Rounded.CheckCircle
                    JobStatus.FAILED -> Icons.Rounded.Error
                    JobStatus.CANCELLED -> Icons.Rounded.Cancel
                    else -> Icons.Rounded.Info
                }
                val iconTint = when (historyItem.status) {
                    JobStatus.COMPLETED -> Color(0xFF2E7D32)
                    JobStatus.FAILED -> MaterialTheme.colorScheme.error
                    else -> Color.Gray
                }
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
                Text(
                    text = "Dettaglio Job #${historyItem.noteNum}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Info generali
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val opType = if (historyItem.type == JobType.TRANSCRIPTION) "🎙️ Trascrizione Vocale" else "🧠 Sintesi Neurale"
                        Text(text = "Operazione: $opType", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(text = "Modello: ${historyItem.engineName}", fontSize = 12.sp)
                        Text(text = "Tag: ${historyItem.noteTag}", fontSize = 12.sp)
                        Text(text = "Data/Ora: ${timeFormat.format(Date(historyItem.timestamp))}", fontSize = 12.sp)
                        Text(text = "Tempo calcolo: ${String.format(Locale.US, "%.2f", historyItem.durationMs / 1000f)} secondi", fontSize = 12.sp)

                        if (historyItem.tokensGenerated != null && historyItem.tokensGenerated > 0) {
                            Text(
                                text = "Token prodotti: ${historyItem.tokensGenerated} (${String.format(Locale.US, "%.1f", historyItem.tokensPerSec ?: 0f)} tok/s)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (historyItem.audioDurationSec != null && historyItem.audioDurationSec > 0f) {
                            val rtf = if (historyItem.durationMs > 0) (historyItem.durationMs / 1000f) / historyItem.audioDurationSec else 0f
                            Text(
                                text = "Durata audio: ${String.format(Locale.US, "%.1f", historyItem.audioDurationSec)}s (RTF: ${String.format(Locale.US, "%.2f", rtf)}x)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // In caso di errore: visualizza il box con l'errore
                if (historyItem.errorDetails != null) {
                    Text(
                        text = "DETTAGLI ERRORE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = historyItem.errorDetails,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // In caso di preview risultato
                if (!historyItem.previewResult.isNullOrBlank()) {
                    Text(
                        text = "ANTEPRIMA TESTO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "“${historyItem.previewResult}...”",
                            fontSize = 12.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onRetry) {
                Icon(imageVector = Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Riprova Nota")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Chiudi")
            }
        }
    )
}
