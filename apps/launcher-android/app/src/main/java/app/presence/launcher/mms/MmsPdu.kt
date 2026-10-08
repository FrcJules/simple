package app.presence.launcher.mms

import android.content.Context
import com.google.android.mms.InvalidHeaderValueException
import com.google.android.mms.pdu_alt.CharacterSets
import com.google.android.mms.pdu_alt.EncodedStringValue
import com.google.android.mms.pdu_alt.NotificationInd
import com.google.android.mms.pdu_alt.NotifyRespInd
import com.google.android.mms.pdu_alt.PduBody
import com.google.android.mms.pdu_alt.PduComposer
import com.google.android.mms.pdu_alt.PduHeaders
import com.google.android.mms.pdu_alt.PduParser
import com.google.android.mms.pdu_alt.PduPart
import com.google.android.mms.pdu_alt.RetrieveConf
import com.google.android.mms.pdu_alt.SendReq
import app.presence.launcher.messages.TextSafety
import com.google.android.mms.pdu_alt.GenericPdu
import java.io.UnsupportedEncodingException

/** What the operator announces: "an MMS is waiting for you at this address" (M-Notification.ind). */
data class MmsNotification(val transactionId: String, val location: String, val from: String?, val sizeBytes: Long)

class ReceivedImage(val mime: String, val bytes: ByteArray) {
    override fun toString() = "ReceivedImage($mime, ${bytes.size} bytes)" // never the content
}

/** A downloaded MMS (M-Retrieve.conf), reduced to what we show: who, when, the text and the pictures. */
class ReceivedMms(val from: String?, val dateMs: Long?, val text: String, val images: List<ReceivedImage>, val otherParts: Int) {
    override fun toString() = "ReceivedMms(images=${images.size}, other=$otherParts)" // never the content
}

/**
 * Reading and writing the few MMS PDUs we need. Everything here comes from the network, so it is bounded: a hostile or broken
 * message can only be refused, never make us allocate or store without limit.
 */
object MmsPdu {
    const val MAX_PDU_BYTES = 12 * 1024 * 1024
    const val MAX_PART_BYTES = 6 * 1024 * 1024
    const val MAX_PARTS = 16
    const val MAX_TEXT_CHARS = 5_000
    const val MAX_IMAGES = 8

    const val MAX_LOCATION_CHARS = 2_048
    private const val YEAR_2000_MS = 946_684_800_000L

    /** Pictures only: other types are counted, not stored, since we cannot show them safely. */
    private val IMAGE_TYPES = setOf("image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp")

    /** The `data` extra of WAP_PUSH_DELIVER, or null if it is not an MMS notification. */
    fun parseNotification(pdu: ByteArray): MmsNotification? {
        if (pdu.isEmpty() || pdu.size > MAX_PDU_BYTES) return null
        val n = parseSafely(pdu) as? NotificationInd ?: return null
        val tx = n.transactionId?.toString(Charsets.ISO_8859_1)?.takeIf { it.isNotBlank() } ?: return null
        val location = n.contentLocation?.toString(Charsets.ISO_8859_1)?.takeIf { it.isNotBlank() } ?: return null
        if (!isSafeLocation(location)) return null
        return MmsNotification(tx.take(128), location, n.from?.string?.let(::cleanAddress), n.messageSize.coerceAtLeast(0))
    }

    fun parseRetrieveConf(pdu: ByteArray): ReceivedMms? {
        if (pdu.isEmpty() || pdu.size > MAX_PDU_BYTES) return null
        val r = parseSafely(pdu) as? RetrieveConf ?: return null
        val body = r.body
        val text = StringBuilder()
        val images = ArrayList<ReceivedImage>()
        var other = 0
        val parts = minOf(body?.partsNum ?: 0, MAX_PARTS)
        for (i in 0 until parts) {
            val part = body.getPart(i) ?: continue
            val mime = part.contentType?.toString(Charsets.ISO_8859_1)?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            val data = part.data ?: continue
            if (data.size > MAX_PART_BYTES) { other++; continue }
            when {
                mime == "text/plain" -> text.append(decodeText(data, part.charset))
                // The declared type is not trusted: the first bytes must really be that kind of picture.
                mime in IMAGE_TYPES && images.size < MAX_IMAGES && looksLike(mime, data) -> images += ReceivedImage(mime, data)
                mime == "application/smil" -> Unit // layout description, not content
                else -> other++
            }
        }
        // Seconds since 1970: a date outside 2000..(far future) is nonsense and, multiplied, could overflow.
        val date = r.date.takeIf { it in YEAR_2000_MS / 1000..4_102_444_800L }?.times(1000)
        return ReceivedMms(r.from?.string?.let(::cleanAddress), date, TextSafety.clean(text.toString()).trim().take(MAX_TEXT_CHARS), images, other)
    }

    /** The parser works on bytes sent by anyone: whatever it throws, the message is refused and the app lives on. */
    private fun parseSafely(pdu: ByteArray): GenericPdu? =
        try {
            PduParser(pdu, true).parse()
        } catch (e: RuntimeException) {
            null
        } catch (e: AssertionError) {
            null // the vendored parser asserts on malformed input (a no-op on a phone, but never rely on that)
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: StackOverflowError) {
            null
        }

    /**
     * The location is fetched by the system over the carrier's MMS network, and anyone able to send a WAP push can forge it.
     * We refuse what has no business being an MMSC address: credentials in the URL, the phone itself, link-local and IPv6 literals.
     * Private ranges stay allowed because some carriers really run their MMSC there. The MMSC cannot be compared with the APN
     * (reading it is privileged), so this is a limit, not a guarantee (docs/privacy.md).
     */
    internal fun isSafeLocation(location: String): Boolean {
        if (location.length > MAX_LOCATION_CHARS || location.any { it.isWhitespace() || it.isISOControl() }) return false
        val uri = try { java.net.URI(location) } catch (e: java.net.URISyntaxException) { return false }
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return false
        if (uri.rawUserInfo != null) return false
        val host = uri.host?.lowercase() ?: return false
        if (host == "localhost" || host.endsWith(".localhost") || host.startsWith("[")) return false
        val octets = host.split('.').map { it.toIntOrNull() }
        if (octets.size == 4 && octets.all { it != null }) { // an IPv4 literal
            val a = octets[0]!!; val b = octets[1]!!
            if (a == 127 || a == 0 || (a == 169 && b == 254) || a >= 224) return false
        }
        return true
    }

    /** The acknowledgement the operator expects once we got the message; without it some carriers resend it. */
    fun notifyResponse(context: Context, transactionId: String): ByteArray? =
        try {
            PduComposer(context.applicationContext, NotifyRespInd(PduHeaders.CURRENT_MMS_VERSION, transactionId.toByteArray(Charsets.ISO_8859_1), PduHeaders.STATUS_RETRIEVED)).make()
        } catch (e: InvalidHeaderValueException) {
            null
        } catch (e: RuntimeException) {
            null
        }

    /** An M-Send.req carrying one picture (and an optional text) to one recipient. Null if the PDU cannot be built. */
    fun sendRequest(context: Context, to: String, jpeg: ByteArray, text: String = ""): ByteArray? =
        try {
            val req = SendReq()
            req.from = EncodedStringValue(PduHeaders.FROM_INSERT_ADDRESS_TOKEN_STR) // the operator fills in our number
            req.to = arrayOf(EncodedStringValue(to))
            req.date = System.currentTimeMillis() / 1000
            req.messageClass = PduHeaders.MESSAGE_CLASS_PERSONAL_STR.toByteArray()
            req.expiry = 7 * 24 * 3600L
            req.priority = PduHeaders.PRIORITY_NORMAL
            req.deliveryReport = PduHeaders.VALUE_NO
            req.readReport = PduHeaders.VALUE_NO
            val body = PduBody()
            body.addPart(
                PduPart().apply {
                    contentType = "image/jpeg".toByteArray()
                    name = "photo.jpg".toByteArray()
                    contentLocation = "photo.jpg".toByteArray()
                    contentId = "<photo>".toByteArray()
                    data = jpeg
                },
            )
            if (text.isNotBlank()) {
                body.addPart(
                    PduPart().apply {
                        contentType = "text/plain".toByteArray()
                        name = "text.txt".toByteArray()
                        contentLocation = "text.txt".toByteArray()
                        contentId = "<text>".toByteArray()
                        charset = CharacterSets.UTF_8
                        data = text.toByteArray(Charsets.UTF_8)
                    },
                )
            }
            req.body = body
            req.messageSize = jpeg.size.toLong() + text.length
            PduComposer(context.applicationContext, req).make()
        } catch (e: InvalidHeaderValueException) {
            null
        } catch (e: RuntimeException) {
            null
        }

    private fun decodeText(data: ByteArray, mibEnum: Int): String {
        val charset = try {
            // 0 = not stated: operators mostly send UTF-8 in that case.
            if (mibEnum == 0) Charsets.UTF_8 else charset(CharacterSets.getMimeName(mibEnum))
        } catch (e: UnsupportedEncodingException) {
            Charsets.UTF_8
        } catch (e: IllegalArgumentException) {
            Charsets.UTF_8
        }
        return String(data, charset)
    }

    internal fun looksLike(mime: String, d: ByteArray): Boolean = when (mime) {
        "image/jpeg", "image/jpg" -> d.size > 4 && d[0] == 0xFF.toByte() && d[1] == 0xD8.toByte()
        "image/png" -> d.size > 8 && d[0] == 0x89.toByte() && d[1] == 'P'.code.toByte() && d[2] == 'N'.code.toByte() && d[3] == 'G'.code.toByte()
        "image/gif" -> d.size > 6 && d[0] == 'G'.code.toByte() && d[1] == 'I'.code.toByte() && d[2] == 'F'.code.toByte() && d[3] == '8'.code.toByte()
        "image/webp" -> d.size > 12 && String(d, 0, 4, Charsets.ISO_8859_1) == "RIFF" && String(d, 8, 4, Charsets.ISO_8859_1) == "WEBP"
        else -> false
    }

    /** `+33611223344/TYPE=PLMN` → `+33611223344`; an e-mail style address is kept as it is. */
    internal fun cleanAddress(raw: String): String = raw.substringBefore("/TYPE=").trim().take(60)
}
