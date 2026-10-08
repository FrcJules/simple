package app.presence.launcher.mms

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.presence.launcher.messages.MessageStatus
import app.presence.launcher.messages.MessageStore
import app.presence.launcher.messages.StoredMessage
import app.presence.launcher.notifications.Incoming
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MmsInboxTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val dbName = "mms-test.db"
    private lateinit var store: MessageStore
    private lateinit var files: MmsFiles
    private val transport = FakeMmsTransport()
    private val forwarded = mutableListOf<Incoming>()
    private val notified = mutableListOf<StoredMessage>()
    private var now = 1_800_000_000_000L
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 9, 8, 7, 0xFF.toByte(), 0xD9.toByte())
    private var imageOk: (ReceivedImage) -> Boolean = { true }

    private class FakeMmsTransport : MmsTransport {
        class Download(val location: String, val target: Uri, val subId: Int)
        val downloads = mutableListOf<Download>()
        val posts = mutableListOf<Uri>()
        var refuse: RuntimeException? = null
        override fun download(location: String, target: Uri, subId: Int, done: PendingIntent) {
            refuse?.let { throw it }
            downloads += Download(location, target, subId)
        }
        override fun post(pdu: Uri, subId: Int, sent: PendingIntent?) { posts += pdu }
    }

    @Before fun setUp() {
        context.deleteDatabase(dbName)
        store = MessageStore(context, dbName, legacyFile = null)
        files = MmsFiles(context)
    }

    @After fun tearDown() { context.deleteDatabase(dbName) }

    private fun inbox(contacts: Map<String, String> = mapOf("+33611223344" to "Marie")) = MmsInbox(
        context, store, files, transport, contacts::get, forwarded::add, notified::add,
        unknownSender = "Numéro inconnu", photoLabel = "Photo", imageOk = { imageOk(it) }, clock = { now },
    )

    private fun notification(tx: String = "tx1", from: String? = "+33611223344", size: Long = 40_000) =
        MmsTestPdus.notification(tx, "http://mmsc.example/$tx", from, size)

    /** What the platform does: writes the downloaded message into the file we handed it. */
    private fun platformDownloads(pdu: ByteArray): String {
        val name = transport.downloads.last().target.lastPathSegment!!
        files.transferFile(name)!!.writeBytes(pdu)
        return name
    }

    private fun message(): StoredMessage = store.messages.value.single()
    private val retrieveWithPhoto get() = MmsTestPdus.retrieveConf(parts = listOf(MmsTestPdus.textPart("Regarde !"), MmsTestPdus.typedPart("image/jpeg", jpeg)))

    @Test
    fun anAnnouncementShowsABeingReceivedBubbleAndStartsTheDownload() {
        inbox().onNotification(notification(), subId = 2)
        val m = message()
        assertTrue(m.mms)
        assertFalse(m.outgoing)
        assertEquals(MessageStatus.SENDING, m.status)
        assertEquals("Marie", m.from)
        assertEquals("+33611223344", m.address)
        assertEquals(1, transport.downloads.size)
        assertEquals("http://mmsc.example/tx1", transport.downloads[0].location)
        assertEquals(2, transport.downloads[0].subId)
        assertNotNull(store.pending(m.id))
    }

    @Test
    fun theOperatorRepeatingItsAnnouncementDoesNotDuplicateTheMessage() {
        inbox().onNotification(notification(), -1)
        inbox().onNotification(notification(), -1)
        assertEquals(1, store.count())
        assertEquals(1, transport.downloads.size)
    }

    @Test
    fun aSuccessfulDownloadKeepsTextAndPicturesAcknowledgesAndAnnounces() {
        val box = inbox()
        box.onNotification(notification(), -1)
        val id = message().id
        val name = platformDownloads(retrieveWithPhoto)

        box.onDownloaded(id, name, ok = true)

        val m = message()
        assertEquals(MessageStatus.NONE, m.status)
        assertEquals("Regarde !", m.text)
        assertEquals(1, m.attachments.size)
        assertEquals("image/jpeg", m.attachments[0].mime)
        assertArrayEquals(jpeg, File(m.attachments[0].path).readBytes())
        assertNull(store.pending(id))
        assertEquals(1, transport.posts.size) // the acknowledgement to the operator
        assertEquals(listOf<Incoming>(Incoming.Message("Marie", app.presence.launcher.notifications.Channel.SMS, "Regarde !")), forwarded)
        assertEquals(1, notified.size)
        assertFalse(files.transferFile(name)!!.exists()) // the transfer file is removed
    }

    @Test
    fun aPictureOnlyMessageIsForwardedAsPhotoNeverAsAPicture() {
        val box = inbox()
        box.onNotification(notification(), -1)
        val name = platformDownloads(MmsTestPdus.retrieveConf(parts = listOf(MmsTestPdus.typedPart("image/jpeg", jpeg))))
        box.onDownloaded(message().id, name, ok = true)
        assertEquals(listOf<Incoming>(Incoming.Message("Marie", app.presence.launcher.notifications.Channel.SMS, "Photo")), forwarded)
    }

    @Test
    fun strangersAndSecurityCodesNeverReachTheHomeDisplay() {
        val stranger = inbox(contacts = emptyMap())
        stranger.onNotification(notification("a"), -1)
        stranger.onDownloaded(message().id, platformDownloads(retrieveWithPhoto), ok = true)
        assertTrue(forwarded.isEmpty())
        assertEquals("+33611223344", message().from) // shown by number

        context.deleteDatabase(dbName)
        store = MessageStore(context, dbName, legacyFile = null)
        val box = inbox()
        box.onNotification(notification("b"), -1)
        box.onDownloaded(message().id, platformDownloads(MmsTestPdus.retrieveConf(parts = listOf(MmsTestPdus.textPart("Votre code de confirmation : 482913")))), ok = true)
        assertTrue(message().sensitive)
        assertTrue(forwarded.isEmpty())
    }

    @Test
    fun aFailedDownloadIsVisibleAndCanBeRetried() {
        val box = inbox()
        box.onNotification(notification(), -1)
        val id = message().id
        platformDownloads(ByteArray(0))
        box.onDownloaded(id, transport.downloads.last().target.lastPathSegment!!, ok = false)
        assertEquals(MessageStatus.FAILED, message().status)
        assertNotNull(store.pending(id)) // kept so the person can try again

        box.retry(id)
        assertEquals(MessageStatus.SENDING, message().status)
        assertEquals(2, transport.downloads.size)
        box.onDownloaded(id, platformDownloads(retrieveWithPhoto), ok = true)
        assertEquals(MessageStatus.NONE, message().status)
        assertEquals("Regarde !", message().text)
    }

    @Test
    fun aBrokenDownloadIsNotReceivedRatherThanLost() {
        val box = inbox()
        box.onNotification(notification(), -1)
        box.onDownloaded(message().id, platformDownloads(byteArrayOf(1, 2, 3)), ok = true)
        assertEquals(MessageStatus.FAILED, message().status)
        assertEquals(0, transport.posts.size) // no acknowledgement for what we could not read
    }

    @Test
    fun aMessageTooBigToHandleIsNotDownloaded() {
        inbox().onNotification(notification(size = MmsPdu.MAX_PDU_BYTES + 1L), -1)
        assertEquals(MessageStatus.FAILED, message().status)
        assertEquals(0, transport.downloads.size)
    }

    @Test
    fun theSystemRefusingTheDownloadIsAFailureNotACrash() {
        transport.refuse = SecurityException("no")
        inbox().onNotification(notification(), -1)
        assertEquals(MessageStatus.FAILED, message().status)
    }

    @Test
    fun anUnknownSenderIsLabelled() {
        inbox().onNotification(notification(from = null), -1)
        assertEquals("Numéro inconnu", message().from)
        assertNull(message().address)
    }

    @Test
    fun downloadsThatNeverFinishBecomeNotReceivedAfterAWhile() {
        inbox().onNotification(notification(), -1)
        store.expireStaleDownloads(olderThanMs = now - 1) // not old enough
        assertEquals(MessageStatus.SENDING, message().status)
        store.expireStaleDownloads(olderThanMs = now + 1)
        assertEquals(MessageStatus.FAILED, message().status)
    }

    @Test
    fun onlyOurOwnTransferFileNamesAreAccepted() {
        assertNotNull(files.transferFile("t-" + "a".repeat(32) + ".dat"))
        assertNull(files.transferFile("../files/messages.db"))
        assertNull(files.transferFile("t-abc.dat"))
        assertNull(files.transferFile("t-" + "a".repeat(32) + ".dat/../../x"))
    }

    @Test
    fun erasingTheHistoryAlsoDeletesThePictures() {
        val box = inbox()
        box.onNotification(notification(), -1)
        box.onDownloaded(message().id, platformDownloads(retrieveWithPhoto), ok = true)
        val picture = File(message().attachments.single().path)
        assertTrue(picture.exists())
        store.clear()
        assertFalse(picture.exists())
        assertEquals(0, store.count())
    }

    @Test
    fun aFloodOfAnnouncementsIsCapped() {
        val box = inbox()
        repeat(MmsInbox.MAX_PENDING + 15) { box.onNotification(notification("flood$it"), -1) }
        assertEquals(MmsInbox.MAX_PENDING, store.count())
        assertEquals(MmsInbox.MAX_PENDING, transport.downloads.size)
    }

    @Test
    fun aPictureThatFailsTheDecodeCheckIsNotKept() {
        imageOk = { false }
        val box = inbox()
        box.onNotification(notification(), -1)
        box.onDownloaded(message().id, platformDownloads(retrieveWithPhoto), ok = true)
        assertEquals("Regarde !", message().text) // the text still arrives
        assertTrue(message().attachments.isEmpty())
    }

    @Test
    fun theSenderWrittenInTheDownloadedMessageWinsOverTheForgeableAnnouncement() {
        val box = inbox(contacts = mapOf("+33699999999" to "Paul"))
        box.onNotification(notification(from = "+33611223344"), -1) // the announcement claims Marie's number
        val name = platformDownloads(MmsTestPdus.retrieveConf(from = "+33699999999", parts = listOf(MmsTestPdus.textPart("Salut"))))
        box.onDownloaded(message().id, name, ok = true)
        assertEquals("+33699999999", message().address)
        assertEquals("Paul", message().from)
    }

    @Test
    fun aDownloadFinishingAfterTheHistoryWasErasedLeavesNothingBehind() {
        val box = inbox()
        box.onNotification(notification(), -1)
        val id = message().id
        store.clear() // "Effacer les messages" while the download was running
        // The platform still finishes and drops the whole message into a transfer file.
        val late = files.newTransferFile().also { it.writeBytes(retrieveWithPhoto) }
        box.onDownloaded(id, late.name, ok = true)
        assertEquals(0, store.count())
        assertFalse(late.exists()) // the downloaded PDU (text, photos, numbers) is deleted, not left in the cache
        assertEquals(0, transport.posts.size)
    }

    @Test
    fun erasingTheHistoryRemovesPicturesFoldersAndTransferFiles() {
        val box = inbox()
        box.onNotification(notification(), -1)
        box.onDownloaded(message().id, platformDownloads(retrieveWithPhoto), ok = true)
        val stray = files.savePicture("never-recorded", 0, ReceivedImage("image/jpeg", jpeg))!! // a crash left it without a message
        val leftover = files.newTransferFile()
        store.clear()
        assertFalse(stray.exists())
        assertFalse(stray.parentFile!!.exists())
        assertFalse(leftover.exists())
    }

    @Test
    fun aPictureIsWrittenAtomically() {
        val f = files.savePicture("m", 0, ReceivedImage("image/jpeg", jpeg))!!
        assertEquals("0.jpg", f.name)
        assertTrue(f.parentFile!!.listFiles()!!.none { it.name.endsWith(".tmp") })
    }
}
