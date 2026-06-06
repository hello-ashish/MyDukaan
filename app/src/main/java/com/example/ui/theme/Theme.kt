package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryLightTeal,
    secondary = AccentEmerald,
    tertiary = AccentLime,
    background = CleanSlateDark,
    surface = SurfaceSlateDark,
    primaryContainer = Color(0xFF114E4A), // Teal 900
    onPrimaryContainer = Color(0xFFCCFBF1), // Teal 100
    onPrimary = Color(0xFF0F172A),
    onSecondary = Color.White,
    onBackground = TextSlateDark,
    onSurface = TextSlateDark,
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryTeal,
    secondary = PrimaryDarkTeal,
    tertiary = AccentEmerald,
    background = CleanSlateLight,
    surface = SurfaceSlateLight,
    primaryContainer = Color(0xFFE6F4F1), // Very soft and elegant mint-teal container
    onPrimaryContainer = Color(0xFF0F766E), // Deep dark teal contrast
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextSlateLight,
    onSurface = TextSlateLight,
    surfaceVariant = Color(0xFFF1F5F9), // Beautiful slate 100
    onSurfaceVariant = Color(0xFF475569) // Beautiful slate 600
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
