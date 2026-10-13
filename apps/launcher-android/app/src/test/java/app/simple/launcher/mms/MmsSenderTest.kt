package app.simple.launcher.mms

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.simple.launcher.messages.MessageStatus
import app.simple.launcher.messages.MessageStore
import com.google.android.mms.pdu_alt.PduHeaders
import com.google.android.mms.pdu_alt.PduParser
import com.google.android.mms.pdu_alt.SendReq
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
class MmsSenderTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val dbName = "mms-send-test.db"
    private lateinit var store: MessageStore
    private lateinit var files: MmsFiles
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 4, 5, 6, 0xFF.toByte(), 0xD9.toByte())

    private class Post(val bytes: ByteArray, val subId: Int, val sent: PendingIntent?)

    private val posts = mutableListOf<Post>()
    private var refuse: RuntimeException? = null
    private var default = true
    private var compressed: ByteArray? = jpeg

    private val transport = object : MmsTransport {
        override fun download(location: String, target: Uri, subId: Int, done: PendingIntent) = Unit
        override fun post(pdu: Uri, subId: Int, sent: PendingIntent?) {
            refuse?.let { throw it }
            posts += Post(files.transferFile(pdu.lastPathSegment!!)!!.readBytes(), subId, sent)
        }
    }

    @Before fun setUp() {
        context.deleteDatabase(dbName)
        store = MessageStore(context, dbName, legacyFile = null)
        files = MmsFiles(context)
    }

    @After fun tearDown() { context.deleteDatabase(dbName) }

    private fun sender(now: Long = 1_800_000_000_000L) =
        MmsSender(context, store, files, transport, compress = { compressed }, isDefaultApp = { default }, clock = { now })

    private val photo = Uri.parse("content://media/picker/1")

    @Test
    fun sendsOnePictureToOnePersonAndShowsItBeingSent() {
        val r = sender().send("+33611223344", "Marie", photo, subId = 3) as MmsSendResult.Started
        val m = store.get(r.messageId)!!
        assertTrue(m.mms && m.outgoing)
        assertEquals(MessageStatus.SENDING, m.status)
        assertEquals("Marie", m.from)
        assertEquals("+33611223344", m.address)
        assertTrue(m.read)
        assertArrayEquals(jpeg, File(m.attachments.single().path).readBytes()) // kept for a retry
        assertEquals(1, posts.size)
        assertEquals(3, posts[0].subId)
        assertNotNull(posts[0].sent)
    }

    @Test
    fun theSentPduIsAValidMmsToTheRightRecipientWithThePicture() {
        sender().send("+33611223344", "Marie", photo)
        val req = PduParser(posts.single().bytes, true).parse() as SendReq
        assertEquals("+33611223344", req.to.single().string)
        assertEquals(PduHeaders.MESSAGE_TYPE_SEND_REQ, req.messageType)
        val part = req.body.getPart(0)
        assertEquals("image/jpeg", String(part.contentType))
        assertArrayEquals(jpeg, part.data)
        assertEquals(1, req.body.partsNum)
    }

    @Test
    fun theOperatorsAnswerSetsSentOrNotSentAndRemovesTheTransferFile() {
        val box = sender()
        val id = (box.send("0611223344", "Marie", photo) as MmsSendResult.Started).messageId
        val name = files.writeTransfer(byteArrayOf(1))!!.name
        box.onSent(id, name, ok = true)
        assertEquals(MessageStatus.SENT, store.get(id)!!.status)
        assertFalse(files.transferFile(name)!!.exists())

        val id2 = (box.send("0611223344", "Marie", photo) as MmsSendResult.Started).messageId
        box.onSent(id2, files.writeTransfer(byteArrayOf(1))!!.name, ok = false)
        assertEquals(MessageStatus.FAILED, store.get(id2)!!.status)
    }

    @Test
    fun aLateAnswerNeverOverwritesAFinalStatus() {
        val box = sender()
        val id = (box.send("0611223344", "Marie", photo) as MmsSendResult.Started).messageId
        box.onSent(id, files.writeTransfer(byteArrayOf(1))!!.name, ok = false)
        box.onSent(id, files.writeTransfer(byteArrayOf(1))!!.name, ok = true)
        assertEquals(MessageStatus.FAILED, store.get(id)!!.status)
    }

    @Test
    fun aFailedPictureIsRetriedFromTheCopyKeptWithTheMessage() {
        val box = sender()
        val id = (box.send("0611223344", "Marie", photo) as MmsSendResult.Started).messageId
        box.onSent(id, files.writeTransfer(byteArrayOf(1))!!.name, ok = false)
        compressed = null // the original photo is not needed again
        box.retry(id)
        assertEquals(MessageStatus.SENDING, store.get(id)!!.status)
        assertEquals(2, posts.size)
        val req = PduParser(posts[1].bytes, true).parse() as SendReq
        assertArrayEquals(jpeg, req.body.getPart(0).data)
    }

    @Test
    fun refusesBeforeDoingAnything() {
        default = false
        assertEquals(MmsSendResult.NotDefaultApp, sender().send("0611223344", "M", photo))
        default = true
        assertEquals(MmsSendResult.InvalidNumber, sender().send("12", "M", photo))
        assertEquals(MmsSendResult.InvalidNumber, sender().send("0611,0622", "M", photo))
        compressed = null
        assertEquals(MmsSendResult.Unreadable, sender().send("0611223344", "M", photo))
        assertEquals(0, store.count())
        assertEquals(0, posts.size)
    }

    @Test
    fun thePlatformRefusingIsAVisibleFailureNotACrash() {
        refuse = SecurityException("no")
        val id = (sender().send("0611223344", "Marie", photo) as MmsSendResult.Started).messageId
        assertEquals(MessageStatus.FAILED, store.get(id)!!.status)
    }

    @Test
    fun retryOnlyAppliesToOurOwnFailedPictures() {
        val box = sender()
        box.retry("nope") // unknown id: nothing happens
        assertEquals(0, posts.size)
        val id = (box.send("0611223344", "Marie", photo) as MmsSendResult.Started).messageId
        box.retry(id) // still being sent: not sent a second time
        assertEquals(1, posts.size)
        box.onSent(id, files.writeTransfer(byteArrayOf(1))!!.name, ok = true)
        box.retry(id) // already sent: not sent again
        assertEquals(1, posts.size)
    }

    @Test
    fun theRecipientNumberIsNormalisedForTheOperator() {
        sender().send("+33 (6) 11-22.33.44", "Marie", photo)
        val req = PduParser(posts.single().bytes, true).parse() as SendReq
        assertEquals("+33611223344", req.to.single().string)
    }

    @Test
    fun anyUnexpectedPlatformFailureIsAVisibleFailureAndLeavesNoPduOnDisk() {
        refuse = IllegalStateException("telephony service died")
        val id = (sender().send("0611223344", "Marie", photo) as MmsSendResult.Started).messageId
        assertEquals(MessageStatus.FAILED, store.get(id)!!.status)
        assertEquals(0, File(context.cacheDir, "mms-transfer").listFiles().orEmpty().size)
    }

    @Test
    fun aSendThatNeverGotAnAnswerBecomesNotSent() {
        val id = (sender(now = 1_000L).send("0611223344", "Marie", photo) as MmsSendResult.Started).messageId
        store.expireStaleSends(olderThanMs = 500L)
        assertEquals(MessageStatus.SENDING, store.get(id)!!.status)
        store.expireStaleSends(olderThanMs = 2_000L)
        assertEquals(MessageStatus.FAILED, store.get(id)!!.status)
    }

    @Test
    fun onlyPicturesInOurOwnFolderAreEverRead() {
        val outside = File(context.cacheDir, "secret.txt").also { it.writeText("x") }
        assertNull(files.readPicture(outside.path))
        assertNull(files.readPicture(File(context.filesDir, "mms/../messages.db").path))
    }
}
