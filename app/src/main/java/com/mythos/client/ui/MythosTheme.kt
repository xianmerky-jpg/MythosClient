package com.mythos.client.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Mythos visual system.
 *
 * Intentionally monochrome: the product identity comes from hierarchy, spacing,
 * typography and iconography rather than bright accent colors. Surfaces are
 * opaque so the UI stays crisp on OLED displays instead of looking muddy.
 */
object MythosColors {
    val Background = Color(0xFF0D0F11)
    val Panel = Color(0xFF14171A)
    val Elevated = Color(0xFF1A1E22)
    val Interactive = Color(0xFF20252A)
    val Soft = Color(0xFF181B1F)
    val Border = Color(0xFF2B3036)
    val BorderSoft = Color(0xFF23282D)
    val Text = Color(0xFFF2F3F1)
    val TextSecondary = Color(0xFFB4B8BD)
    val TextMuted = Color(0xFF7D838A)
    val Accent = Color(0xFFE6E8E7)
    val AccentMuted = Color(0xFFC9CDCF)
}

private val MythosScheme = darkColorScheme(
    background = MythosColors.Background,
    surface = MythosColors.Panel,
    surfaceVariant = MythosColors.Elevated,
    primary = MythosColors.Accent,
    onPrimary = MythosColors.Background,
    onBackground = MythosColors.Text,
    onSurface = MythosColors.Text,
    outline = MythosColors.Border
)

@Composable
fun MythosTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MythosScheme, content = content)
}
