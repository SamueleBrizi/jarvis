package it.samuele.jarvis.brain.commands

import android.content.Context
import it.samuele.jarvis.brain.Command
import it.samuele.jarvis.brain.Reply
import it.samuele.jarvis.brain.Utterance
import it.samuele.jarvis.core.Prefs
import java.util.Calendar
import kotlin.random.Random

/** Saluti, battute, moneta, dado: la personalità di Jarvis. */
object FunCmd : Command {
    override val name = "svago"

    private val jokes = listOf(
        "Perché il computer va dal dottore? Perché ha un virus. Io per fortuna mi aggiorno da solo.",
        "Qual è il colmo per un elettricista? Non fare mai scintille con la fidanzata.",
        "Sa perché i fantasmi sono pessimi bugiardi? Perché si vede attraverso di loro.",
        "Cosa fa un'ape in palestra? Lo zum-ba.",
        "Qual è il colmo per un giardiniere? Avere un pollice verde e uno nero... di rabbia."
    )

    override fun handle(u: Utterance, ctx: Context): Reply? {
        val t = Prefs.title
        val n = u.n
        if (n in setOf("ciao", "ehi", "ciao jarvis", "salve", "buongiorno", "buonasera", "buon pomeriggio", "hey")) {
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val g = when (h) { in 5..12 -> "Buongiorno"; in 13..17 -> "Buon pomeriggio"; else -> "Buonasera" }
            return Reply("$g, $t. Come posso aiutarla?", followUp = true)
        }
        if (u.has("buonanotte")) return Reply("Buonanotte, $t. Resto di guardia.")
        if (u.has("come stai", "come va")) return Reply("Tutti i sistemi operativi, $t. E lei come sta?", followUp = true)
        if (u.has("barzelletta", "battuta", "fammi ridere")) return Reply(jokes.random())
        if (u.has("lancia una moneta", "testa o croce")) return Reply(if (Random.nextBoolean()) "Testa." else "Croce.")
        if (u.has("tira un dado", "lancia un dado")) return Reply("È uscito ${Random.nextInt(1, 7)}.")
        if (u.has("numero a caso", "numero casuale")) return Reply("Il numero è ${Random.nextInt(1, 101)}.")
        if (u.has("ti voglio bene", "sei il migliore", "bravo")) return Reply("Troppo gentile, $t. Faccio solo il mio lavoro.")
        if (u.has("sei sveglio", "ci sei", "mi senti")) return Reply("Sempre, $t.", followUp = true)
        return null
    }
}
