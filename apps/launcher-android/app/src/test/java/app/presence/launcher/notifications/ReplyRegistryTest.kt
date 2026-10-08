package app.presence.launcher.notifications

import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.test.core.app.ApplicationProvider
import app.presence.launcher.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReplyRegistryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun fresh() = ReplyRegistry.clear()
    @After fun cleanUp() = ReplyRegistry.clear()

    private fun pending(): PendingIntent =
        PendingIntent.getBroadcast(context, 0, Intent("app.presence.test.REPLY"), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun notification(freeText: Boolean = true, withInput: Boolean = true): Notification {
        val icon = Icon.createWithResource(context, R.drawable.ic_launcher_foreground)
        val b = Notification.Action.Builder(icon, "Répondre", pending())
        if (withInput) b.addRemoteInput(RemoteInput.Builder("reply_text").setAllowFreeFormInput(freeText).build())
        return Notification.Builder(context, "test").setSmallIcon(icon).addAction(b.build()).build()
    }

    @Test
    fun findsTheReplyActionThatTakesFreeText() {
        val (action, inputs) = ReplyRegistry.findReplyAction(notification())!!
        assertNotNull(action)
        assertEquals("reply_text", inputs.single().resultKey)
    }

    @Test
    fun ignoresNotificationsWithoutAUsableReplyAction() {
        assertNull(ReplyRegistry.findReplyAction(notification(withInput = false)))
        assertNull(ReplyRegistry.findReplyAction(notification(freeText = false))) // only canned answers: not for dictated text
        assertNull(ReplyRegistry.findReplyAction(Notification.Builder(context, "t").setSmallIcon(Icon.createWithResource(context, R.drawable.ic_launcher_foreground)).build()))
    }

    @Test
    fun repliesThroughTheNotificationWithTheTextInTheRightField() {
        val (action, inputs) = ReplyRegistry.findReplyAction(notification())!!
        ReplyRegistry.put("key1", "whatsapp:name:marie", action, inputs)
        assertTrue(ReplyRegistry.reply(context, "whatsapp:name:marie", "  Oui, à demain !  "))

        val sent = shadowOf(context as Application).broadcastIntents.last()
        assertEquals("app.presence.test.REPLY", sent.action)
        val results = RemoteInput.getResultsFromIntent(sent)
        assertEquals("Oui, à demain !", results.getCharSequence("reply_text").toString())
    }

    @Test
    fun availabilityFollowsTheNotification() {
        val (action, inputs) = ReplyRegistry.findReplyAction(notification())!!
        ReplyRegistry.put("key1", "whatsapp:name:marie", action, inputs)
        assertEquals(setOf("whatsapp:name:marie"), ReplyRegistry.available.value)
        assertTrue(ReplyRegistry.canReply("whatsapp:name:marie"))
        ReplyRegistry.removeNotification("other-key") // someone else's notification: nothing changes
        assertTrue(ReplyRegistry.canReply("whatsapp:name:marie"))
        ReplyRegistry.removeNotification("key1") // WhatsApp's notification was dismissed
        assertFalse(ReplyRegistry.canReply("whatsapp:name:marie"))
        assertTrue(ReplyRegistry.available.value.isEmpty())
    }

    @Test
    fun theLatestNotificationOfAConversationReplacesTheOlderOne() {
        val (a1, i1) = ReplyRegistry.findReplyAction(notification())!!
        val (a2, i2) = ReplyRegistry.findReplyAction(notification())!!
        ReplyRegistry.put("old", "whatsapp:name:marie", a1, i1)
        ReplyRegistry.put("new", "whatsapp:name:marie", a2, i2)
        ReplyRegistry.removeNotification("old") // the old one going away must not drop the live one
        assertTrue(ReplyRegistry.canReply("whatsapp:name:marie"))
    }

    @Test
    fun refusesWhenThereIsNothingToAnswerThroughOrNothingToSay() {
        assertFalse(ReplyRegistry.reply(context, "whatsapp:name:nobody", "Bonjour"))
        val (action, inputs) = ReplyRegistry.findReplyAction(notification())!!
        ReplyRegistry.put("k", "whatsapp:name:marie", action, inputs)
        assertFalse(ReplyRegistry.reply(context, "whatsapp:name:marie", "   "))
    }

    @Test
    fun aCancelledNotificationIsAFailureAndIsNotOfferedAgain() {
        val pi = pending()
        val icon = Icon.createWithResource(context, R.drawable.ic_launcher_foreground)
        val n = Notification.Builder(context, "t").setSmallIcon(icon)
            .addAction(Notification.Action.Builder(icon, "Répondre", pi).addRemoteInput(RemoteInput.Builder("k").build()).build()).build()
        val (action, inputs) = ReplyRegistry.findReplyAction(n)!!
        ReplyRegistry.put("k", "whatsapp:name:marie", action, inputs)
        pi.cancel()
        assertFalse(ReplyRegistry.reply(context, "whatsapp:name:marie", "Bonjour"))
        assertFalse(ReplyRegistry.canReply("whatsapp:name:marie"))
    }
}
