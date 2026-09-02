package com.es1.companion.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.es1.companion.ui.theme.TechFontFamily

val AVAILABLE_FILTER_TAGS = listOf(
    "All" to "TUTTE LE NOTE",
    "Todo" to "TODO / TASK",
    "Idea" to "IDEE",
    "Meeting" to "MEETING",
    "Work" to "LAVORO",
    "Buy" to "ACQUISTI",
    "Private" to "PRIVATO",
    "Note" to "NOTE"
)

@Composable
fun TagFilterDropdown(
    selectedTag: String,
    totalNotesCount: Int,
    onTagSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val currentLabel = AVAILABLE_FILTER_TAGS.find { it.first == selectedTag }?.second
        ?: if (selectedTag == "All") "TUTTE LE NOTE" else selectedTag.uppercase()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label di riepilogo tech
        Text(
            text = "// COUNT: $totalNotesCount NOTES",
            fontSize = 11.sp,
            fontFamily = TechFontFamily,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Dropdown Selector Button (Tech Sharp)
        Box {
            Surface(
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(0.dp))
                    .clickable { expanded = true },
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "[ $currentLabel ]",
                        fontSize = 11.sp,
                        fontFamily = TechFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Apri filtri",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(0.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                AVAILABLE_FILTER_TAGS.forEach { (tagKey, tagTitle) ->
                    val isSelected = (tagKey == selectedTag) || (selectedTag == "All" && tagKey == "All")
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = tagTitle,
                                    fontSize = 12.sp,
                                    fontFamily = TechFontFamily,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onTagSelected(tagKey)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
