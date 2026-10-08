package app.presence.launcher.messages

import org.junit.Assert.assertEquals
import org.junit.Test

class TextSafetyTest {
    @Test
    fun keepsOrdinaryTextIncludingAccentsEmojiAndLineBreaks() =
        assertEquals("Bonjour papa, ça va ? 😊\nÀ demain\tvers 15h", TextSafety.clean("Bonjour papa, ça va ? 😊\nÀ demain\tvers 15h"))

    @Test
    fun removesControlBidiOverrideAndInvisibleCharacters() {
        assertEquals("virement 100 euros", TextSafety.clean("virement\u202E 100\u200B euros\u0000\u0007"))
        assertEquals("abc", TextSafety.clean("a\u2066b\u2069c\uFEFF"))
    }
}
