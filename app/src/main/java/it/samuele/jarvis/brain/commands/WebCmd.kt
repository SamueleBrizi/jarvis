package it.samuele.jarvis.brain.commands

import android.content.Context
import it.samuele.jarvis.ai.LocalLlm
import it.samuele.jarvis.brain.Brain
import it.samuele.jarvis.brain.Command
import it.samuele.jarvis.brain.Reply
import it.samuele.jarvis.brain.Utterance
import it.samuele.jarvis.core.Bus
import it.samuele.jarvis.core.Prefs
import it.samuele.jarvis.util.Text
import it.samuele.jarvis.web.WebAgent
import it.samuele.jarvis.web.WebController

/**
 * Navigazione web propria:
 *  - "cerca su internet ..." -> Jarvis cerca, apre i risultati, legge e risponde
 *  - "apri il sito repubblica punto it", "mostrami ..." -> browser di Jarvis
 *  - nel browser: "scorri giù", "indietro", "leggi la pagina", "riassumi", "apri il secondo risultato"
 */
object WebCmd : Command {
    override val name = "web"

    private val ordinals = mapOf(
        "primo" to 1, "prima" to 1, "1" to 1, "secondo" to 2, "seconda" to 2, "2" to 2, "terzo" to 3, "terza" to 3, "3" to 3,
        "quarto" to 4, "quarta" to 4, "4" to 4, "quinto" to 5, "quinta" to 5, "5" to 5
    )

    override fun handle(u: Utterance, ctx: Context): Reply? {
        val n = u.n
        val t = Prefs.title

        // --- comandi del browser aperto ---
        if (WebController.isOpen) {
            when {
                n.startsWith("scorri giu") || n in setOf("vai giu", "scendi", "giu", "piu giu", "continua a scorrere") -> { WebController.scroll(true); return Reply("") }
                n.startsWith("scorri su") || n in setOf("vai su", "sali", "su", "piu su") -> { WebController.scroll(false); return Reply("") }
                u.has("torna in cima", "inizio pagina") -> { WebController.top(); return Reply("") }
                n == "indietro" || u.has("torna indietro", "pagina precedente") -> { WebController.back(); return Reply("") }
                n == "avanti" || u.has("pagina successiva") -> { WebController.forward(); return Reply("") }
                u.has("ricarica") -> { WebController.reload(); return Reply("") }
                u.has("chiudi il browser", "chiudi la pagina", "chiudi internet") -> { WebController.close(); return Reply("Chiuso.") }
                u.has("leggi la pagina", "leggimi la pagina", "leggi l articolo", "leggimi l articolo", "leggi questa pagina") -> {
                    val text = WebController.pageText() ?: return Reply("Non riesco a leggere questa pagina.")
                    return Reply(text.take(2500))
                }
                u.has("riassumi", "riassunto", "di cosa parla") -> {
                    val text = WebController.pageText() ?: return Reply("Non riesco a leggere questa pagina.")
                    return summarize(WebController.title(), text)
                }
                u.has("risultato") || (u.has("apri il", "apri la") && ordinals.keys.any { u.word(it) }) -> {
                    val idx = ordinals.entries.firstOrNull { u.word(it.key) }?.value ?: 1
                    val res = WebController.results()
                    if (res.size >= idx) { WebController.load(res[idx - 1].second); return Reply("Apro: ${res[idx - 1].first}.") }
                }
            }
        }

        // --- apri un risultato dell'ultima ricerca fatta a voce ---
        if ((u.has("apri il", "apri la", "mostrami il") && u.has("risultato")) || n == "aprilo" || n == "aprila" || n == "apri" || n == "si" || n == "si aprila" || n == "si aprilo" || u.has("apri la pagina")) {
            val idx = ordinals.entries.firstOrNull { u.word(it.key) }?.value ?: 1
            val r = WebAgent.lastResults.getOrNull(idx - 1)
            if (r != null) {
                WebController.open(ctx, r.url)
                return Reply("Apro ${r.domain}.")
            }
        }

        // --- browser ---
        if (u.has("apri il browser", "apri internet", "apri il navigatore", "apri il web")) {
            WebController.open(ctx, "https://duckduckgo.com/?kl=it-it")
            return Reply("Ecco il browser, $t. Mi dica cosa cercare.", followUp = true)
        }
        val site = siteFrom(n)
        if (site != null) {
            WebController.open(ctx, site)
            return Reply("Apro ${site.removePrefix("https://")}.")
        }
        Text.after(n, "mostrami ", "cerca nel browser ", "cerca su google ", "fammi vedere ")?.let { q ->
            if (q.isNotBlank()) {
                WebController.open(ctx, WebController.searchUrl(q))
                return Reply("Ecco i risultati per $q.")
            }
        }

        // --- ricerca "da solo": cerca, legge e risponde ---
        val q = Regex("^(?:cerca|cercami|trova|trovami)(?: (?:su internet|sul web|online|in rete|in internet))? (?:informazioni su |notizie su )?(.+)$").find(n)?.groupValues?.get(1)
            ?: Text.after(n, "cerca su internet ", "cerca sul web ", "cerca online ", "guarda su internet ", "controlla su internet ")
        if (!q.isNullOrBlank()) {
            if (WebController.isOpen) {
                WebController.load(WebController.searchUrl(q))
                return Reply("Cerco $q.")
            }
            return webAnswer(q) ?: Reply("Non sono riuscito a trovare nulla su $q. Controlli la connessione.")
        }
        if (u.has("ultime notizie", "notizie di oggi", "le notizie")) {
            return webAnswer("ultime notizie oggi Italia")
        }
        return null
    }

    /** Converte "repubblica punto it" / "vai su amazon.it" in un indirizzo. */
    private fun siteFrom(n: String): String? {
        val m = Regex("(?:apri|vai su|vai a|portami su|apri il sito|sito)\\s+(?:il sito\\s+)?(?:di\\s+)?([a-z0-9\\- ]+?)\\s*(?:punto|\\.)\\s*(it|com|org|net|eu|tv|io|gov it)\\b").find(n)
        if (m != null) {
            val name = m.groupValues[1].replace(" ", "")
            val tld = m.groupValues[2].replace(" ", ".")
            return "https://$name.$tld"
        }
        val s = Regex("apri il sito (?:di |del |della )?([a-z0-9 ]+)$").find(n) ?: return null
        val q = s.groupValues[1].trim()
        // nome senza dominio: cerco il sito ufficiale e apro il primo risultato
        val r = WebAgent.search("$q sito ufficiale").firstOrNull() ?: return null
        return r.url
    }

    /** Risposta trovata sul web (con l'IA locale riassume la pagina, senza legge il trafiletto). */
    fun webAnswer(question: String): Reply? {
        Bus.log("sys", "Cerco sul web: $question")
        val found = WebAgent.research(question) ?: return null
        val (res, text) = found
        Bus.card(res.title, res.snippet.ifBlank { text.take(220) }, res.url)
        Brain.aiWithContext(question, res.domain, text)?.let { return it }
        val snippet = res.snippet.ifBlank { Text.firstSentences(text, 2) }
        return Reply("Secondo ${res.domain}: $snippet Vuole che apra la pagina?", followUp = true)
    }

    private fun summarize(title: String, text: String): Reply {
        if (LocalLlm.ready) {
            val prompt = "Riassumi in tre frasi in italiano questa pagina web intitolata \"$title\":\n\n${text.take(2000)}"
            return Reply("", followUp = true, stream = { onSentence, onDone ->
                LocalLlm.ask(prompt, remember = false, onSentence = onSentence, onDone = onDone)
            })
        }
        return Reply("Senza IA locale le leggo l'inizio: " + Text.firstSentences(text, 4))
    }
}
