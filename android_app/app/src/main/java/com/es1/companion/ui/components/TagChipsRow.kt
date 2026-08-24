package com.es1.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.es1.companion.ui.theme.getTagColor

val ALL_TAGS = listOf("All", "Todo", "Meeting", "Idea", "Work", "Buy", "Private", "Note", "Untagged")

@Composable
fun TagChipsRow(
    selectedTag: String,
    onTagSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ALL_TAGS.forEach { tag ->
            val isSelected = (tag == selectedTag)
            val chipBgColor = if (isSelected) {
                if (tag == "All") MaterialTheme.colorScheme.primary else getTagColor(tag)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }

            val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(chipBgColor)
                    .clickable { onTagSelected(tag) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tag,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
