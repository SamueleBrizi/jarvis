package it.samuele.jarvis.voice.stt

import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Dopo "Jarvis" ascolta il comando con Vosk (offline, italiano).
 * Si ferma quando l'utente smette di parlare o dopo un tempo massimo.
 */
class CommandListener(model: Model, private val onPartial: (String) -> Unit) {
    private val rec = Recognizer(model, 16000f)
    private var frames = 0
    private var lastPartial = ""
    private var lastChange = 0

    fun begin() {
        rec.reset()
        frames = 0
        lastPartial = ""
        lastChange = 0
    }

    /** null = continua ad ascoltare; "" = non ho sentito nulla; altrimenti il testo */
    fun process(frame: ShortArray): String? {
        frames++
        if (rec.acceptWaveForm(frame, frame.size)) {
            val text = JSONObject(rec.result).optString("text").trim()
            if (text.isNotEmpty()) return text
        } else {
            val p = JSONObject(rec.partialResult).optString("partial").trim()
            if (p != lastPartial) { lastPartial = p; lastChange = frames; onPartial(p) }
        }
        if (lastPartial.isEmpty() && frames > 75) return ""                       // 6 s di silenzio
        if (lastPartial.isNotEmpty() && frames - lastChange > 18) return flush()  // 1,5 s senza parole nuove
        if (frames > 160) return flush()                                           // 13 s massimo
        return null
    }

    private fun flush(): String {
        val t = JSONObject(rec.finalResult).optString("text").trim()
        return t.ifEmpty { lastPartial }
    }

    fun close() { try { rec.close() } catch (_: Exception) {} }
}
