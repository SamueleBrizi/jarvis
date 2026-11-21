package it.samuele.jarvis.brain.commands

import android.content.Context
import android.content.Intent
import it.samuele.jarvis.brain.Command
import it.samuele.jarvis.brain.Reply
import it.samuele.jarvis.brain.Utterance
import it.samuele.jarvis.core.Prefs
import it.samuele.jarvis.util.Text

/** Apre direttamente le app installate: "apri WhatsApp", "avvia Spotify". */
object AppsCmd : Command {
    override val name = "app"

    private data class App(val label: String, val norm: String, val pkg: String)
    private var cache: List<App> = emptyList()
    private var cacheTime = 0L

    // come Vosk scrive spesso i nomi stranieri -> nome dell'app
    private val aliases = mapOf(
        "uozzap" to "whatsapp", "whats app" to "whatsapp", "uotsapp" to "whatsapp", "wazzap" to "whatsapp",
        "you tube" to "youtube", "iutub" to "youtube", "iu tub" to "youtube", "spotifai" to "spotify",
        "instagram" to "instagram", "insta" to "instagram", "feisbuc" to "facebook", "face book" to "facebook",
        "tic toc" to "tiktok", "tik tok" to "tiktok", "telegram" to "telegram", "gmail" to "gmail",
        "mappe" to "maps", "google maps" to "maps", "navigatore" to "maps", "play store" to "play store",
        "netflix" to "netflix", "galleria" to "galleria", "foto" to "galleria", "impostazioni" to "impostazioni",
        "calcolatrice" to "calcolatrice", "orologio" to "orologio", "calendario" to "calendario",
        "messaggi" to "messaggi", "telefono" to "telefono", "rubrica" to "contatti", "chrome" to "chrome",
        "amazon" to "amazon", "musica" to "spotify"
    )

    private fun apps(ctx: Context): List<App> {
        if (cache.isNotEmpty() && System.currentTimeMillis() - cacheTime < 10 * 60_000) return cache
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        cache = pm.queryIntentActivities(i, 0).map {
            val label = it.loadLabel(pm).toString()
            App(label, Text.norm(label), it.activityInfo.packageName)
        }.distinctBy { it.pkg }
        cacheTime = System.currentTimeMillis()
        return cache
    }

    override fun handle(u: Utterance, ctx: Context): Reply? {
        val m = Regex("^(?:apri|avvia|lancia|fai partire|apri l app|apri l applicazione|metti|accedi a)\\s+(.+)$").find(u.n) ?: return null
        var name = Text.stripArticles(m.groupValues[1].removePrefix("app ").removePrefix("applicazione "))
        aliases[name]?.let { name = it }
        val list = apps(ctx)
        val compact = name.replace(" ", "")
        var best: App? = null
        var bestScore = 0f
        for (a in list) {
            val ac = a.norm.replace(" ", "")
            var s = Text.similar(ac, compact)
            if (a.norm == name || ac == compact) s = 1.1f
            else if (a.norm.startsWith(name) || ac.startsWith(compact)) s = maxOf(s, 0.9f)
            else if (a.norm.split(" ").contains(name)) s = maxOf(s, 0.85f)
            if (s > bestScore) { bestScore = s; best = a }
        }
        val app = best
        if (app == null || bestScore < 0.7f) {
            // se non è un'app potrebbe essere una pagina web: lo lascio agli altri moduli
            return if (u.starts("apri", "avvia", "lancia")) Reply("Non trovo l'app $name, ${Prefs.title}.") else null
        }
        val launch = ctx.packageManager.getLaunchIntentForPackage(app.pkg) ?: return Reply("Non riesco ad aprire ${app.label}.")
        return try {
            ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Reply("Apro ${app.label}.")
        } catch (e: Exception) {
            Reply("Android non mi lascia aprire ${app.label} dallo sfondo: attivi il permesso \"Mostra sopra le altre app\".")
        }
    }
}
