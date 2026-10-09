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

    @Test
    fun codesSplitInTwoBlocksOrVeryLongAreCaught() {
        assertTrue(SensitiveText.looksSensitive("Votre code de connexion : 123 456"))
        assertTrue(SensitiveText.looksSensitive("Code de validation 123-456, expire dans 5 minutes"))
        assertTrue(SensitiveText.looksSensitive("Identifiant de paiement 1234567890"))
    }

    @Test
    fun moreBankingWordsAreCaught() {
        assertTrue(SensitiveText.looksSensitive("Achat de 59 EUR chez X : saisissez 482913 pour valider"))
        assertTrue(SensitiveText.looksSensitive("Pour votre securite, le CVV temporaire est 4821"))
    }

    @Test
    fun everydayWordsAndNumbersAreNotMistakenForCodes() {
        assertFalse(SensitiveText.looksSensitive("Mon lapin a mangé 2026 carottes")) // "pin" inside a word
        assertFalse(SensitiveText.looksSensitive("Regarde sur Pinterest, j'ai trouvé 12345 idées"))
        assertFalse(SensitiveText.looksSensitive("Rendez-vous confirmé à 1530, je viens te chercher"))
        assertFalse(SensitiveText.looksSensitive("Confirmation du rdv de 2026, à bientôt"))
        assertFalse(SensitiveText.looksSensitive("Appelle-moi au 0612345678 c'est urgent"))
        assertFalse(SensitiveText.looksSensitive("Le colis expire bientôt, rendez-vous en 2026"))
        assertFalse(SensitiveText.looksSensitive("J'ai fait 4500 pas aujourd'hui, et un bon achat"))
    }

    @Test
    fun aCodeNextToANumberIsStillCaughtEvenIfItLooksLikeAYearOrATime() {
        assertTrue(SensitiveText.looksSensitive("Le code du portail est 1234"))
        assertTrue(SensitiveText.looksSensitive("Votre code : 2026"))
        assertTrue(SensitiveText.looksSensitive("Mot de passe temporaire 1530"))
        assertTrue(SensitiveText.looksSensitive("Votre code de vérification est 482913"))
    }

    @Test
    fun aMoneyWordNeedsALongNumber() {
        assertTrue(SensitiveText.looksSensitive("Virement de 250 EUR reçu, référence 78451239"))
        assertFalse(SensitiveText.looksSensitive("Virement de 250 EUR reçu"))
        assertFalse(SensitiveText.looksSensitive("Paiement accepté : 1530")) // a 4-digit number alone, no code word
    }
}
