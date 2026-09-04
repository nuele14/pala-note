package com.es1.companion.ui.theme

import androidx.compose.ui.graphics.Color

// Cyber Dark Palette (grilledpixels.com inspired: pure black, white contrast, tech grays)
val CyberBlack = Color(0xFF000000)
val CyberDarkSurface = Color(0xFF0A0A0A)
val CyberDarkCard = Color(0xFF121212)
val CyberDarkCardBorder = Color(0xFF262626)
val CyberWhite = Color(0xFFFFFFFF)
val CyberLightGray = Color(0xFFE0E0E0)
val CyberMidGray = Color(0xFF888888)
val CyberDarkGray = Color(0xFF1E1E1E)

// Surfing Light Palette (surfing.academy inspired: pure white, vibrant coral #FF8562, dark text)
val SurfingWhite = Color(0xFFFFFFFF)
val SurfingLightSurface = Color(0xFFFAFAFA)
val SurfingLightCard = Color(0xFFFFFFFF)
val SurfingLightCardBorder = Color(0xFFE5E5E5)
val SurfingCoral = Color(0xFFFF8562)
val SurfingCoralDark = Color(0xFFD65632)
val SurfingCoralContainer = Color(0xFFFFECE6)
val SurfingCoralOnContainer = Color(0xFF992C0F)
val SurfingBlack = Color(0xFF111111)
val SurfingGray = Color(0xFF666666)

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
