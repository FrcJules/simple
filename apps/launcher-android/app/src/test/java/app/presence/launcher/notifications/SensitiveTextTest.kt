package app.presence.launcher.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveTextTest {
    @Test
    fun oneTimeCodesAreSensitive() {
        assertTrue(SensitiveText.looksSensitive("Votre code de vérification est 482913"))
        assertTrue(SensitiveText.looksSensitive("Ne communiquez jamais ce code : 5521"))
        assertTrue(SensitiveText.looksSensitive("Your OTP is 774412. Do not share it."))
        assertTrue(SensitiveText.looksSensitive("Confirmez le virement de 250 EUR avec le 3D SECURE 123456"))
    }

    @Test
    fun ordinaryFamilyMessagesAreNot() {
        assertFalse(SensitiveText.looksSensitive("Bonjour papa, je passe demain à 15h"))
        assertFalse(SensitiveText.looksSensitive("On se retrouve à 18h30, apporte le gâteau"))
        assertFalse(SensitiveText.looksSensitive("Le code est sur la table")) // a keyword without digits
    }

    @Test
    fun parserDropsSensitiveMessagesBeforeAnythingIsStored() {
        val n = RawNotification("com.google.android.apps.messaging", "Ma banque", "Votre code de vérification est 482913", "msg")
        assertNull(NotificationParser.parse(n))
        val ok = RawNotification("com.google.android.apps.messaging", "Marie", "Je passe à 15h", "msg")
        assertEquals(Incoming.Message("Marie", Channel.SMS, "Je passe à 15h"), NotificationParser.parse(ok))
    }
}
