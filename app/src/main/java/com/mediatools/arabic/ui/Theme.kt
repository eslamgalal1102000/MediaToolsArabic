package com.mediatools.arabic.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD1AE),
    onPrimary = Color(0xFF00382A),
    background = Color(0xFF101418),
    surface = Color(0xFF101418),
    onBackground = Color(0xFFE1E3E8),
    onSurface = Color(0xFFE1E3E8),
)

@Composable
fun MediaToolsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Dark) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}

fun formatSize(bytes: Long): String {
    if (bytes < 0) return "—"
    val mb = bytes / 1_000_000.0
    return if (mb >= 1) String.format(Locale.US, "%.2f MB", mb)
    else String.format(Locale.US, "%.0f KB", bytes / 1000.0)
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}
