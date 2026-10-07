package app.presence.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit

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

/**
 * Our text is much larger than Material's defaults, whose styles carry a fixed line height (e.g. 24sp):
 * lines would overlap. Dropping it lets Android derive the spacing from the font size.
 * (`ProvideTextStyle` cannot do this: it merges, and an unspecified value keeps the old one.)
 */
private fun Typography.withoutFixedLineHeight(): Typography {
    fun TextStyle.free() = copy(lineHeight = TextUnit.Unspecified)
    return Typography(
        displayLarge = displayLarge.free(), displayMedium = displayMedium.free(), displaySmall = displaySmall.free(),
        headlineLarge = headlineLarge.free(), headlineMedium = headlineMedium.free(), headlineSmall = headlineSmall.free(),
        titleLarge = titleLarge.free(), titleMedium = titleMedium.free(), titleSmall = titleSmall.free(),
        bodyLarge = bodyLarge.free(), bodyMedium = bodyMedium.free(), bodySmall = bodySmall.free(),
        labelLarge = labelLarge.free(), labelMedium = labelMedium.free(), labelSmall = labelSmall.free(),
    )
}

@Composable
fun PresenceTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = HighContrast, typography = Typography().withoutFixedLineHeight()) {
        // Explicit opaque background: text must never depend on the window theme or the system dark mode.
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // Android 15+ draws edge-to-edge: keep content clear of the status/navigation bars and cutouts.
            Box(Modifier.fillMaxSize().safeDrawingPadding()) { content() }
        }
    }
