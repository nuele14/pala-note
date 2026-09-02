package com.es1.companion.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.es1.companion.data.remote.SyncState

@Composable
fun SyncModalDialog(
    syncState: SyncState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Sincronizzazione ES1",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (syncState) {
                    is SyncState.Idle -> {
                        Text("Pronto alla sincronizzazione...")
                    }
                    is SyncState.Connecting -> {
                        Text(
                            text = syncState.message,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is SyncState.Downloading -> {
                        Text(
                            text = "Scaricamento nota #${syncState.noteNum} (${syncState.current}/${syncState.total})...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        LinearProgressIndicator(
                            progress = { syncState.current.toFloat() / syncState.total.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    is SyncState.Processing -> {
                        Text(
                            text = syncState.message,
                            fontSize = 14.sp
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is SyncState.Success -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF66BB6A),
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = buildString {
                                    if (syncState.downloadedCount > 0) {
                                        append("✓ Sincronizzate ${syncState.downloadedCount} note!\n")
                                    }
                                    if (syncState.uploadedArticlesCount > 0) {
                                        append("✓ ${syncState.uploadedArticlesCount} articoli inviati al Reader!")
                                    }
                                    if (isEmpty()) {
                                        append("✓ Note e Reader già aggiornati.")
                                    }
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    is SyncState.Error -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = syncState.message,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (syncState is SyncState.Success || syncState is SyncState.Error) {
                TextButton(onClick = onDismiss) {
                    Text("Chiudi", fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}
