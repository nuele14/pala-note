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
val ES1DarkCardBorder = Color(0xFF383838)
val ES1DarkOnSurface = Color(0xFFEEEEEE)
val ES1DarkOnSurfaceVariant = Color(0xFFCCCCCC)

// ES1 Light Palette
val ES1LightSurface = Color(0xFFFFFFFF)
val ES1LightBackground = Color(0xFFF6F7F9)
val ES1LightCard = Color(0xFFFFFFFF)
val ES1LightCardBorder = Color(0xFFE5E7EB)
val ES1LightOnSurface = Color(0xFF1F2937)
val ES1LightOnSurfaceVariant = Color(0xFF4B5563)

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
val TagUntagged = Color(0xFF9E9E9E)
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
