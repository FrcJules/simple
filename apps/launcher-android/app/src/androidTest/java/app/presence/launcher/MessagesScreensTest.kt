package app.presence.launcher

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.presence.launcher.messages.MessageItem
import app.presence.launcher.messages.MessageStatus
import app.presence.launcher.messages.StoredMessage
import app.presence.launcher.notifications.Channel
import app.presence.launcher.ui.HomeScreen
import app.presence.launcher.messages.Attachment
import app.presence.launcher.ui.ConversationScreen
import app.presence.launcher.ui.ImageViewerScreen
import app.presence.launcher.ui.PhotoChoiceScreen
import app.presence.launcher.ui.PhotoConfirmScreen
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

    /** Pictures are loaded in the background: wait for them before taking a screenshot. */
    private fun shotAfterPicturesLoaded(name: String) {
        Thread.sleep(1_500)
        rule.waitForIdle()
        shot(name)
    }

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
        rule.onNodeWithText("Pas encore de messages.").assertIsDisplayed()
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
        rule.onNodeWithText("il y a 25 minutes · Reçu").assertIsDisplayed()
        rule.onNodeWithText("il y a 5 minutes · Non envoyé").assertIsDisplayed()
        rule.onNodeWithText("Réessayer").assertIsDisplayed()
        shot("25_conversation")
    }

    @Test
    fun aFailedMessageCanBeRetried() {
        var retried: String? = null
        val failed = msg("f", "À demain", 5, outgoing = true, status = MessageStatus.FAILED)
        rule.setContent { ConversationScreen(items[0], listOf(failed), now, canDictate = true, notice = null, onListen = {}, onReply = {}, onBack = {}, onRetry = { retried = it.id }) }
        rule.onNodeWithText("Réessayer").performClick()
        assertEquals("f", retried)
    }

    @Test
    fun aNewConversationOffersToDictateWithoutOpeningTheMicrophoneByItself() {
        rule.setContent { ConversationScreen(items[0], emptyList(), now, canDictate = true, notice = null, onListen = {}, onReply = {}, onBack = {}) }
        rule.onNodeWithText("Dire mon message").assertIsDisplayed()
    }

    @Test
    fun aSecurityCodeIsMaskedUntilTouched() {
        val thread = listOf(msg("a", "Votre code est 482913", 3, sensitive = true))
        rule.setContent { ConversationScreen(items[0], thread, now, canDictate = true, notice = null, onListen = {}, onReply = {}, onBack = {}) }
        assertEquals(0, rule.onAllNodesWithText("Votre code est 482913").fetchSemanticsNodes().size)
        rule.onNodeWithText("Message de sécurité (code)\nTouchez pour voir. Un code : ne le donnez à personne.").performClick()
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

    private fun pictureFile(): String {
        val bmp = android.graphics.Bitmap.createBitmap(300, 200, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bmp).drawColor(android.graphics.Color.rgb(200, 120, 40))
        val f = java.io.File(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "test-picture.png")
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        return f.path
    }

    private fun mmsFrom(text: String, status: MessageStatus, attachments: List<Attachment> = emptyList()) =
        StoredMessage("m", "Marie", Channel.SMS, text, now - 60_000, read = false, address = "0600000001", status = status, mms = true, attachments = attachments)

    @Test
    fun aReceivedPictureIsShownAndCanBeOpened() {
        val path = pictureFile()
        var opened: String? = null
        val m = mmsFrom("Regarde !", MessageStatus.NONE, listOf(Attachment("image/png", path)))
        rule.setContent { ConversationScreen(items[0], listOf(m), now, canDictate = true, notice = null, onListen = {}, onReply = {}, onBack = {}, onImage = { opened = it }) }
        rule.onNodeWithText("Regarde !").assertIsDisplayed()
        rule.onNodeWithContentDescription("Photo").assertIsDisplayed()
        rule.onNodeWithText("Touchez la photo pour l'agrandir.").assertIsDisplayed()
        shotAfterPicturesLoaded("29_conversation_photo")
        rule.onNodeWithContentDescription("Photo").performClick()
        assertEquals(path, opened)
    }

    @Test
    fun aPictureBeingReceivedIsExplainedAndHasNoRetry() {
        val receiving = mmsFrom("", MessageStatus.SENDING)
        rule.setContent { ConversationScreen(items[0], listOf(receiving), now, canDictate = true, notice = null, onListen = {}, onReply = {}, onBack = {}) }
        rule.onNodeWithText("Photo en cours de réception…").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Réessayer").fetchSemanticsNodes().size)
    }

    @Test
    fun aPictureThatDidNotArriveOffersARetry() {
        var retried: String? = null
        val failed = mmsFrom("", MessageStatus.FAILED)
        rule.setContent { ConversationScreen(items[0], listOf(failed), now, canDictate = true, notice = null, onListen = {}, onReply = {}, onBack = {}, onRetry = { retried = it.id }) }
        rule.onNodeWithText("Cette photo n'est pas arrivée.").assertIsDisplayed()
        shot("30_photo_failed")
        rule.onNodeWithText("Recevoir à nouveau").performClick()
        assertEquals("m", retried)
    }

    @Test
    fun theListPreviewSaysPhotoWhenThereIsNoText() {
        val m = mmsFrom("", MessageStatus.NONE, listOf(Attachment("image/png", pictureFile())))
        rule.setContent { MessagesScreen(listOf(MessageItem(m, marie, unread = 1)), now, onOpen = {}, onWrite = {}, onBack = {}) }
        rule.onNodeWithText("Photo").assertIsDisplayed()
    }

    @Test
    fun theImageViewerShowsThePictureWithOnlyABackButton() {
        var back = false
        rule.setContent { ImageViewerScreen(pictureFile(), onBack = { back = true }) }
        rule.onNodeWithContentDescription("Photo").assertIsDisplayed()
        shotAfterPicturesLoaded("31_image_viewer")
        rule.onNodeWithText("Retour").performClick()
        assertTrue(back)
    }

    @Test
    fun theConversationOffersToSendAPhotoOnlyWhenAllowed() {
        var asked = false
        rule.setContent { ConversationScreen(items[0], listOf(items[0].message), now, canDictate = true, notice = null, onListen = {}, onReply = {}, onBack = {}, onSendPhoto = { asked = true }) }
        rule.onNodeWithText("Envoyer une photo").performClick()
        assertTrue(asked)
    }

    @Test
    fun noPhotoButtonWithoutPermissionToSend() {
        rule.setContent { ConversationScreen(items[0], listOf(items[0].message), now, canDictate = true, notice = null, onListen = {}, onReply = {}, onBack = {}) }
        assertEquals(0, rule.onAllNodesWithText("Envoyer une photo").fetchSemanticsNodes().size)
    }

    @Test
    fun photoChoiceShowsWhoReceivesAndOffersCameraAndLibrary() {
        var taken = false
        var picked = false
        rule.setContent { PhotoChoiceScreen(marie, known = true, canTakePhoto = true, notice = null, onTake = { taken = true }, onPick = { picked = true }, onBack = {}) }
        rule.onNodeWithText("Envoyer une photo à :").assertIsDisplayed()
        rule.onNodeWithText("Marie").assertIsDisplayed()
        shot("32_photo_choice")
        rule.onNodeWithText("Prendre une photo maintenant").performClick()
        rule.onNodeWithText("Une photo de mon téléphone").performClick()
        assertTrue(taken && picked)
    }

    @Test
    fun photoChoiceWithoutACameraOnlyOffersTheLibraryAndShowsLaunchFailures() {
        rule.setContent { PhotoChoiceScreen(marie, known = true, canTakePhoto = false, notice = "Cette fonction n'est pas disponible sur ce téléphone.", onTake = {}, onPick = {}, onBack = {}) }
        assertEquals(0, rule.onAllNodesWithText("Prendre une photo maintenant").fetchSemanticsNodes().size)
        rule.onNodeWithText("Une photo de mon téléphone").assertIsDisplayed()
        rule.onNodeWithText("Cette fonction n'est pas disponible sur ce téléphone.").assertIsDisplayed()
    }

    @Test
    fun anUnknownRecipientIsShownWithAWarning() {
        val stranger = Contact(-1, "0611223344", "", null)
        rule.setContent { PhotoChoiceScreen(stranger, known = false, canTakePhoto = true, notice = null, onTake = {}, onPick = {}, onBack = {}) }
        rule.onNodeWithText("0611223344").assertIsDisplayed()
        rule.onNodeWithText("Ce numéro n'est pas dans vos contacts.").assertIsDisplayed()
    }

    @Test
    fun photoConfirmShowsThePictureAndTheRecipientAndCanBeRefused() {
        var sent = false
        var refused = false
        val uri = android.net.Uri.fromFile(java.io.File(pictureFile()))
        rule.setContent { PhotoConfirmScreen(marie, known = true, photo = uri, notice = null, sending = false, onSend = { sent = true }, onNotThisOne = { refused = true }) }
        rule.onNodeWithText("Envoyer cette photo ?").assertIsDisplayed()
        rule.onNodeWithText("Marie").assertIsDisplayed()
        shotAfterPicturesLoaded("33_photo_confirm")
        rule.onNodeWithText("Pas cette photo").performClick()
        assertTrue(refused)
        assertEquals(false, sent)
    }

    @Test
    fun whileSendingTheButtonsAreDisabledSoNothingIsSentTwice() {
        var sent = 0
        val uri = android.net.Uri.fromFile(java.io.File(pictureFile()))
        rule.setContent { PhotoConfirmScreen(marie, known = true, photo = uri, notice = null, sending = true, onSend = { sent++ }, onNotThisOne = {}) }
        rule.onNodeWithText("Envoi en cours…").assertIsDisplayed()
        rule.onNodeWithText("Envoi en cours…").performClick()
        assertEquals(0, sent)
    }

    @Test
    fun aFailureIsShownWithAnIconWordsAndStillOffersToSendAgain() {
        val uri = android.net.Uri.fromFile(java.io.File(pictureFile()))
        rule.setContent { PhotoConfirmScreen(marie, known = true, photo = uri, notice = "La photo n'a pas pu partir. Touchez « Envoyer » pour réessayer, ou choisissez une autre photo.", sending = false, onSend = {}, onNotThisOne = {}) }
        rule.onNodeWithText("La photo n'a pas pu partir. Touchez « Envoyer » pour réessayer, ou choisissez une autre photo.").assertIsDisplayed()
        rule.onNodeWithText("Envoyer").assertIsDisplayed()
    }

    @Test
    fun aWhatsAppConversationWithoutItsNotificationOffersToOpenWhatsApp() {
        var opened = false
        val wa = MessageItem(StoredMessage("w", "Marie", Channel.WHATSAPP, "Salut", now - 60_000), marie, unread = 1)
        rule.setContent { ConversationScreen(wa, listOf(wa.message), now, canDictate = true, notice = null, onListen = {}, onReply = {}, onBack = {}, onOpenWhatsApp = { opened = true }) }
        rule.onNodeWithText("Pour répondre à ce message, ouvrez WhatsApp.").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Répondre").fetchSemanticsNodes().size) // no reply button: it could not be delivered
        shot("34_whatsapp_open")
        rule.onNodeWithText("Ouvrir WhatsApp").performClick()
        assertTrue(opened)
    }

    @Test
    fun anAnswerableWhatsAppConversationOffersToReplyLikeAnSms() {
        val wa = MessageItem(StoredMessage("w", "Marie", Channel.WHATSAPP, "Salut", now - 60_000), marie, unread = 1, whatsappReplyAvailable = true)
        rule.setContent { ConversationScreen(wa, listOf(wa.message), now, canDictate = true, notice = null, onListen = {}, onReply = {}, onBack = {}) }
        rule.onNodeWithText("Répondre").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Ouvrir WhatsApp").fetchSemanticsNodes().size)
    }
}
