package app.presence.launcher.mms

import android.content.Context
import com.google.android.mms.InvalidHeaderValueException
import com.google.android.mms.pdu_alt.CharacterSets
import com.google.android.mms.pdu_alt.NotificationInd
import com.google.android.mms.pdu_alt.NotifyRespInd
import com.google.android.mms.pdu_alt.PduComposer
import com.google.android.mms.pdu_alt.PduHeaders
import com.google.android.mms.pdu_alt.PduParser
import com.google.android.mms.pdu_alt.RetrieveConf
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

    /** Pictures only: other types are counted, not stored, since we cannot show them safely. */
    private val IMAGE_TYPES = setOf("image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp", "image/bmp")

    /** The `data` extra of WAP_PUSH_DELIVER, or null if it is not an MMS notification. */
    fun parseNotification(pdu: ByteArray): MmsNotification? {
        if (pdu.isEmpty() || pdu.size > MAX_PDU_BYTES) return null
        val parsed = try {
            PduParser(pdu, true).parse()
        } catch (e: RuntimeException) {
            null
        } catch (e: AssertionError) {
            null // the vendored parser asserts on malformed input (a no-op on a phone, but never rely on that)
        }
        val n = parsed as? NotificationInd ?: return null
        val tx = n.transactionId?.toString(Charsets.ISO_8859_1)?.takeIf { it.isNotBlank() } ?: return null
        val location = n.contentLocation?.toString(Charsets.ISO_8859_1)?.takeIf { it.isNotBlank() } ?: return null
        // The location is fetched by the system over the carrier network: refuse anything that is not an http(s) URL.
        if (!location.startsWith("http://", ignoreCase = true) && !location.startsWith("https://", ignoreCase = true)) return null
        return MmsNotification(tx.take(128), location, n.from?.string?.let(::cleanAddress), n.messageSize.coerceAtLeast(0))
    }

    fun parseRetrieveConf(pdu: ByteArray): ReceivedMms? {
        if (pdu.isEmpty() || pdu.size > MAX_PDU_BYTES) return null
        val parsed = try {
            PduParser(pdu, true).parse()
        } catch (e: RuntimeException) {
            null
        } catch (e: AssertionError) {
            null
        }
        val r = parsed as? RetrieveConf ?: return null
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
                mime in IMAGE_TYPES && images.size < MAX_IMAGES -> images += ReceivedImage(mime, data)
                mime == "application/smil" -> Unit // layout description, not content
                else -> other++
            }
        }
        val date = r.date.takeIf { it > 0 }?.times(1000)
        return ReceivedMms(r.from?.string?.let(::cleanAddress), date, text.toString().trim().take(MAX_TEXT_CHARS), images, other)
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

    /** `+33611223344/TYPE=PLMN` → `+33611223344`; an e-mail style address is kept as it is. */
    internal fun cleanAddress(raw: String): String = raw.substringBefore("/TYPE=").trim().take(60)
}
