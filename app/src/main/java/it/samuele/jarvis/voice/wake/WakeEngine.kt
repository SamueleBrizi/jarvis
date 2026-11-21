package it.samuele.jarvis.voice.wake

/** Riconosce la parola di attivazione. trailingCommand = comando detto subito dopo ("Jarvis, apri YouTube"). */
data class WakeResult(val score: Float, val trailingCommand: String? = null)

interface WakeEngine {
    val label: String
    /** Riceve 80 ms di audio. Restituisce un risultato quando sente "Jarvis". */
    fun process(frame: ShortArray): WakeResult?
    fun reset()
    fun close()
}
