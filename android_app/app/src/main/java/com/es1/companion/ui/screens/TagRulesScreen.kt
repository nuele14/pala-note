package com.es1.companion.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.es1.companion.data.local.TagRuleEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagRulesScreen(
    tagRules: List<TagRuleEntity>,
    onSaveTagRule: (String, String) -> Unit,
    onResetDefaults: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prompt per Tipo di Nota", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Indietro"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = {
                        onResetDefaults()
                        Toast.makeText(context, "Regole ripristinate ai valori predefiniti", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Reset", color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    )
                    {
                        Icon(
                            imageVector = Icons.Rounded.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Riconoscimento Vocale dei Tag (Voice Trigger)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Pronunciando parole trigger come compito, da fare, riunione o idea all inizio della registrazione vocale, ES1 riconosce il tag e applica il System Prompt corrispondente per la sintesi AI.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(tagRules, key = { it.tag }) { rule ->
                TagRuleCard(
                    rule = rule,
                    onSave = { prompt ->
                        onSaveTagRule(rule.tag, prompt)
                        Toast.makeText(context, "Prompt salvato per '" + rule.tag + "'", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
fun TagRuleCard(
    rule: TagRuleEntity,
    onSave: (String) -> Unit
) {
    var promptText by remember(rule.systemPrompt) { mutableStateOf(rule.systemPrompt) }
    val isModified = promptText.trim() != rule.systemPrompt.trim()
    val tagColor = getRuleColor(rule.tag)
    val triggerWords = getTriggerWordsForTag(rule.tag)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(tagColor)
                    )
                    Text(
                        text = rule.tag,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ) {
                    Text(
                        text = "Cartella: " + rule.targetFolder,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (triggerWords.isNotBlank()) {
                Text(
                    text = "🎙️ Trigger: " + triggerWords,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                label = { Text("System Prompt di Rielaborazione") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                minLines = 2,
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = { onSave(promptText) },
                    enabled = isModified,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Salva Prompt", fontSize = 12.sp)
                }
            }
        }
    }
}

fun getRuleColor(tag: String): Color {
    return when (tag.lowercase()) {
        "todo" -> Color(0xFF4CAF50)
        "meeting" -> Color(0xFF2196F3)
        "idea" -> Color(0xFFFF9800)
        "work" -> Color(0xFF9C27B0)
        "buy" -> Color(0xFF00BCD4)
        "private" -> Color(0xFFE91E63)
        "note" -> Color(0xFF607D8B)
        else -> Color(0xFF795548)
    }
}

fun getTriggerWordsForTag(tag: String): String {
    return when (tag.lowercase()) {
        "todo" -> "task, todo, da fare, compito, promemoria, ricordati di, attività"
        "meeting" -> "meeting, riunione, colloquio, call, intervista, allineamento"
        "idea" -> "idea, spunto, intuizione, pensiero, nuovo progetto"
        "work" -> "work, lavoro, ufficio, progetto lavoro, task lavoro"
        "buy" -> "spesa, buy, compra, comprare, acquistare, lista spesa"
        "private" -> "private, privato, personale, diario, segreto"
        "note" -> "note, nota, appunto, scrivi"
        else -> ""
    }
}
