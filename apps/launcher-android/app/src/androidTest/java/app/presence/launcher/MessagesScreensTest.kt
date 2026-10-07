package app.presence.launcher

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.presence.launcher.messages.MessageItem
import app.presence.launcher.messages.MessageStatus
import app.presence.launcher.messages.StoredMessage
import app.presence.launcher.notifications.Channel
import app.presence.launcher.ui.HomeScreen
import app.presence.launcher.ui.ConversationScreen
import app.presence.launcher.ui.PickContactScreen
import app.presence.launcher.ui.SmsRoleScreen
import app.presence.launcher.ui.MessagesScreen
import app.presence.launcher.ui.ReplyConfirmScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MessagesScreensTest {
    @get:Rule val rule = createComposeRule()

    private val now = 1_800_000_000_000L
    private val marie = Contact(1, "Marie", "0600000001", null)
    private fun item(id: String, from: String, text: String, ageMin: Long, read: Boolean, contact: Contact?) =
        MessageItem(StoredMessage(id, from, Channel.SMS, text, now - ageMin * 60_000, read, address = "0600000001"), contact, unread = if (read) 0 else 1)

    private val items = listOf(
        item("1", "Marie", "Bonjour papa, je passe demain après-midi avec les enfants.", 20, false, marie),
        item("2", "Numéro inconnu", "Votre colis arrive.", 180, true, null).let { it.copy(message = it.message.copy(address = null)) },
    )

    private fun shot(name: String) = Screenshots.save(name, rule.onRoot().captureToImage().asAndroidBitmap())

    @Test
    fun listShowsSendersTextAndNewMarker() {
        rule.setContent { MessagesScreen(items, now, onOpen = {}, onWrite = {}, onBack = {}) }
        rule.onNodeWithText("Mes messages").assertIsDisplayed()
        rule.onNodeWithText("Marie").assertIsDisplayed()
        rule.onNodeWithText("il y a 20 minutes").assertIsDisplayed()
        rule.onNodeWithText("il y a 3 heures").assertIsDisplayed()
        rule.onNodeWithText("Nouveau").assertIsDisplayed()
        shot("20_messages_list")
    }

    @Test
    fun openingARowReportsIt() {
        var opened: String? = null
        rule.setContent { MessagesScreen(items, now, onOpen = { opened = it.message.id }, onWrite = {}, onBack = {}) }
        rule.onNodeWithText("Marie").performClick()
        assertEquals("1", opened)
    }

    @Test
    fun emptyListSaysSo() {
        rule.setContent { MessagesScreen(emptyList(), now, onOpen = {}, onWrite = {}, onBack = {}) }
        rule.onNodeWithText("Aucun nouveau message.").assertIsDisplayed()
        shot("21_messages_empty")
    }

    @Test
    fun detailOffersListenAndReplyOnlyForKnownContacts() {
        var listened = false
        rule.setContent { ConversationScreen(items[0], listOf(items[0].message), now, canDictate = true, notice = null, onListen = { listened = true }, onReply = {}, onBack = {}) }
        rule.onNodeWithText("Répondre").assertIsDisplayed()
        shot("22_message_detail")
        rule.onNodeWithText("Écouter").performClick()
        assertTrue(listened)
    }

    @Test
    fun detailHidesReplyForUnknownSenders() {
        rule.setContent { ConversationScreen(items[1], listOf(items[1].message), now, canDictate = true, notice = null, onListen = {}, onReply = {}, onBack = {}) }
        rule.onNodeWithText("Écouter").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Répondre").fetchSemanticsNodes().size)
    }

    @Test
    fun replyConfirmationShowsTheDictatedTextAndLetsYouCancel() {
        var sent = false
        var cancelled = false
        rule.setContent { ReplyConfirmScreen(items[0], "Oui, venez à 15 heures", onListen = {}, onSend = { sent = true }, onRedo = {}, onCancel = { cancelled = true }) }
        rule.onNodeWithText("Oui, venez à 15 heures").assertIsDisplayed()
        rule.onNodeWithText("Répondre à Marie").assertIsDisplayed()
        shot("23_reply_confirm")
        rule.onNodeWithText("Ne pas envoyer").performClick()
        assertTrue(cancelled)
        assertEquals(false, sent)
    }

    @Test
    fun homeShowsTheUnreadBadge() {
        rule.setContent { HomeScreen(seniorName = "Jean-Pierre", unreadMessages = 2) }
        rule.onNodeWithText("2 nouveaux").assertIsDisplayed()
        shot("24_home_badge")
    }

    @Test
    fun detailExplainsWhenDictationIsUnavailable() {
        rule.setContent { ConversationScreen(items[0], listOf(items[0].message), now, canDictate = false, notice = null, onListen = {}, onReply = {}, onBack = {}) }
        rule.onNodeWithText("Je ne peux pas écouter votre voix sur ce téléphone. Demandez à un proche.").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Répondre").fetchSemanticsNodes().size)
    }

    @Test
    fun detailShowsTheNoticeAfterAnEmptyDictation() {
        rule.setContent { ConversationScreen(items[0], listOf(items[0].message), now, canDictate = true, notice = "Je n'ai rien entendu. Touchez « Répondre » pour réessayer.", onListen = {}, onReply = {}, onBack = {}) }
        rule.onNodeWithText("Je n'ai rien entendu. Touchez « Répondre » pour réessayer.").assertIsDisplayed()
    }

    private fun msg(id: String, text: String, ageMin: Long, outgoing: Boolean = false, status: MessageStatus = MessageStatus.NONE, sensitive: Boolean = false) =
        StoredMessage(id, "Marie", Channel.SMS, text, now - ageMin * 60_000, read = true, outgoing = outgoing, address = "0600000001", status = status, sensitive = sensitive)

    @Test
    fun conversationShowsBothDirectionsWithTheirStatus() {
        val thread = listOf(
            msg("a", "Bonjour papa", 30),
            msg("b", "Bonjour ma chérie", 25, outgoing = true, status = MessageStatus.DELIVERED),
            msg("c", "À demain", 5, outgoing = true, status = MessageStatus.FAILED),
        )
        rule.setContent { ConversationScreen(items[0], thread, now, canDictate = true, notice = null, onListen = {}, onReply = {}, onBack = {}) }
        rule.onNodeWithText("Bonjour papa").assertIsDisplayed()
        rule.onNodeWithText("Bonjour ma chérie").assertIsDisplayed()
        rule.onNodeWithText("Vous · il y a 25 minutes · Remis").assertIsDisplayed()
        rule.onNodeWithText("Vous · il y a 5 minutes · Non envoyé").assertIsDisplayed()
        shot("25_conversation")
    }

    @Test
    fun aSecurityCodeIsMaskedUntilTouched() {
        val thread = listOf(msg("a", "Votre code est 482913", 3, sensitive = true))
        rule.setContent { ConversationScreen(items[0], thread, now, canDictate = true, notice = null, onListen = {}, onReply = {}, onBack = {}) }
        assertEquals(0, rule.onAllNodesWithText("Votre code est 482913").fetchSemanticsNodes().size)
        rule.onNodeWithText("Message de sécurité (code)\nTouchez pour voir").performClick()
        rule.onNodeWithText("Votre code est 482913").assertIsDisplayed()
        // and it is never offered to be read aloud
        assertEquals(0, rule.onAllNodesWithText("Écouter").fetchSemanticsNodes().size)
    }

    @Test
    fun directSendWordingIsShownWhenWeAreTheSmsApp() {
        rule.setContent { ReplyConfirmScreen(items[0], "Oui", onListen = {}, onSend = {}, onRedo = {}, onCancel = {}, directSend = true) }
        rule.onNodeWithText("Le message sera envoyé tout de suite.").assertIsDisplayed()
        rule.onNodeWithText("Envoyer").assertIsDisplayed()
        shot("26_reply_direct")
    }

    @Test
    fun pickingARecipientReportsTheContact() {
        var picked: Contact? = null
        rule.setContent { PickContactScreen(R.string.write_to_title, listOf(marie), onPick = { picked = it }, onBack = {}) }
        rule.onNodeWithText("À qui voulez-vous écrire ?").assertIsDisplayed()
        rule.onNodeWithText("Marie").performClick()
        assertEquals(marie, picked)
        shot("27_pick_recipient")
    }

    @Test
    fun smsRoleExplanationOffersToContinueOrWait() {
        var later = false
        rule.setContent { SmsRoleScreen(onContinue = {}, onLater = { later = true }) }
        rule.onNodeWithText("Continuer").assertIsDisplayed()
        rule.onNodeWithText("Plus tard").performClick()
        assertTrue(later)
        shot("28_sms_role")
    }
}
