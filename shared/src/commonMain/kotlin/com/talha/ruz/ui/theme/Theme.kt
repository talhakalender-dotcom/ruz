package com.talha.ruz.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Ruz renkleri: petrol (düzen) + kehribar (gün ışığı).
private val Light = lightColorScheme(
    primary = Color(0xFF1E5A6B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBFE6F0),
    onPrimaryContainer = Color(0xFF001F27),
    secondary = Color(0xFFB7791F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDB0),
    onSecondaryContainer = Color(0xFF2A1800),
    background = Color(0xFFF8F7F4),
    onBackground = Color(0xFF1A1C1D),
    surface = Color(0xFFF8F7F4),
    onSurface = Color(0xFF1A1C1D),
    surfaceVariant = Color(0xFFE2E3DF),
    onSurfaceVariant = Color(0xFF44474A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F2EE),
    surfaceContainer = Color(0xFFEDECE8),
    surfaceContainerHigh = Color(0xFFE7E6E2),
    surfaceContainerHighest = Color(0xFFE1E0DC),
    outline = Color(0xFF74777A),
    outlineVariant = Color(0xFFC6C7C3),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8ED0E2),
    onPrimary = Color(0xFF003642),
    primaryContainer = Color(0xFF004E5E),
    onPrimaryContainer = Color(0xFFBFE6F0),
    secondary = Color(0xFFFFB955),
    onSecondary = Color(0xFF462A00),
    secondaryContainer = Color(0xFF633F00),
    onSecondaryContainer = Color(0xFFFFDDB0),
    background = Color(0xFF111416),
    onBackground = Color(0xFFE2E2E0),
    surface = Color(0xFF111416),
    onSurface = Color(0xFFE2E2E0),
    surfaceVariant = Color(0xFF3F4446),
    onSurfaceVariant = Color(0xFFC2C7C9),
    surfaceContainerLowest = Color(0xFF0C0F10),
    surfaceContainerLow = Color(0xFF191C1E),
    surfaceContainer = Color(0xFF1D2022),
    surfaceContainerHigh = Color(0xFF272A2C),
    surfaceContainerHighest = Color(0xFF323537),
    outline = Color(0xFF8C9193),
    outlineVariant = Color(0xFF3F4446),
)

@Composable
fun RuzTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
