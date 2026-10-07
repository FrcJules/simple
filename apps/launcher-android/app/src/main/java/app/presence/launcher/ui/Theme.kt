package app.presence.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import app.presence.launcher.config.ThemeConfig

/** The active theme, provided once at the root from the JSON configuration (default: built-in high-contrast palette). */
val LocalThemeConfig = staticCompositionLocalOf { ThemeConfig.Default }

/** Explicit palette from [ThemeConfig] (validated for contrast by the parser), not dependent on the system dark mode. */
private fun ThemeConfig.toColorScheme() = lightColorScheme(
    primary = Color(primary),
    onPrimary = Color(onPrimary),
    primaryContainer = Color(accent),
    onPrimaryContainer = Color(text),
    background = Color(background),
    onBackground = Color(text),
    surface = Color(background),
    onSurface = Color(text),
    surfaceContainerHighest = Color(tile),
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
fun PresenceTheme(content: @Composable () -> Unit) {
    val config = LocalThemeConfig.current
    val density = LocalDensity.current
    // fontScale multiplies the system font scale and is validated >= 1: text never gets smaller than the 24 sp baseline.
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * config.fontScale)) {
        MaterialTheme(colorScheme = config.toColorScheme(), typography = Typography().withoutFixedLineHeight()) {
            // Explicit opaque background: text must never depend on the window theme or the system dark mode.
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                // Android 15+ draws edge-to-edge: keep content clear of the status/navigation bars and cutouts.
                Box(Modifier.fillMaxSize().safeDrawingPadding()) { content() }
            }
        }
    }
}
