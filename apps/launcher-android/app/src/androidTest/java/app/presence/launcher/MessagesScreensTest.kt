package app.presence.launcher

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.presence.launcher.messages.MessageItem
import app.presence.launcher.messages.StoredMessage
import app.presence.launcher.notifications.Channel
import app.presence.launcher.ui.HomeScreen
import app.presence.launcher.ui.MessageDetailScreen
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
        MessageItem(StoredMessage(id, from, Channel.SMS, text, now - ageMin * 60_000, read), contact)

    private val items = listOf(
        item("1", "Marie", "Bonjour papa, je passe demain après-midi avec les enfants.", 20, false, marie),
        item("2", "Numéro inconnu", "Votre colis arrive.", 180, true, null),
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
        rule.setContent { MessageDetailScreen(items[0], now, onListen = { listened = true }, onReply = {}, onBack = {}) }
        rule.onNodeWithText("Répondre").assertIsDisplayed()
        shot("22_message_detail")
        rule.onNodeWithText("Écouter").performClick()
        assertTrue(listened)
    }

    @Test
    fun detailHidesReplyForUnknownSenders() {
        rule.setContent { MessageDetailScreen(items[1], now, onListen = {}, onReply = {}, onBack = {}) }
        rule.onNodeWithText("Écouter").assertIsDisplayed()
        rule.onAllNodesWithTextCount("Répondre", 0)
    }

    @Test
    fun replyConfirmationShowsTheDictatedTextAndLetsYouCancel() {
        var sent = false
        var cancelled = false
        rule.setContent { ReplyConfirmScreen(items[0], "Oui, venez à 15 heures", onSend = { sent = true }, onRedo = {}, onCancel = { cancelled = true }) }
        rule.onNodeWithText("Oui, venez à 15 heures").assertIsDisplayed()
        rule.onNodeWithText("Répondre à Marie").assertIsDisplayed()
        shot("23_reply_confirm")
        rule.onNodeWithText("Non, annuler").performClick()
        assertTrue(cancelled)
        assertEquals(false, sent)
    }

    @Test
    fun homeShowsTheUnreadBadge() {
        rule.setContent { HomeScreen(seniorName = "Jean-Pierre", unreadMessages = 2) }
        rule.onNodeWithText("2").assertIsDisplayed()
        shot("24_home_badge")
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String, expected: Int) {
    assertEquals(expected, onAllNodesWithText(text).fetchSemanticsNodes().size)
}
