package com.es1.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeviceUnknown
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.es1.companion.data.remote.SyncProtocol
import com.es1.companion.data.remote.SyncState
import com.es1.companion.data.remote.ble.BleDeviceItem

@Composable
fun SyncModalDialog(
    syncState: SyncState,
    onSelectBleDevice: ((BleDeviceItem) -> Unit)? = null,
    onForceResync: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val isBle = when (syncState) {
        is SyncState.Connecting -> syncState.protocol == SyncProtocol.BLE
        is SyncState.Progress -> syncState.protocol == SyncProtocol.BLE
        is SyncState.Downloading -> syncState.protocol == SyncProtocol.BLE
        is SyncState.Processing -> syncState.protocol == SyncProtocol.BLE
        is SyncState.Success -> syncState.protocol == SyncProtocol.BLE
        is SyncState.DeviceSelection -> true
        else -> false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isBle) Icons.Rounded.Bluetooth else Icons.Rounded.Wifi,
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

                // Protocol Badge (BLE or Wi-Fi)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(
                        text = if (isBle) "BLE" else "WI-FI",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (syncState) {
                    is SyncState.Idle -> {
                        Text("Pronto alla sincronizzazione...")
                    }

                    is SyncState.DeviceSelection -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Dispositivi ES1 Rilevati:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Tocca il tuo dispositivo per associarlo:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(syncState.devices) { device ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                            .clickable { onSelectBleDevice?.invoke(device) }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = device.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = device.address,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "${device.rssi} dBm",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    is SyncState.Connecting -> {
                        Text(
                            text = syncState.message,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    is SyncState.Progress -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = syncState.itemLabel,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${syncState.progressPct}%",
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            LinearProgressIndicator(
                                progress = { syncState.progressPct / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )

                            if (syncState.totalBytes > 0) {
                                val curKb = syncState.bytesTransferred / 1024
                                val totKb = syncState.totalBytes / 1024
                                Text(
                                    text = "$curKb KB / $totKb KB  (${syncState.currentItem}/${syncState.totalItems})",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    is SyncState.Downloading -> {
                        Text(
                            text = "Scaricamento nota #${syncState.noteNum} (${syncState.current}/${syncState.total})...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        LinearProgressIndicator(
                            progress = { syncState.current.toFloat() / syncState.total.toFloat() },
                            modifier = Modifier.fillMaxWidth()
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
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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
                                            append("✓ ${syncState.uploadedArticlesCount} articoli inviati al Reader!\n")
                                        }
                                        if (isEmpty()) {
                                            append("✓ Tutte le note risultano già scaricate.")
                                        }
                                    }.trimEnd(),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (syncState.downloadedCount == 0 && onForceResync != null) {
                                TextButton(
                                    onClick = onForceResync,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Riscarica tutte le note da zero", fontSize = 12.sp)
                                }
                            }
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
        dismissButton = {
            if (onForceResync != null && (syncState is SyncState.Success || syncState is SyncState.Error || syncState is SyncState.Idle)) {
                TextButton(onClick = onForceResync) {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Forza Sync Globale", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            if (syncState is SyncState.Success || syncState is SyncState.Error || syncState is SyncState.DeviceSelection) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = if (syncState is SyncState.DeviceSelection) "Annulla" else "Chiudi",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    )
}
