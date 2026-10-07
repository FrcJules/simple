package app.presence.launcher.messages

import app.presence.launcher.notifications.Channel

/**
 * Groups messages into one conversation per person. `0612345678` and `+33 6 12 34 56 78` are the same person, so numbers
 * are compared on their last 9 digits. Alphanumeric sender names ("AMAZON") and short codes ("36105") keep their own
 * thread, and a WhatsApp conversation never merges with an SMS one: replying would silently change channel.
 */
object ThreadKey {
    fun of(channel: Channel, address: String?, name: String): String {
        if (channel == Channel.WHATSAPP || address.isNullOrBlank()) return "${channel.wire}:name:${name.trim().lowercase()}"
        val a = address.trim()
        if (a.any { it.isLetter() }) return "sms:alpha:${a.lowercase()}"
        val digits = a.filter { it.isDigit() }
        return if (digits.length >= 9) "sms:num:${digits.takeLast(9)}" else "sms:short:$digits"
    }
}
