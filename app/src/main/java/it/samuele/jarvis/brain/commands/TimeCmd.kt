package it.samuele.jarvis.brain.commands

import android.content.Context
import it.samuele.jarvis.brain.Command
import it.samuele.jarvis.brain.Reply
import it.samuele.jarvis.brain.Utterance
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Ora e data. */
object TimeCmd : Command {
    override val name = "ora"
    private val loc = Locale.ITALIAN

    override fun handle(u: Utterance, ctx: Context): Reply? {
        if (u.has("che ore sono", "che ora e", "dimmi l ora", "che ora sono", "sai che ore")) {
            val c = Calendar.getInstance()
            val h = c.get(Calendar.HOUR_OF_DAY)
            val m = c.get(Calendar.MINUTE)
            val ore = when (h) { 0 -> "È mezzanotte"; 1 -> "È l'una"; 12 -> "È mezzogiorno"; else -> "Sono le $h" }
            return Reply(if (m == 0) "$ore in punto." else "$ore e $m.")
        }
        if (u.has("che giorno e", "che giorno siamo", "che data e", "quanti ne abbiamo", "data di oggi")) {
            val s = SimpleDateFormat("EEEE d MMMM yyyy", loc).format(Date())
            return Reply("Oggi è $s.")
        }
        if (u.has("che giorno sara domani", "domani che giorno")) {
            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            return Reply("Domani è " + SimpleDateFormat("EEEE d MMMM", loc).format(c.time) + ".")
        }
        return null
    }
}
