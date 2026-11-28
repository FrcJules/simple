package app.simple.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
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

    private fun layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    /** Every line ends at a space, after a hyphen, or at the end of the text: never inside a word. */
    private fun assertNoWordCut(text: String) {
        val layout = layoutOf(text)
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            val before = text[end - 1]
            assertTrue("line $line of \"$text\" ends inside a word", before.isWhitespace() || before == '-' || text[end].isWhitespace())
        }
    }

    @Test
    fun aSingleWordStaysOnOneLineByGettingSmaller() {
        rule.setContent { Box(Modifier.width(140.dp)) { FitText("Messages", fontSize = 24.sp) } }
        assertEquals(1, layoutOf("Messages").lineCount)
    }

    @Test
    fun aLongNameBreaksOnlyBetweenWords() {
        val name = "Marie-Christine de La Rochefoucauld"
        rule.setContent { Box(Modifier.width(180.dp)) { FitText(name, fontSize = 28.sp) } }
        assertNoWordCut(name)
    }
}
