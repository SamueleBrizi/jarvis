package it.samuele.jarvis.brain.commands

import android.content.Context
import it.samuele.jarvis.brain.Command
import it.samuele.jarvis.brain.Reply
import it.samuele.jarvis.brain.Utterance
import it.samuele.jarvis.core.Prefs
import it.samuele.jarvis.util.Http
import it.samuele.jarvis.util.Text
import org.json.JSONArray
import org.json.JSONObject

/** Casa: comanda Home Assistant (Fase 3). Configura indirizzo e token nelle impostazioni. */
object SmartHomeCmd : Command {
    override val name = "casa"

    private val domains = setOf("light", "switch", "fan", "cover", "climate", "media_player", "scene", "script", "input_boolean", "lock")
    private val words = arrayOf("luce", "luci", "lampada", "lampadina", "presa", "ventilatore", "condizionatore", "clima",
        "tapparella", "tapparelle", "serranda", "tenda", "scena", "televisore", "tv", "riscaldamento", "termosifone", "led", "abat jour")

    override fun handle(u: Utterance, ctx: Context): Reply? {
        val action = when {
            u.starts("accendi", "attiva", "avvia la scena", "apri la tapparella", "apri le tapparelle", "alza") -> "on"
            u.starts("spegni", "disattiva", "chiudi la tapparella", "chiudi le tapparelle", "abbassa la tapparella", "abbassa le tapparelle") -> "off"
            u.starts("cambia", "inverti") -> "toggle"
            else -> null
        } ?: return null
        val isHome = u.has(*words) || u.has("casa", "salotto", "cucina", "camera", "bagno", "ingresso", "giardino")
        val url = Prefs.str("haUrl").trimEnd('/')
        val token = Prefs.str("haToken")
        if (url.isBlank() || token.isBlank()) {
            return if (isHome) Reply("La casa non è ancora collegata, ${Prefs.title}. Inserisca indirizzo e token di Home Assistant nelle impostazioni.") else null
        }
        val target = Text.stripArticles(u.n.substringAfter(" ", "")).trim()
        if (target.isBlank()) return null
        val auth = mapOf("Authorization" to "Bearer $token")
        val states = JSONArray(Http.get("$url/api/states", auth, timeout = 6000))
        var best: JSONObject? = null
        var bestScore = 0f
        val q = Text.norm(target)
        for (i in 0 until states.length()) {
            val e = states.getJSONObject(i)
            val id = e.getString("entity_id")
            if (id.substringBefore('.') !in domains) continue
            val fname = Text.norm(e.optJSONObject("attributes")?.optString("friendly_name") ?: id)
            var s = Text.similar(fname, q)
            if (q.contains(fname) || fname.contains(q)) s = maxOf(s, 0.85f)
            if (s > bestScore) { bestScore = s; best = e }
        }
        val ent = best
        if (ent == null || bestScore < 0.6f) return if (isHome) Reply("Non trovo \"$target\" in casa.") else null
        val id = ent.getString("entity_id")
        val domain = id.substringBefore('.')
        val fname = ent.optJSONObject("attributes")?.optString("friendly_name") ?: id
        val service = when {
            domain == "cover" -> if (action == "on") "open_cover" else if (action == "off") "close_cover" else "toggle"
            domain == "lock" -> if (action == "on") "unlock" else "lock"
            domain == "scene" || domain == "script" -> "turn_on"
            action == "on" -> "turn_on"
            action == "off" -> "turn_off"
            else -> "toggle"
        }
        Http.post("$url/api/services/$domain/$service", JSONObject().put("entity_id", id).toString(), auth, timeout = 6000)
        val verb = when (service) {
            "turn_on" -> "acceso"; "turn_off" -> "spento"; "open_cover" -> "aperto"; "close_cover" -> "chiuso"
            else -> "fatto"
        }
        return Reply("$fname: $verb.")
    }
}
