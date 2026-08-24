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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.es1.companion.data.local.NoteEntity
import com.es1.companion.ui.theme.getTagColor
import java.util.Locale

@Composable
fun NoteCard(
    note: NoteEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tagColor = getTagColor(note.tag)
    val hasTrans = !note.transcriptionText.isNullOrBlank()
    val hasElab = !note.elaboratedMarkdown.isNullOrBlank()

    val displayTitle = note.elaboratedTitle
        ?: if (hasTrans) note.transcriptionText!!.take(40) else "Nota #${note.deviceNoteNum}"

    val displaySnippet = note.elaboratedMarkdown?.take(90)?.replace("#", "")?.replace("- [ ]", "•")?.trim()
        ?: (note.transcriptionText?.take(90) ?: "Audio registrato")

    val dateFormatted = note.createdUtc.take(10)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Tag Badge + #Num + Duration + Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(tagColor)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = note.tag,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "#${note.deviceNoteNum}",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = String.format(Locale.US, "%.1fs • %s", note.durationSec, dateFormatted),
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }

            // Title
            Text(
                text = displayTitle,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Snippet Preview
            Text(
                text = displaySnippet,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )

            // Status Icons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "STT",
                    tint = if (hasTrans) Color(0xFF66BB6A) else Color.DarkGray,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = "LLM",
                    tint = if (hasElab) Color(0xFF42A5F5) else Color.DarkGray,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Sync",
                    tint = Color(0xFFAB47BC),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
