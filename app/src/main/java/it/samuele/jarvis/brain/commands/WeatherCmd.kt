package it.samuele.jarvis.brain.commands

import android.content.Context
import it.samuele.jarvis.brain.Command
import it.samuele.jarvis.brain.Reply
import it.samuele.jarvis.brain.Utterance
import it.samuele.jarvis.core.Prefs
import it.samuele.jarvis.util.Http
import org.json.JSONObject
import kotlin.math.roundToInt

/** Meteo gratuito con Open-Meteo (nessuna chiave). */
object WeatherCmd : Command {
    override val name = "meteo"

    override fun handle(u: Utterance, ctx: Context): Reply? {
        if (!u.has("meteo", "che tempo", "piovera", "piove", "temperatura", "fa freddo", "fa caldo", "nevica", "ombrello", "previsioni")) return null
        if (u.has("in casa", "del frigo", "della stanza")) return null // lo lascio alla domotica
        val city = Regex("(?:a|ad|di|su|per|in) ([a-z][a-z ]+?)(?: domani| oggi| stasera| dopodomani)?$").find(u.n)
            ?.groupValues?.get(1)?.trim()
            ?.takeIf { it !in setOf("oggi", "domani", "casa", "stasera", "fuori") }
            ?: Prefs.str("city")
        val day = when {
            u.has("dopodomani") -> 2
            u.has("domani") -> 1
            else -> 0
        }
        val geo = JSONObject(Http.get("https://geocoding-api.open-meteo.com/v1/search?name=${Http.enc(city)}&count=1&language=it"))
        val place = geo.optJSONArray("results")?.optJSONObject(0)
            ?: return Reply("Non trovo la città $city, ${Prefs.title}.")
        val lat = place.getDouble("latitude")
        val lon = place.getDouble("longitude")
        val name = place.optString("name", city)
        val w = JSONObject(Http.get(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=3"
        ))
        val d = w.getJSONObject("daily")
        val max = d.getJSONArray("temperature_2m_max").getDouble(day).roundToInt()
        val min = d.getJSONArray("temperature_2m_min").getDouble(day).roundToInt()
        val rain = d.optJSONArray("precipitation_probability_max")?.optInt(day, -1) ?: -1
        val rainTxt = if (rain >= 0) " Probabilità di pioggia $rain percento." else ""
        if (day == 0) {
            val c = w.getJSONObject("current")
            val now = c.getDouble("temperature_2m").roundToInt()
            val desc = describe(c.getInt("weather_code"))
            return Reply("A $name ora ci sono $now gradi, $desc. Oggi minima $min e massima $max.$rainTxt")
        }
        val desc = describe(d.getJSONArray("weather_code").getInt(day))
        val q = if (day == 1) "Domani" else "Dopodomani"
        return Reply("$q a $name: $desc, tra $min e $max gradi.$rainTxt")
    }

    private fun describe(code: Int): String = when (code) {
        0 -> "cielo sereno"
        1 -> "prevalentemente sereno"
        2 -> "parzialmente nuvoloso"
        3 -> "coperto"
        45, 48 -> "nebbia"
        51, 53, 55 -> "pioviggine"
        56, 57 -> "pioviggine gelata"
        61 -> "pioggia debole"
        63 -> "pioggia"
        65 -> "pioggia forte"
        66, 67 -> "pioggia gelata"
        71, 73, 75, 77 -> "neve"
        80, 81 -> "rovesci"
        82 -> "rovesci violenti"
        85, 86 -> "rovesci di neve"
        95 -> "temporale"
        96, 99 -> "temporale con grandine"
        else -> "condizioni variabili"
    }
}
