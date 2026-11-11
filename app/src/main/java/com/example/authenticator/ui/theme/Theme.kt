package com.example.authenticator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.authenticator.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF5C4DCC), onPrimary = Color.White,
    primaryContainer = Color(0xFFEAE6FF), onPrimaryContainer = Color(0xFF30216B),
    secondary = Color(0xFF157B73), secondaryContainer = Color(0xFFD5F3EC),
    background = Color(0xFFF7F7FC), surface = Color(0xFFFDFBFF),
    surfaceVariant = Color(0xFFECEAF3), onSurfaceVariant = Color(0xFF5D5B6B)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFC4B8FF), onPrimary = Color(0xFF30216B),
    primaryContainer = Color(0xFF3D3079), onPrimaryContainer = Color(0xFFEAE6FF),
    secondary = Color(0xFF83D5C7), secondaryContainer = Color(0xFF154F47),
    background = Color(0xFF111119), surface = Color(0xFF1B1B26),
    surfaceVariant = Color(0xFF2C2B3C), onSurfaceVariant = Color(0xFFC6C2D5)
)

@Composable
fun AuthenticatorTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = Typography, content = content)
}
