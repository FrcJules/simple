package app.presence.launcher.mms

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.mms.pdu_alt.NotifyRespInd
import com.google.android.mms.pdu_alt.PduHeaders
import com.google.android.mms.pdu_alt.PduParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MmsPduTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())

    @Test
    fun readsTheOperatorsNotification() {
        val n = MmsPdu.parseNotification(MmsTestPdus.notification("tx-42", "http://mmsc.example/msg?id=7", size = 123_456))!!
        assertEquals("tx-42", n.transactionId)
        assertEquals("http://mmsc.example/msg?id=7", n.location)
        assertEquals("+33611223344", n.from) // "/TYPE=PLMN" removed
        assertEquals(123_456L, n.sizeBytes)
    }

    @Test
    fun aNotificationWithoutSenderStillWorks() = assertNull(MmsPdu.parseNotification(MmsTestPdus.notification("t", "http://x/y", from = null))?.from)

    @Test
    fun refusesALocationThatIsNotHttp() {
        assertNull(MmsPdu.parseNotification(MmsTestPdus.notification("t", "ftp://x/y")))
        assertNull(MmsPdu.parseNotification(MmsTestPdus.notification("t", "file:///data/data/app/secret")))
        assertNull(MmsPdu.parseNotification(MmsTestPdus.notification("t", "content://sms/inbox")))
    }

    @Test
    fun garbageAndOversizedInputAreRefusedNotCrashed() {
        assertNull(MmsPdu.parseNotification(ByteArray(0)))
        assertNull(MmsPdu.parseNotification(byteArrayOf(1, 2, 3, 4, 5)))
        assertNull(MmsPdu.parseNotification(ByteArray(MmsPdu.MAX_PDU_BYTES + 1)))
        assertNull(MmsPdu.parseRetrieveConf(ByteArray(0)))
        assertNull(MmsPdu.parseRetrieveConf(byteArrayOf(0x8C.toByte(), 0x84.toByte(), 0x8D.toByte())))
        assertNull(MmsPdu.parseRetrieveConf(ByteArray(MmsPdu.MAX_PDU_BYTES + 1)))
        // a notification is not a message
        assertNull(MmsPdu.parseRetrieveConf(MmsTestPdus.notification("t", "http://x/y")))
    }

    @Test
    fun readsTextAndPictures() {
        val pdu = MmsTestPdus.retrieveConf(
            parts = listOf(
                MmsTestPdus.typedPart("application/smil", "<smil/>".toByteArray()),
                MmsTestPdus.typedPart("image/jpeg", jpeg),
                MmsTestPdus.textPart("Regarde comme il a grandi ! é"),
            ),
        )
        val m = MmsPdu.parseRetrieveConf(pdu)!!
        assertEquals("Regarde comme il a grandi ! é", m.text)
        assertEquals("+33611223344", m.from)
        assertEquals(1_791_271_800_000L, m.dateMs)
        assertEquals(1, m.images.size)
        assertEquals("image/jpeg", m.images[0].mime)
        assertArrayEquals(jpeg, m.images[0].bytes)
        assertEquals(0, m.otherParts) // the SMIL layout is neither content nor "other"
    }

    @Test
    fun onlyPicturesAreKeptOtherTypesAreCounted() {
        val m = MmsPdu.parseRetrieveConf(
            MmsTestPdus.retrieveConf(
                parts = listOf(
                    MmsTestPdus.typedPart("video/3gpp", byteArrayOf(1, 2)),
                    MmsTestPdus.typedPart("application/x-shellscript", byteArrayOf(3)),
                    MmsTestPdus.typedPart("text/html", "<script>x</script>".toByteArray()),
                    MmsTestPdus.typedPart("image/png", byteArrayOf(4, 5)),
                ),
            ),
        )!!
        assertEquals(1, m.images.size)
        assertEquals("", m.text) // html is not shown as text
        assertEquals(3, m.otherParts)
    }

    @Test
    fun aMessageWithoutSenderIsStillReadable() {
        val m = MmsPdu.parseRetrieveConf(MmsTestPdus.retrieveConf(from = null, parts = listOf(MmsTestPdus.textPart("Bonjour"))))!!
        assertNull(m.from) // the SMS-level address from the notification is used instead
        assertEquals("Bonjour", m.text)
    }

    @Test
    fun aMessageWithoutTheMandatoryDateIsRefused() =
        assertNull(MmsPdu.parseRetrieveConf(MmsTestPdus.retrieveConf(dateSec = null, parts = listOf(MmsTestPdus.textPart("Bonjour")))))

    @Test
    fun partsBeyondTheLimitsAreIgnored() {
        val many = (1..MmsPdu.MAX_PARTS + 10).map { MmsTestPdus.typedPart("image/jpeg", jpeg) }
        val m = MmsPdu.parseRetrieveConf(MmsTestPdus.retrieveConf(parts = many))!!
        assertEquals(MmsPdu.MAX_IMAGES, m.images.size)
    }

    @Test
    fun theNotifyResponseIsAValidAcknowledgement() {
        val bytes = MmsPdu.notifyResponse(context, "tx-42")
        assertNotNull(bytes)
        val parsed = PduParser(bytes!!, true).parse() as NotifyRespInd
        assertEquals("tx-42", String(parsed.transactionId))
        assertEquals(PduHeaders.STATUS_RETRIEVED, parsed.status)
        assertTrue(bytes.size < 100)
    }

    @Test
    fun toStringNeverShowsContent() {
        val m = MmsPdu.parseRetrieveConf(MmsTestPdus.retrieveConf(parts = listOf(MmsTestPdus.textPart("secret"), MmsTestPdus.typedPart("image/jpeg", jpeg))))!!
        assertEquals("ReceivedMms(images=1, other=0)", m.toString())
    }
}
