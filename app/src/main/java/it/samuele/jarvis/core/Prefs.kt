package it.samuele.jarvis.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Impostazioni di Jarvis. Ogni voce ha un valore predefinito: il suo tipo
 * (testo, numero, sì/no) decide come viene salvata.
 */
object Prefs {
    private lateinit var sp: SharedPreferences

    val defaults: Map<String, Any> = linkedMapOf(
        "wakeEngine" to "oww",          // "oww" = Hey Jarvis (leggero) | "vosk" = "Jarvis" libero (consuma di più)
        "wakeThreshold" to 0.5f,        // sensibilità openWakeWord (più basso = più sensibile)
        "wakeVariants" to "jarvis, giarvis, jarvi, jervis, gervis, jarvis, giarvi, giaris, charles, jarrys",
        "greeting" to "voce",           // "voce" | "bip"
        "greetingText" to "Eccomi, signore.",
        "userTitle" to "signore",
        "city" to "Roma",
        "followUp" to true,             // dopo una risposta resta in ascolto qualche secondo
        "screenOnWake" to false,        // accende lo schermo quando sente "Jarvis"
        "llmEnabled" to true,
        "llmBackend" to "cpu",          // "cpu" | "gpu"
        "llmFormat" to "auto",          // "auto" | "gemma" | "chatml" | "raw"
        "llmMaxTokens" to 1024,
        "llmName" to "",
        "webAnswers" to true,           // se non sa rispondere cerca sul web
        "haUrl" to "",
        "haToken" to "",
        "ttsRate" to 1.0f,
        "ttsPitch" to 0.9f,
        "autoStart" to false
    )

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
    }

    fun str(key: String): String = sp.getString(key, defaults[key] as? String ?: "") ?: ""
    fun bool(key: String): Boolean = sp.getBoolean(key, defaults[key] as? Boolean ?: false)
    fun int(key: String): Int = sp.getInt(key, defaults[key] as? Int ?: 0)
    fun float(key: String): Float = sp.getFloat(key, defaults[key] as? Float ?: 0f)

    fun put(key: String, value: Any) {
        val e = sp.edit()
        when (value) {
            is Boolean -> e.putBoolean(key, value)
            is Int -> e.putInt(key, value)
            is Float -> e.putFloat(key, value)
            else -> e.putString(key, value.toString())
        }
        e.apply()
    }

    /** Salva un valore arrivato come testo dall'interfaccia, convertendolo nel tipo giusto. */
    fun putFromText(key: String, raw: String) {
        when (defaults[key]) {
            is Boolean -> put(key, raw == "true" || raw == "1")
            is Int -> raw.toFloatOrNull()?.let { put(key, it.toInt()) }
            is Float -> raw.replace(',', '.').toFloatOrNull()?.let { put(key, it) }
            is String -> put(key, raw)
            else -> {}
        }
    }

    fun toJson(): JSONObject {
        val o = JSONObject()
        for ((k, d) in defaults) {
            when (d) {
                is Boolean -> o.put(k, bool(k))
                is Int -> o.put(k, int(k))
                is Float -> o.put(k, float(k).toDouble())
                else -> o.put(k, str(k))
            }
        }
        return o
    }

    // Scorciatoie usate spesso
    val title: String get() = str("userTitle").ifBlank { "signore" }
}
