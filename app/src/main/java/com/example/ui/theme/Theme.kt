package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

class ColorTheme(
    val primary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val slateGray: Color,
    val neutralGray: Color,
    val borderGray: Color,
    val lightPurple: Color
)

@Composable
fun rememberColorTheme(darkTheme: Boolean = isSystemInDarkTheme()): ColorTheme {
    return remember(darkTheme) {
        if (darkTheme) {
            ColorTheme(
                primary = Color(0xFFA78BFA), // Pastel lavender/purple
                secondary = Color(0xFFC084FC), // Lavender purple gradient end
                background = Color(0xFF0F121E), // Deep space dark background
                surface = Color(0xFF13172E), // Card slate purple-blue
                textPrimary = Color.White, // Main high contrast
                slateGray = Color(0xFF94A3B8), // Muted slate gray
                neutralGray = Color(0xFF1E2442), // Textfield background
                borderGray = Color(0xFF1E2442), // Thin border
                lightPurple = Color(0xFF2E2A4C) // Integrated purple highlights/badges
            )
        } else {
            ColorTheme(
                primary = Color(0xFF6366F1), // Bold Electric indigo
                secondary = Color(0xFF4F46E5), // Indigo blue-purple
                background = Color(0xFFF9FAFB), // Clean warm paper light background
                surface = Color.White, // Clean white surface card
                textPrimary = Color(0xFF0F172A), // Soft obsidian black display headings
                slateGray = Color(0xFF475569), // Muted text/subtitles
                neutralGray = Color(0xFFF1F5F9), // Input pill backround
                borderGray = Color(0xFFE2E8F0), // Clean thin border lines
                lightPurple = Color(0xFFEEF2FF) // Creamy light-lavender highlight blocks
            )
        }
    }
}

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFA78BFA),
    secondary = Color(0xFFC084FC),
    tertiary = Color(0xFFEFB8C8),
    background = Color(0xFF0F121E),
    surface = Color(0xFF13172E),
    onPrimary = Color(0xFF13172E),
    onSecondary = Color(0xFFCCC2DC),
    onBackground = Color.White,
    onSurface = Color.White,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF6366F1),
    secondary = Color(0xFF4F46E5),
    tertiary = Color(0xFFEEF2FF),
    background = Color(0xFFF9FAFB),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Force customized Bold Typography visual theme instead of dynamic OS tokens
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
