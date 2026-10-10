package app.presence.launcher.notifications

enum class Channel(val wire: String) { SMS("sms"), WHATSAPP("whatsapp") }

/** Notification.CATEGORY_MESSAGE is "msg". The few fields of a notification we look at. Nothing else is read (see ADR 0002). */
data class RawNotification(val packageName: String, val title: String?, val text: String?, val category: String?)

sealed interface Incoming {
    data class MissedCall(val from: String) : Incoming
    data class Message(val from: String, val channel: Channel, val text: String) : Incoming
}

/**
 * Turns whitelisted notifications into events. Anything from another app returns null without being inspected.
 * Notification layouts differ per app and per vendor: keep the rules here, covered by tests.
 */
object NotificationParser {
    const val MAX_NAME = 60
    const val MAX_TEXT = 500

    private val dialerPackages = setOf(
        "com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer",
        "com.samsung.android.incallui", "com.android.server.telecom", "com.android.incallui",
    )
    private val smsPackages = setOf("com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms")
    private val whatsappPackages = setOf("com.whatsapp", "com.whatsapp.w4b")
    private val missedKeywords = listOf("appel manqué", "appels manqués", "missed call", "missed calls")
    private val unreadSuffix = Regex("""\s*\(\d+\s+\S+\)\s*$""")

    /** Checked first by the listener so notifications from other apps are never read at all. */
    fun isWatched(packageName: String) =
        packageName in dialerPackages || packageName in smsPackages || packageName in whatsappPackages

    fun parse(n: RawNotification): Incoming? {
        val title = n.title?.trim().orEmpty()
        val text = n.text?.trim().orEmpty()
        return when (n.packageName) {
            in dialerPackages -> missedCall(n.category, title, text)
            in smsPackages -> message(n.category, title, text, Channel.SMS)
            in whatsappPackages ->
                if (title.equals("WhatsApp", ignoreCase = true)) null else message(n.category, title, text, Channel.WHATSAPP)
            else -> null
        }
    }

    /** Why [parse] returned nothing for a watched app, in words for the family's diagnostic (no name, no text). */
    fun rejectionReason(n: RawNotification): String {
        val title = n.title?.trim().orEmpty()
        val text = n.text?.trim().orEmpty()
        return when {
            n.packageName in dialerPackages -> "not a missed call"
            n.packageName in whatsappPackages && title.equals("WhatsApp", ignoreCase = true) -> "WhatsApp summary (no sender)"
            n.category != "msg" -> "category ${knownCategory(n.category)}, not a message"
            title.isEmpty() -> "no sender"
            text.isEmpty() -> "no text"
            SensitiveText.looksSensitive(text) -> "looks like a code (kept private)"
            else -> "not understood"
        }
    }

    /** The category comes from another app: only a closed list of Android's own names is ever written down. */
    private fun knownCategory(category: String?): String = when (category) {
        null, "" -> "empty"
        "call", "email", "event", "msg", "promo", "recommendation", "social", "status", "sys", "transport", "progress", "reminder", "alarm", "err", "service" -> category
        else -> "other"
    }

    private fun isMissedCallLabel(s: String) = missedKeywords.any { s.contains(it, ignoreCase = true) }

    private fun missedCall(category: String?, title: String, text: String): Incoming? {
        val titleIsLabel = isMissedCallLabel(title)
        if (category != "missed_call" && !titleIsLabel && !isMissedCallLabel(text)) return null
        // Dialers put either the label in the title and the caller in the text, or the opposite.
        val name = (if (titleIsLabel) text else title).take(MAX_NAME).trim()
        return if (name.isEmpty()) null else Incoming.MissedCall(name)
    }

    /** Only real conversation messages (category "msg"): skips "sending…", system and promotional notifications. */
    private fun message(category: String?, title: String, text: String, channel: Channel): Incoming? {
        if (category != "msg") return null
        val from = title.replace(unreadSuffix, "").take(MAX_NAME).trim()
        if (from.isEmpty() || text.isEmpty() || SensitiveText.looksSensitive(text)) return null
        return Incoming.Message(from, channel, text.take(MAX_TEXT))
    }
}
