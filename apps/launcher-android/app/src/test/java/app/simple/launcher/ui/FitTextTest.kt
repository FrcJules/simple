package app.simple.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], fontScale = 2f)
class FitTextTest {
    @get:Rule val rule = createComposeRule()

    private var layout: TextLayoutResult? = null

    /** Shows [text] in a box [width] wide and returns how it was laid out; screen readers still get the plain text. */
    private fun layoutOf(text: String, width: Int, fontSize: Int): TextLayoutResult {
        rule.setContent { Box(Modifier.width(width.dp)) { FitText(text, fontSize = fontSize.sp, onTextLayout = { layout = it }) } }
        rule.onNodeWithText(text).assertExists()
        return checkNotNull(layout)
    }

    /** Every line ends at a space, after a hyphen, or at the end of the text: never inside a word. */
    private fun assertNoWordCut(text: String, layout: TextLayoutResult) {
        val shown = layout.layoutInput.text.text // with the zero-width spaces after hyphens
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            val before = shown[end - 1]
            assertTrue("line $line of \"$text\" ends inside a word", before.isWhitespace() || before == '\u200B' || shown[end].isWhitespace())
        }
    }

    @Test
    fun aSingleWordStaysOnOneLineByGettingSmaller() {
        assertEquals(1, layoutOf("Messages", width = 140, fontSize = 24).lineCount)
    }

    @Test
    fun aHyphenatedNameBreaksAfterTheHyphenInANarrowCard() {
        val name = "Marie-Christine"
        val layout = layoutOf(name, width = 150, fontSize = 28)
        assertEquals(2, layout.lineCount)
        assertEquals("Marie-\u200B", layout.layoutInput.text.text.substring(0, layout.getLineEnd(0)))
    }

    @Test
    fun aLongNameBreaksOnlyBetweenWords() {
        val name = "Marie-Christine de La Rochefoucauld"
        assertNoWordCut(name, layoutOf(name, width = 180, fontSize = 28))
    }
}
