package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SteamDarkColorScheme = darkColorScheme(
    primary = SteamAccent,
    onPrimary = Color(0xFF020617),
    primaryContainer = Color(0xFF083344),
    onPrimaryContainer = SteamAccent,
    secondary = SteamNeon,
    onSecondary = Color(0xFF022C22),
    secondaryContainer = Color(0xFF064E3B),
    onSecondaryContainer = SteamNeon,
    tertiary = SteamSale,
    onTertiary = Color(0xFF0F172A),
    background = SteamBg,
    onBackground = SteamTextPrimary,
    surface = SteamSurface,
    onSurface = SteamTextPrimary,
    surfaceVariant = SteamCard,
    onSurfaceVariant = SteamTextSecondary,
    outline = SteamBorder,
    error = SteamDanger,
    onError = Color(0xFF450A0A)
)

private val SteamShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun SteamPulseTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SteamDarkColorScheme,
        typography = SteamTypography,
        shapes = SteamShapes,
        content = content
    )
}
