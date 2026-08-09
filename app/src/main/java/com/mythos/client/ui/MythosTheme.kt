package com.mythos.client.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Mythos Material 3 design tokens.
 *
 * The app keeps its dark network-console identity, but now uses an intentional
 * tonal palette instead of relying on borders and near-identical gray panels.
 * These aliases also keep the existing feature UI readable while components
 * are migrated to semantic Material color roles.
 */
object MythosColors {
    val Background = Color(0xFF0B1020)
    val Panel = Color(0xFF11182A)
    val Elevated = Color(0xFF182238)
    val Interactive = Color(0xFF223154)
    val Soft = Color(0xFF141D31)
    val Border = Color(0xFF46526E)
    val BorderSoft = Color(0xFF2C3853)
    val Text = Color(0xFFE6E9F2)
    val TextSecondary = Color(0xFFC3C7D4)
    val TextMuted = Color(0xFF8F99B2)
    val Accent = Color(0xFFBAC6FF)
    val AccentMuted = Color(0xFF9CAEF7)
    val PrimaryContainer = Color(0xFF33477F)
    val OnPrimary = Color(0xFF14245B)
    val Success = Color(0xFF82D5A3)
    val Warning = Color(0xFFFFD080)
    val Danger = Color(0xFFFFB4AB)
}

private val MythosScheme = darkColorScheme(
    primary = MythosColors.Accent,
    onPrimary = MythosColors.OnPrimary,
    primaryContainer = MythosColors.PrimaryContainer,
    onPrimaryContainer = Color(0xFFDDE1FF),
    inversePrimary = Color(0xFF485D92),
    secondary = Color(0xFFC1C8E8),
    onSecondary = Color(0xFF2A3042),
    secondaryContainer = Color(0xFF3F465B),
    onSecondaryContainer = Color(0xFFDDE1FF),
    tertiary = Color(0xFF9DD1FF),
    onTertiary = Color(0xFF003352),
    tertiaryContainer = Color(0xFF164B6B),
    onTertiaryContainer = Color(0xFFCDE5FF),
    background = MythosColors.Background,
    onBackground = MythosColors.Text,
    surface = MythosColors.Background,
    onSurface = MythosColors.Text,
    surfaceVariant = MythosColors.Elevated,
    onSurfaceVariant = MythosColors.TextSecondary,
    surfaceTint = MythosColors.Accent,
    inverseSurface = Color(0xFFE2E2EA),
    inverseOnSurface = Color(0xFF2E3038),
    error = MythosColors.Danger,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = MythosColors.Border,
    outlineVariant = MythosColors.BorderSoft,
    scrim = Color.Black
)

private val MythosTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.25.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

private val MythosShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MythosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MythosScheme,
        typography = MythosTypography,
        shapes = MythosShapes,
        content = content
    )
}
