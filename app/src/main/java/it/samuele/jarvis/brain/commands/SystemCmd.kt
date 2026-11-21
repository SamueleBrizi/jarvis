package it.samuele.jarvis.brain.commands

import android.content.Context
import it.samuele.jarvis.brain.Brain
import it.samuele.jarvis.brain.Command
import it.samuele.jarvis.brain.Reply
import it.samuele.jarvis.brain.Utterance
import it.samuele.jarvis.core.Prefs
import it.samuele.jarvis.service.JarvisService

/** Comandi su Jarvis stesso: stop, grazie, chi sei, aiuto, ripeti, spegniti. */
object SystemCmd : Command {
    override val name = "sistema"

    override fun handle(u: Utterance, ctx: Context): Reply? {
        val t = Prefs.title
        val n = u.n
        if (n in setOf("stop", "basta", "zitto", "silenzio", "annulla", "niente", "lascia stare", "lascia perdere", "fermati", "nulla"))
            return Reply("")
        if (u.has("grazie")) return Reply("Sempre a sua disposizione, $t.")
        if (u.has("chi sei", "come ti chiami", "presentati"))
            return Reply("Sono Jarvis, il suo assistente personale. Funziono interamente su questo telefono, senza servizi a pagamento.")
        if (u.has("cosa sai fare", "cosa puoi fare", "aiuto", "che comandi"))
            return Reply("Posso aprire app, chiamare e scrivere ai contatti, impostare timer e sveglie, prendere note, " +
                "dirle meteo e ora, fare calcoli, cercare e leggere pagine web, controllare la casa con Home Assistant " +
                "e, se ho l'intelligenza artificiale locale, conversare con lei.")
        if (u.has("ripeti", "cosa hai detto"))
            return Reply(Brain.lastSpeech.ifBlank { "Non ho detto ancora nulla, $t." })
        if (u.has("spegniti", "disattivati", "smetti di ascoltare", "vai in pausa", "chiudi jarvis"))
            return Reply("Mi metto in pausa, $t. Riapra l'app quando ha bisogno di me.",
                after = { JarvisService.instance?.shutdown() })
        if (u.has("dimentica la conversazione", "nuova conversazione", "cambiamo argomento")) {
            it.samuele.jarvis.ai.LocalLlm.forget()
            return Reply("Fatto, ricominciamo da capo.", followUp = true)
        }
        return null
    }
}
