package app.presence.launcher.messages

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A short trail of what the message plumbing did and why ("notification of WhatsApp: kept", "of Messages: ignored, category …",
 * "SMS copied: 2"), shown to the family in the settings to find out why a message did not arrive. It holds only the app's
 * short name and the outcome: never a name, a number or a word of any message. Kept on the phone, 30 lines at most, erased with the messages.
 */
object MessageDiagnostics {
    private const val FILE = "message_diagnostics"
    private const val KEY = "lines"
    private const val MAX_LINES = 30

    @Synchronized
    fun record(context: Context, what: String, clock: () -> Long = System::currentTimeMillis) {
        val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val line = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE).format(Date(clock())) + "  " + what.take(120)
        val lines = (listOf(line) + stored(prefs.getString(KEY, null))).take(MAX_LINES)
        prefs.edit().putString(KEY, lines.joinToString("\n")).apply()
    }

    /** Newest first. */
    fun lines(context: Context): List<String> =
        stored(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null))

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun stored(raw: String?) = raw?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
}
