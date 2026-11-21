package it.samuele.jarvis.brain

/**
 * La risposta di Jarvis.
 * speech   = cosa dire
 * followUp = dopo aver parlato resta in ascolto (per una domanda successiva)
 * after    = azione da eseguire dopo aver parlato
 * stream   = risposta dell'IA locale che arriva frase per frase
 */
data class Reply(
    val speech: String,
    val followUp: Boolean = false,
    val after: (() -> Unit)? = null,
    val stream: ((onSentence: (String) -> Unit, onDone: (String) -> Unit) -> Boolean)? = null
)

/** Il comando ricevuto: testo originale e versione normalizzata (minuscolo, senza accenti). */
class Utterance(val raw: String) {
    val n: String = it.samuele.jarvis.util.Text.norm(raw)
    val words: List<String> = n.split(" ")
    fun has(vararg keys: String) = keys.any { n.contains(it) }
    fun word(vararg w: String) = w.any { it in words }
    fun starts(vararg keys: String) = keys.any { n.startsWith(it) }
}

/** Ogni modulo prova a gestire il comando: se non è suo restituisce null. */
interface Command {
    val name: String
    fun handle(u: Utterance, ctx: android.content.Context): Reply?
}
