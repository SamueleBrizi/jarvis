package it.samuele.jarvis.voice.wake

import it.samuele.jarvis.util.Text
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Alternativa: ascolta tutto in italiano con Vosk e reagisce a "Jarvis" (e alle varianti
 * con cui Vosk lo scrive). Funziona anche con "Jarvis" da solo e capisce il comando
 * detto di seguito ("Jarvis apri YouTube"). Consuma più batteria di openWakeWord.
 */
class VoskWakeEngine(model: Model, variantsCsv: String) : WakeEngine {
    override val label = "Jarvis (Vosk, ascolto libero)"

    private val rec = Recognizer(model, 16000f)
    private val variants = variantsCsv.split(",").map { Text.norm(it) }.filter { it.isNotBlank() }.toSet()
    private var armedFrames = -1

    private fun wakeIndex(tokens: List<String>): Int =
        tokens.indexOfFirst { tok -> tok in variants || Text.similar(tok, "jarvis") >= 0.75f }

    override fun process(frame: ShortArray): WakeResult? {
        val isFinal = rec.acceptWaveForm(frame, frame.size)
        if (isFinal) {
            val text = JSONObject(rec.result).optString("text")
            val r = check(text)
            armedFrames = -1
            return r
        }
        val partial = JSONObject(rec.partialResult).optString("partial")
        if (armedFrames < 0 && wakeIndex(Text.norm(partial).split(" ")) >= 0) armedFrames = 0
        if (armedFrames >= 0) {
            armedFrames++
            // l'utente ha detto "Jarvis" ma non fa pausa: dopo 2,5 s chiudo la frase
            if (armedFrames > 31) {
                armedFrames = -1
                return check(JSONObject(rec.finalResult).optString("text"))
            }
        }
        return null
    }

    private fun check(text: String): WakeResult? {
        val tokens = Text.norm(text).split(" ")
        val i = wakeIndex(tokens)
        if (i < 0) return null
        val trailing = tokens.drop(i + 1).joinToString(" ").trim()
        return WakeResult(1f, trailing.ifBlank { null })
    }

    override fun reset() { rec.reset(); armedFrames = -1 }
    override fun close() { try { rec.close() } catch (_: Exception) {} }
}
