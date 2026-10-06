package app.presence.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Explicit high-contrast palette (black on white, dark blue accents): not the default Material colors, and not dependent on the system dark mode. */
private val HighContrast = lightColorScheme(
    primary = Color(0xFF0B3D91),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceContainerHighest = Color(0xFFE3E8F2),
)

@Composable
fun PresenceTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = HighContrast) {
    // Explicit opaque background: text must never depend on the window theme or the system dark mode.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
}
