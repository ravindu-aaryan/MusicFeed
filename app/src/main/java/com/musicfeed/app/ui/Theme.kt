package com.musicfeed.app.ui

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val FallbackColors = darkColorScheme(
    primary = Color(0xFFE1306C),
    secondary = Color(0xFFF77737),
    tertiary = Color(0xFF833AB4),
    background = Color(0xFF120C18),
    surface = Color(0xFF1A1025),
)

@Composable
fun MusicFeedTheme(content: @Composable () -> Unit) {
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        FallbackColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
