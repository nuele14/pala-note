package com.es1.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NoteAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.es1.companion.data.local.NoteEntity
import com.es1.companion.ui.components.NoteCard
import com.es1.companion.ui.components.TagChipsRow

@Composable
fun FeedScreen(
    notes: List<NoteEntity>,
    selectedTag: String,
    onTagSelected: (String) -> Unit,
    onNoteClick: (NoteEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Tag Filter Chips Row
        TagChipsRow(
            selectedTag = selectedTag,
            onTagSelected = onTagSelected
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (notes.isEmpty()) {
            // Empty State
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
                        imageVector = Icons.Outlined.NoteAlt,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(54.dp)
                    )
                    Text(
                        text = "Nessuna nota trovata",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.LightGray
                    )
                    Text(
                        text = "Registra una nota su ES1 e sincronizza!",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        } else {
            // Notes List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp) // Space for FAB
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        onClick = { onNoteClick(note) }
                    )
                }
            }
        }
    }
}
