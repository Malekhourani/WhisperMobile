package com.whisper.mobile.ui.theme

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6B4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA7F5D2),
    secondary = Color(0xFF4E6356),
    surface = Color(0xFFFBFDF9),
    background = Color(0xFFFBFDF9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BD8B6),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF00513A),
    secondary = Color(0xFFB3CCBB),
    surface = Color(0xFF191C1A),
    background = Color(0xFF191C1A),
)

@Composable
fun WhisperMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
