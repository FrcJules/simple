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
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0)

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
                    MmsTestPdus.typedPart("image/png", png),
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

    @Test
    fun locationsThatCannotBeAnMmscAreRefused() {
        listOf(
            "http://localhost/x", "http://127.0.0.1/x", "http://0.0.0.0/x", "http://169.254.169.254/latest/meta-data",
            "http://user:pass@mmsc.example/x", "http://[::1]/x", "https://mmsc.example/x y", "ftp://mmsc.example/x",
            "http://" + "a".repeat(MmsPdu.MAX_LOCATION_CHARS) + ".example/x",
        ).forEach { assertNull("$it must be refused", MmsPdu.parseNotification(MmsTestPdus.notification("t", it))) }
    }

    @Test
    fun ordinaryOperatorLocationsAreAccepted() {
        listOf("http://mms.operator.fr/m?id=1", "https://mmsc.example.com:8443/x", "http://10.12.0.5:8080/mms/42").forEach {
            assertEquals(it, MmsPdu.parseNotification(MmsTestPdus.notification("t", it))?.location)
        }
    }

    @Test
    fun aPictureWhoseBytesDoNotMatchItsDeclaredTypeIsDropped() {
        val m = MmsPdu.parseRetrieveConf(
            MmsTestPdus.retrieveConf(
                parts = listOf(
                    MmsTestPdus.typedPart("image/jpeg", byteArrayOf(1, 2, 3, 4, 5, 6)), // not a JPEG
                    MmsTestPdus.typedPart("image/png", jpeg), // a JPEG labelled PNG
                    MmsTestPdus.typedPart("image/jpeg", jpeg),
                ),
            ),
        )!!
        assertEquals(1, m.images.size)
        assertEquals("image/jpeg", m.images[0].mime)
    }

    @Test
    fun bmpIsNoLongerAccepted() {
        val bmp = "BM".toByteArray() + ByteArray(30)
        assertEquals(0, MmsPdu.parseRetrieveConf(MmsTestPdus.retrieveConf(parts = listOf(MmsTestPdus.typedPart("image/bmp", bmp))))!!.images.size)
    }

    @Test
    fun anAbsurdDateIsIgnoredInsteadOfOverflowing() {
        val far = MmsPdu.parseRetrieveConf(MmsTestPdus.retrieveConf(dateSec = Long.MAX_VALUE / 500, parts = listOf(MmsTestPdus.textPart("x"))))
        assertNull(far?.dateMs)
        val old = MmsPdu.parseRetrieveConf(MmsTestPdus.retrieveConf(dateSec = 1_000L, parts = listOf(MmsTestPdus.textPart("x"))))
        assertNull(old?.dateMs)
    }

    @Test
    fun hostileTextHasItsControlAndBidiCharactersRemoved() {
        val m = MmsPdu.parseRetrieveConf(MmsTestPdus.retrieveConf(parts = listOf(MmsTestPdus.textPart("Bonjour\u202E ligne\u0007\nsuite\u200B"))))!!
        assertEquals("Bonjour ligne\nsuite", m.text)
    }

    @Test
    fun aHugeAnnouncedPartLengthIsRefusedWithoutAllocating() {
        // 1 part whose header says it has 2 GB of data, in a 40-byte message.
        val evil = byteArrayOf(0x8C.toByte(), 0x84.toByte(), 0x8D.toByte(), 0x90.toByte(), 0x85.toByte(), 0x04, 0x6B, 0x2A, 0x6D, 0x38,
            0x84.toByte(), 0xA3.toByte(), 0x01, 0x04, 0x87.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F, 0x74, 0x65, 0x78, 0x74)
        assertNull(MmsPdu.parseRetrieveConf(evil))
    }

    @Test
    fun deeplyNestedAlternativesAreRefusedNotACrash() {
        var inner = MmsTestPdus.typedPart("image/jpeg", jpeg)
        repeat(30) { inner = MmsTestPdus.typedPart("application/vnd.wap.multipart.alternative", byteArrayOf(1) + inner) }
        assertNull(MmsPdu.parseRetrieveConf(MmsTestPdus.retrieveConf(parts = listOf(inner)))?.takeIf { it.images.isNotEmpty() })
    }
}
