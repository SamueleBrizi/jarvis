package it.samuele.jarvis.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import it.samuele.jarvis.core.Prefs
import it.samuele.jarvis.util.Text
import java.util.Locale

/** La voce di Jarvis: sintesi vocale di Android in italiano, offline. */
class Speaker(ctx: Context, private val onAllDone: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val active = HashSet<String>()
    private var counter = 0
    @Volatile private var ready = false

    private val tts: TextToSpeech = TextToSpeech(ctx.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            applyVoice()
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) = finished(id)
            @Deprecated("vecchia API") override fun onError(id: String?) = finished(id)
            override fun onError(id: String?, errorCode: Int) = finished(id)
            override fun onStop(id: String?, interrupted: Boolean) = finished(id)
        })
    }

    fun applyVoice() {
        if (!ready) return
        tts.language = Locale.ITALIAN
        tts.setSpeechRate(Prefs.float("ttsRate"))
        tts.setPitch(Prefs.float("ttsPitch"))
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
    }

    private fun finished(id: String?) {
        val empty: Boolean
        synchronized(active) {
            if (id != null) active.remove(id)
            empty = active.isEmpty()
        }
        if (empty) main.post { onAllDone() }
    }

    val isSpeaking: Boolean get() = synchronized(active) { active.isNotEmpty() }

    /** flush = interrompe quello che sta dicendo; altrimenti mette in coda. */
    fun say(text: String, flush: Boolean = true) {
        val clean = Text.forSpeech(text)
        if (!ready || clean.isBlank()) {
            if (!isSpeaking) main.post { onAllDone() }
            return
        }
        val parts = Text.chunks(clean)
        synchronized(active) {
            if (flush) active.clear()
            parts.forEachIndexed { i, p ->
                val id = "u${counter++}"
                active.add(id)
                val mode = if (flush && i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                tts.speak(p, mode, Bundle(), id)
            }
        }
    }

    fun stop() {
        synchronized(active) { active.clear() }
        tts.stop()
        main.post { onAllDone() }
    }

    fun shutdown() {
        try { tts.stop(); tts.shutdown() } catch (_: Exception) {}
    }
}
