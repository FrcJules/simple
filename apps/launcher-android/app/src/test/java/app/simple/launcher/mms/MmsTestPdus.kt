package app.simple.launcher.mms

import java.io.ByteArrayOutputStream

/** Hand-built MMS PDUs following OMA-TS-MMS-ENC (WSP encoding), so the vendored parser is checked against the specification. */
object MmsTestPdus {
    private fun ByteArrayOutputStream.text(s: String) {
        write(s.toByteArray(Charsets.ISO_8859_1))
        write(0)
    }

    private fun uintvar(value: Int): ByteArray {
        val groups = ArrayList<Int>()
        var v = value
        do { groups.add(v and 0x7F); v = v shr 7 } while (v > 0)
        return ByteArray(groups.size) { i ->
            val g = groups[groups.size - 1 - i]
            (if (i < groups.size - 1) g or 0x80 else g).toByte()
        }
    }

    private fun longInteger(value: Long): ByteArray {
        val bytes = value.toString(16).let { if (it.length % 2 == 1) "0$it" else it }.chunked(2).map { it.toInt(16).toByte() }
        return byteArrayOf(bytes.size.toByte()) + bytes.toByteArray()
    }

    private fun fromHeader(out: ByteArrayOutputStream, from: String) {
        val inner = ByteArrayOutputStream().apply { write(0x80); text("$from/TYPE=PLMN") }.toByteArray()
        out.write(0x89); out.write(inner.size); out.write(inner)
    }

    /** M-Notification.ind: "an MMS is waiting at [location]". */
    fun notification(tx: String, location: String, from: String? = "+33611223344", size: Long = 45_000): ByteArray =
        ByteArrayOutputStream().apply {
            write(0x8C); write(0x82) // message type: m-notification-ind
            write(0x98); text(tx) // transaction id
            write(0x8D); write(0x90) // MMS version 1.0
            if (from != null) fromHeader(this, from)
            write(0x8A); write(0x80) // message class: personal
            write(0x8E); write(longInteger(size)) // message size
            write(0x88); write(byteArrayOf(0x06, 0x81.toByte(), 0x04, 0x00, 0x09, 0x3A, 0x80.toByte())) // expiry: relative, 604800 s
            write(0x83); text(location) // content location
        }.toByteArray()

    private fun part(contentType: ByteArray, data: ByteArray): ByteArray {
        val headers = contentType
        return ByteArrayOutputStream().apply {
            write(uintvar(headers.size)); write(uintvar(data.size)); write(headers); write(data)
        }.toByteArray()
    }

    fun textPart(text: String): ByteArray {
        val ct = ByteArrayOutputStream().apply { text("text/plain"); write(0x81); write(0xEA) }.toByteArray() // charset = UTF-8 (106)
        return part(byteArrayOf(ct.size.toByte()) + ct, text.toByteArray(Charsets.UTF_8))
    }

    fun typedPart(mime: String, data: ByteArray): ByteArray = part(ByteArrayOutputStream().apply { text(mime) }.toByteArray(), data)

    /** M-Retrieve.conf: the downloaded message. [parts] are encoded with [textPart] / [typedPart]. */
    fun retrieveConf(from: String? = "+33611223344", dateSec: Long? = 1_791_271_800L, parts: List<ByteArray>): ByteArray =
        ByteArrayOutputStream().apply {
            write(0x8C); write(0x84) // message type: m-retrieve-conf
            write(0x8D); write(0x90) // MMS version
            if (dateSec != null) { write(0x85); write(longInteger(dateSec)) }
            if (from != null) fromHeader(this, from)
            write(0x84); write(0xA3) // content type: application/vnd.wap.multipart.mixed
            write(uintvar(parts.size))
            parts.forEach { write(it) }
        }.toByteArray()
}
