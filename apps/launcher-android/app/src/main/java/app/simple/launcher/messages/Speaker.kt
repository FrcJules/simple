package app.simple.launcher.messages

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Reads a message aloud, only on explicit request. The engine starts asynchronously: a request made before it is
 * ready is queued instead of being reported as a failure. French voice data may be missing on some phones.
 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private enum class State { STARTING, READY, FAILED }

    private val tts = TextToSpeech(context.applicationContext, this)
    private var state = State.STARTING
    private var pending: String? = null
    private var onUnavailable: (() -> Unit)? = null

    override fun onInit(status: Int) {
        val result = if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.FRENCH) else TextToSpeech.ERROR
        state = if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED || result == TextToSpeech.ERROR) State.FAILED else State.READY
        val text = pending
        val failure = onUnavailable
        pending = null
        onUnavailable = null
        if (text != null) if (state == State.READY) say(text) else failure?.invoke()
    }

    fun speak(text: String, onUnavailable: () -> Unit) {
        when (state) {
            State.READY -> say(text)
            State.STARTING -> {
                pending = text
                this.onUnavailable = onUnavailable
            }
            State.FAILED -> onUnavailable()
        }
    }

    private fun say(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "message")
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.shutdown()
    }
}
