package it.samuele.jarvis.brain

import android.content.Context
import it.samuele.jarvis.ai.LocalLlm
import it.samuele.jarvis.brain.commands.*
import it.samuele.jarvis.core.Prefs
import it.samuele.jarvis.web.WebAgent

/**
 * Il cervello: prima prova i comandi a regole (veloci e sicuri), poi l'IA locale,
 * poi una ricerca sul web. L'ordine dei moduli conta.
 */
object Brain {
    private val modules: List<Command> = listOf(
        SystemCmd, TimeCmd, TimerCmd, NotesCmd, DeviceCmd, CallCmd, WebCmd, SmartHomeCmd,
        AppsCmd, WeatherCmd, MathCmd, FunCmd, KnowledgeCmd
    )

    @Volatile var lastSpeech = ""

    fun process(text: String, ctx: Context): Reply {
        val u = Utterance(text)
        if (u.n.isBlank()) return Reply("")

        // "chiedi all'intelligenza artificiale ..." forza l'IA
        val forced = it.samuele.jarvis.util.Text.after(u.n, "chiedi all intelligenza artificiale", "chiedi all ia ", "domanda all ia ")
        if (forced != null && forced.isNotBlank()) return ai(forced, ctx)

        for (m in modules) {
            val r = try { m.handle(u, ctx) } catch (e: Exception) {
                android.util.Log.e("Jarvis", "modulo ${m.name}", e)
                Reply("Il modulo ${m.name} ha avuto un problema: ${e.message ?: "errore"}.")
            }
            if (r != null) return r
        }
        return ai(text, ctx)
    }

    /** Domanda libera: IA locale, altrimenti ricerca web, altrimenti scuse. */
    fun ai(question: String, ctx: Context): Reply {
        if (LocalLlm.ready) {
            return Reply("", followUp = true, stream = { onSentence, onDone ->
                LocalLlm.ask(question, remember = true, onSentence = onSentence, onDone = onDone)
            })
        }
        if (Prefs.bool("webAnswers")) {
            WebCmd.webAnswer(question)?.let { return it }
        }
        return Reply("Mi scusi ${Prefs.title}, non ho capito. Può ripetere?", followUp = true)
    }

    /** Usata dalle ricerche web: l'IA risponde basandosi sul testo trovato. */
    fun aiWithContext(question: String, source: String, text: String): Reply? {
        if (!LocalLlm.ready) return null
        val prompt = "Usa queste informazioni prese dal sito $source per rispondere alla domanda. " +
            "Se le informazioni non bastano, dillo.\n\nINFORMAZIONI:\n${text.take(1800)}\n\nDOMANDA: $question"
        return Reply("", followUp = true, stream = { onSentence, onDone ->
            LocalLlm.ask(prompt, remember = false, onSentence = onSentence, onDone = onDone)
        })
    }

    fun webResultsSummary(): String = WebAgent.lastResults.take(3).mapIndexed { i, r -> "${i + 1}: ${r.title}" }.joinToString(". ")
}
