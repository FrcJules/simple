package app.simple.launcher.sms

import java.io.ByteArrayOutputStream

/** Builds a real SMS-DELIVER PDU (GSM 7-bit, basic alphabet) so receiver tests go through the platform's own decoder. */
object SmsPdu {
    fun deliver(from: String, text: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x00) // no SMSC
        out.write(0x04) // SMS-DELIVER
        val digits = from.filter { it.isDigit() }
        out.write(digits.length)
        out.write(if (from.startsWith("+")) 0x91 else 0x81)
        digits.padEnd(digits.length + digits.length % 2, 'F').chunked(2).forEach { out.write(("${it[1]}${it[0]}").toInt(16)) }
        out.write(0x00) // PID
        out.write(0x00) // DCS: GSM 7-bit
        listOf(0x62, 0x01, 0x01, 0x12, 0x00, 0x00, 0x00).forEach { out.write(it) } // timestamp, BCD swapped (2026-10-10 21:00)
        val septets = text.map { it.code.also { c -> require(c < 128) { "basic ASCII only: $it" } } }
        out.write(septets.size)
        out.write(pack(septets))
        return out.toByteArray()
    }

    private fun pack(septets: List<Int>): ByteArray {
        val out = ByteArray((septets.size * 7 + 7) / 8)
        septets.forEachIndexed { i, s ->
            val bit = i * 7
            val byte = bit / 8
            val shift = bit % 8
            out[byte] = (out[byte].toInt() or ((s shl shift) and 0xFF)).toByte()
            if (shift > 1) out[byte + 1] = (out[byte + 1].toInt() or (s shr (8 - shift))).toByte()
        }
        return out
    }
}
