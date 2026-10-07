package app.presence.launcher.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationParserTest {
    private fun n(pkg: String, title: String?, text: String?, category: String? = "msg") = RawNotification(pkg, title, text, category)

    @Test
    fun googleDialerLabelInTitleCallerInText() =
        assertEquals(Incoming.MissedCall("Marie"), NotificationParser.parse(n("com.google.android.dialer", "Appel manqué", "Marie", null)))

    @Test
    fun callerInTitleLabelInText() =
        assertEquals(Incoming.MissedCall("Marie"), NotificationParser.parse(n("com.samsung.android.dialer", "Marie", "Missed call", null)))

    @Test
    fun missedCallCategoryAloneUsesTitle() =
        assertEquals(Incoming.MissedCall("Paul"), NotificationParser.parse(n("com.android.server.telecom", "Paul", "", "missed_call")))

    @Test
    fun ordinaryDialerNotificationIsIgnored() =
        assertNull(NotificationParser.parse(n("com.google.android.dialer", "Appel en cours", "Marie", null)))

    @Test
    fun missedCallWithoutCallerIsIgnored() =
        assertNull(NotificationParser.parse(n("com.google.android.dialer", "Appel manqué", "", null)))

    @Test
    fun whatsappMessage() =
        assertEquals(
            Incoming.Message("Jules", Channel.WHATSAPP, "Salut papa !"),
            NotificationParser.parse(n("com.whatsapp", "Jules", "Salut papa !")),
        )

    @Test
    fun whatsappUnreadCountSuffixIsStripped() =
        assertEquals(
            Incoming.Message("Famille", Channel.WHATSAPP, "Marie: coucou"),
            NotificationParser.parse(n("com.whatsapp", "Famille (3 messages)", "Marie: coucou")),
        )

    @Test
    fun whatsappAppSummaryIsIgnored() = assertNull(NotificationParser.parse(n("com.whatsapp", "WhatsApp", "3 nouveaux messages")))

    @Test
    fun smsMessage() =
        assertEquals(
            Incoming.Message("Marie", Channel.SMS, "Je passe à 15h"),
            NotificationParser.parse(n("com.google.android.apps.messaging", "Marie", "Je passe à 15h")),
        )

    @Test
    fun longTextIsTruncated() {
        val m = NotificationParser.parse(n("com.android.mms", "Marie", "x".repeat(900))) as Incoming.Message
        assertEquals(NotificationParser.MAX_TEXT, m.text.length)
    }

    @Test
    fun otherAppsAreNeverParsed() {
        assertNull(NotificationParser.parse(n("com.bank.app", "Votre solde", "1234 €")))
        assertNull(NotificationParser.parse(n("com.example.news", "Appel manqué", "Marie")))
    }

    @Test
    fun nonConversationNotificationsFromWatchedAppsAreIgnored() {
        assertNull(NotificationParser.parse(n("com.whatsapp", "Marie", "Envoi en cours…", "progress")))
        assertNull(NotificationParser.parse(n("com.google.android.apps.messaging", "Promo", "-50 % aujourd'hui", "promo")))
        assertNull(NotificationParser.parse(n("com.android.mms", "Marie", "Salut", null)))
    }

    @Test
    fun onlyWhitelistedPackagesAreWatched() {
        assertEquals(true, NotificationParser.isWatched("com.whatsapp"))
        assertEquals(false, NotificationParser.isWatched("com.bank.app"))
    }
}
