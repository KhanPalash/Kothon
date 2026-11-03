package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = KothonCyan,
    onPrimary = Color(0xFF0F172A),
    secondary = KothonIndigo,
    onSecondary = Color(0xFF0F172A),
    tertiary = KothonAmber,
    background = KothonDarkBg,
    surface = KothonDarkSurface,
    surfaceVariant = KothonDarkCard,
    onBackground = KothonTextPrimary,
    onSurface = KothonTextPrimary,
    onSurfaceVariant = KothonTextSecondary
)

private val LightColorScheme = darkColorScheme(
    // Default to dark tech look for audio intelligence app
    primary = KothonCyan,
    onPrimary = Color(0xFF0F172A),
    secondary = KothonIndigo,
    onSecondary = Color(0xFF0F172A),
    tertiary = KothonAmber,
    background = KothonDarkBg,
    surface = KothonDarkSurface,
    surfaceVariant = KothonDarkCard,
    onBackground = KothonTextPrimary,
    onSurface = KothonTextPrimary,
    onSurfaceVariant = KothonTextSecondary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent brand tech colors
    content: @Composable () -> Unit,
) {
    val colorScheme = DarkColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
