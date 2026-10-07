package app.presence.launcher.messages

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Reads a message aloud. French voice data may be missing on some phones: [speak] then returns false. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    @Volatile private var ready = false

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val result = tts.setLanguage(Locale.FRENCH)
        ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun speak(text: String): Boolean {
        if (!ready) return false
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "message")
        return true
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.shutdown()
    }
}
