package it.samuele.jarvis.brain.commands

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import it.samuele.jarvis.brain.Command
import it.samuele.jarvis.brain.Reply
import it.samuele.jarvis.brain.Utterance
import it.samuele.jarvis.core.Prefs
import it.samuele.jarvis.service.JarvisService
import it.samuele.jarvis.util.Text

/** Timer e sveglie: usa l'app Orologio del telefono (suona anche se Jarvis è spento). */
object TimerCmd : Command {
    override val name = "timer"

    override fun handle(u: Utterance, ctx: Context): Reply? {
        val n = Text.numbers(u.n)
        if (u.has("mostra le sveglie", "quali sveglie", "le mie sveglie")) {
            start(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS))
            return Reply("Ecco le sue sveglie.")
        }
        if (u.has("sveglia", "svegliami")) return alarm(n, ctx)
        if (u.has("timer", "conto alla rovescia", "avvisami tra", "avvisami fra")) return timer(n, ctx)
        return null
    }

    private fun timer(n: String, ctx: Context): Reply {
        var secs = 0
        Regex("(\\d+)\\s*(ore|ora)").find(n)?.let { secs += it.groupValues[1].toInt() * 3600 }
        Regex("(\\d+)\\s*(minuti|minuto)").find(n)?.let { secs += it.groupValues[1].toInt() * 60 }
        Regex("(\\d+)\\s*(secondi|secondo)").find(n)?.let { secs += it.groupValues[1].toInt() }
        if (n.contains("mezz ora") || n.contains("mezzora")) secs += 1800
        if (n.contains("quarto d ora")) secs += 900
        if (secs == 0) return Reply("Di quanto tempo, ${Prefs.title}?", followUp = true)
        val label = describe(secs)
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, secs.coerceAtMost(86400))
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Jarvis")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (!start(ctx, i)) {
            // ripiego: timer interno di Jarvis (funziona finché Jarvis è attivo)
            JarvisService.instance?.schedule(secs * 1000L, "${Prefs.title}, il timer di $label è scaduto.")
        }
        return Reply("Timer impostato: $label.")
    }

    private fun alarm(n: String, ctx: Context): Reply {
        val m = Regex("(?:alle|per le|a le)\\s+(\\d{1,2})(?:\\s*(?:e|:|\\.)\\s*(\\d{1,2}|mezza|1 quarto|quarto))?").find(n)
            ?: Regex("(?:all|a l)\\s+(1)(?:\\s*(?:e)\\s*(\\d{1,2}|mezza|1 quarto|quarto))?").find(n)
            ?: return Reply("A che ora la sveglio, ${Prefs.title}?", followUp = true)
        var h = m.groupValues[1].toInt()
        val minTxt = m.groupValues.getOrElse(2) { "" }
        val min = when {
            minTxt.isEmpty() -> 0
            minTxt == "mezza" -> 30
            minTxt.contains("quarto") -> 15
            else -> minTxt.toIntOrNull() ?: 0
        }
        if (h < 12 && (n.contains("di sera") || n.contains("pomeriggio") || n.contains("stasera"))) h += 12
        if (h > 23 || min > 59) return Reply("Quell'orario non mi sembra valido.")
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, min)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Jarvis")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (!start(ctx, i)) return Reply("Non riesco a impostare la sveglia su questo telefono.")
        val mm = if (min == 0) "" else " e $min"
        return Reply("Sveglia impostata alle $h$mm.")
    }

    private fun describe(secs: Int): String {
        val h = secs / 3600; val m = (secs % 3600) / 60; val s = secs % 60
        val parts = ArrayList<String>()
        if (h > 0) parts.add(if (h == 1) "un'ora" else "$h ore")
        if (m > 0) parts.add(if (m == 1) "un minuto" else "$m minuti")
        if (s > 0) parts.add(if (s == 1) "un secondo" else "$s secondi")
        return parts.joinToString(" e ")
    }

    private fun start(ctx: Context, i: Intent): Boolean = try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) { false }
}
