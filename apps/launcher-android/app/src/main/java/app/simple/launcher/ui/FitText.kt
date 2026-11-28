package app.simple.launcher.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * A short label (tile, tab, name) that never breaks inside a word: "Message / s" is unreadable for someone who reads little.
 * Lines break between words (a hyphenated name such as "Marie-Christine" counts as one word); if the widest word does not fit on one line (large system font, small screen),
 * the text gets smaller instead of being cut in the middle, but never smaller on screen than [minFontSize] at the normal font size
 * (24 sp, the project's minimum): someone who enlarged the system font still gets at least what everyone else gets.
 */
@Composable
fun FitText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign = TextAlign.Center,
    minFontSize: TextUnit = 24.sp,
) {
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val style = LocalTextStyle.current.copy(fontWeight = fontWeight)
        val maxWidth = constraints.maxWidth
        // sp grow with the system font scale: divide so the floor is the same rendered size as minFontSize at scale 1.
        val floor = minOf(fontSize.value, minFontSize.value / LocalDensity.current.fontScale)
        val size = remember(text, fontSize, floor, fontWeight, maxWidth, style) {
            val words = text.split(WORD_BREAK).filter { it.isNotBlank() }
            fun widest(size: Float) = words.maxOfOrNull { measurer.measure(it, style.copy(fontSize = size.sp), softWrap = false).size.width } ?: 0
            var s = fontSize.value
            while (s - 1f >= floor && widest(s) > maxWidth) s -= 1f
            s.sp
        }
        Text(text, fontSize = size, fontWeight = fontWeight, textAlign = textAlign, maxLines = MAX_LINES, overflow = TextOverflow.Ellipsis)
    }
}

/** Where a line may break. Not after a hyphen: the platform line breaker does not reliably break there. */
private val WORD_BREAK = Regex("\\s+")
private const val MAX_LINES = 3
