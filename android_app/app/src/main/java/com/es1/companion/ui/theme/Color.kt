package com.es1.companion.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// ES1 Dark Palette
val ES1DarkSurface = Color(0xFF1E1E1E)
val ES1DarkBackground = Color(0xFF121212)
val ES1DarkCard = Color(0xFF252525)
val ES1Primary = Color(0xFF7C4DFF)
val ES1PrimaryVariant = Color(0xFF651FFF)
val ElectricBlue = Color(0xFF2979FF)

// Tag Colors
val TagTodo = Color(0xFFFFA726)
val TagMeeting = Color(0xFFAB47BC)
val TagIdea = Color(0xFF66BB6A)
val TagWork = Color(0xFF42A5F5)
val TagBuy = Color(0xFFFFCA28)
val TagPrivate = Color(0xFFEC407A)
val TagNote = Color(0xFF26C6DA)
val TagUntagged = Color(0xFFBDBDBD)
val TagDefault = Color(0xFF78909C)

fun getTagColor(tag: String): Color {
    return when (tag.lowercase()) {
        "todo" -> TagTodo
        "meeting" -> TagMeeting
        "idea" -> TagIdea
        "work" -> TagWork
        "buy" -> TagBuy
        "private" -> TagPrivate
        "note" -> TagNote
        "untagged" -> TagUntagged
        else -> TagDefault
    }
}
