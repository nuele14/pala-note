package com.es1.companion.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import com.es1.companion.ui.theme.TechFontFamily
import com.es1.companion.ui.theme.BodyFontFamily
import com.es1.companion.domain.llm.LlmModelInfo
import com.es1.companion.domain.stt.ModelDownloadState
import com.es1.companion.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    tagRulesCount: Int,
    onOpenTagRules: () -> Unit,
    supportedLlmModels: List<LlmModelInfo>,
    activeLlmModelId: String,
    downloadedLlmModelIds: Set<String>,
    llmDownloadState: ModelDownloadState,
    onSetActiveLlmModel: (String) -> Unit,
    onDownloadLlmModel: (String) -> Unit,
    onDeleteLlmModel: (String) -> Unit,
    onCleanDeviceMemory: () -> Unit,
    modelDownloadState: ModelDownloadState,
    onDownloadModel: () -> Unit,
    onForceGlobalResync: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = ">> CONFIG // AI & DEVICE",
            fontFamily = TechFontFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        // 1. Theme Selection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "// ASPETTO & TEMA",
                    fontFamily = TechFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val themes = listOf(
                        Triple(ThemeMode.LIGHT, "Chiaro", Icons.Rounded.LightMode),
                        Triple(ThemeMode.DARK, "Scuro", Icons.Rounded.DarkMode),
                        Triple(ThemeMode.SYSTEM, "Auto", Icons.Rounded.BrightnessAuto)
                    )
                    themes.forEach { (mode, label, icon) ->
                        val selected = themeMode == mode
                        OutlinedButton(
                            onClick = { onThemeModeChange(mode) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(0.dp),
                            colors = if (selected) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(label, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 2. Sottomenu: Prompt per Tipo di Nota
        Card(
            onClick = onOpenTagRules,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Prompt per Tipo di Nota",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$tagRulesCount tipi configurabili (Todo, Meeting, Idea, Work...)",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // 3. Modelli LLM On-Device (Elaborazione 100% Locale sul Dispositivo)
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "🧠 Modelli LLM On-Device (Locale)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Text(
                    text = "La sintesi neurale delle note vocali avviene al 100% offline sul processore del tuo telefono. Scegli quale modello scaricare per testarne la bontà e la velocità:",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                // Elenco dei 3 modelli
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    supportedLlmModels.forEach { model ->
                        val isDownloaded = downloadedLlmModelIds.contains(model.id)
                        val isActive = (activeLlmModelId == model.id)
                        val isCurrentlyDownloading = llmDownloadState is ModelDownloadState.Downloading &&
                                (llmDownloadState as ModelDownloadState.Downloading).currentFile.contains(model.name)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(0.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive && isDownloaded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.surface
                            ),
                            border = if (isActive && isDownloaded) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = model.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        if (isActive && isDownloaded) {
                                            Surface(
                                                shape = RoundedCornerShape(0.dp),
                                                color = MaterialTheme.colorScheme.primary
                                            ) {
                                                Text(
                                                    text = "ATTIVO",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(0.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = "~${model.approxSizeMb} MB",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = model.description,
                                    fontSize = 12.sp,
                                    color = if (isActive && isDownloaded) MaterialTheme.colorScheme.onPrimaryContainer else Color.Gray
                                )

                                // Stato / Azioni per questo modello
                                if (isCurrentlyDownloading) {
                                    val state = llmDownloadState as ModelDownloadState.Downloading
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Download: ${state.currentFile}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "${state.progressPct}%",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { state.progressPct / 100f },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                } else if (isDownloaded) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF4CAF50),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Scaricato sul telefono",
                                                fontSize = 11.sp,
                                                color = Color(0xFF4CAF50),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (!isActive) {
                                                OutlinedButton(
                                                    onClick = {
                                                        onSetActiveLlmModel(model.id)
                                                        Toast.makeText(context, "${model.name} impostato come attivo", Toast.LENGTH_SHORT).show()
                                                    },
                                                    shape = RoundedCornerShape(0.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Imposta Attivo", fontSize = 11.sp)
                                                }
                                            }

                                            IconButton(
                                                onClick = {
                                                    onDeleteLlmModel(model.id)
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteOutline,
                                                    contentDescription = "Elimina modello",
                                                    tint = Color.Gray,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // Not downloaded
                                    OutlinedButton(
                                        onClick = { onDownloadLlmModel(model.id) },
                                        shape = RoundedCornerShape(0.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(imageVector = Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Scarica ${model.name} (~${model.approxSizeMb} MB)", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                if (llmDownloadState is ModelDownloadState.Error) {
                    Text(
                        text = "Errore download: ${(llmDownloadState as ModelDownloadState.Error).message}",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 4. Modello Whisper On-Device STT
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "🎙️ Riconoscimento Vocale On-Device (Whisper)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Sherpa-ONNX Whisper Tiny Multilingual • Trascrive le registrazioni vocali in testo locale 100% offline sul dispositivo.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                when (modelDownloadState) {
                    is ModelDownloadState.Ready -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                            Text("Modello Whisper pronto", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4CAF50))
                        }
                    }
                    is ModelDownloadState.Downloading -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Download Whisper (${modelDownloadState.currentFile})...", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                Text("${modelDownloadState.progressPct}%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(progress = { modelDownloadState.progressPct / 100f }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    is ModelDownloadState.NotDownloaded -> {
                        OutlinedButton(
                            onClick = onDownloadModel,
                            shape = RoundedCornerShape(0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scarica Modello Whisper Tiny (~150 MB)", fontSize = 12.sp)
                        }
                    }
                    is ModelDownloadState.Error -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(modelDownloadState.message, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            Button(
                                onClick = onDownloadModel,
                                shape = RoundedCornerShape(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Riprova Download Whisper", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // 5. Clean Device Memory Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🧹 Manutenzione Dispositivo ES1",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Elimina dal dispositivo ES1 tutte le registrazioni WAV e gli articoli già sincronizzati con l app per liberare memoria SD.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onCleanDeviceMemory,
                    shape = RoundedCornerShape(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pulisci Memoria ES1 Sincronizzata", fontSize = 12.sp)
                }
            }
        }

        // 6. Global Resync Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🔄 Risincronizzazione Globale ES1",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Forza il download di tutte le note presenti sulla scheda SD di ES1 (anche quelle già sincronizzate) e rigenera le elaborazioni AI.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onForceGlobalResync,
                    shape = RoundedCornerShape(0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Forza Risincronizzazione Completa", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
