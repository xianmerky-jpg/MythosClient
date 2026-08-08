package com.mythos.client.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object MythosColors {
    val Background = Color(0xFF161615)
    val Panel = Color(0xFF1C1C1B)
    val Elevated = Color(0xFF252524)
    val Soft = Color(0xFF20201F)
    val Border = Color(0xFF333331)
    val BorderSoft = Color(0xFF2A2A28)
    val Text = Color(0xFFE7E7E4)
    val TextSecondary = Color(0xFFA2A29F)
    val TextMuted = Color(0xFF6F6F6C)
    val Accent = Color(0xFFD9D9D7)
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
