package it.samuele.jarvis.util

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

/** Strumenti per il testo: normalizzazione, somiglianza tra parole, numeri scritti in lettere. */
object Text {

    /** minuscolo, senza accenti né punteggiatura, spazi singoli */
    fun norm(s: String): String {
        val noAcc = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return noAcc.replace("'", " ").replace(Regex("[^a-z0-9.,:+\\-*/%\\s]"), " ")
            .replace(Regex("\\s+"), " ").trim()
    }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** 1.0 = uguali, 0.0 = diversissime */
    fun similar(a: String, b: String): Float {
        val m = max(a.length, b.length)
        if (m == 0) return 1f
        return 1f - levenshtein(a, b).toFloat() / m
    }

    fun containsAny(text: String, vararg words: String): Boolean = words.any { text.contains(it) }

    /** Il testo dopo la prima parola chiave trovata, oppure null. */
    fun after(text: String, vararg keys: String): String? {
        for (k in keys) {
            val i = text.indexOf(k)
            if (i >= 0) return text.substring(i + k.length).trim()
        }
        return null
    }

    fun stripArticles(s: String): String =
        s.replace(Regex("^(il|lo|la|i|gli|le|l|un|uno|una|del|della|dello|dei|delle|al|alla|allo|ai|alle|su|di|a)\\s+"), "").trim()

    // ---------- numeri in lettere (Vosk scrive "ventitre", "cinque", "trentacinque") ----------
    private val small = mapOf(
        "zero" to 0, "uno" to 1, "un" to 1, "una" to 1, "due" to 2, "tre" to 3, "quattro" to 4,
        "cinque" to 5, "sei" to 6, "sette" to 7, "otto" to 8, "nove" to 9, "dieci" to 10,
        "undici" to 11, "dodici" to 12, "tredici" to 13, "quattordici" to 14, "quindici" to 15,
        "sedici" to 16, "diciassette" to 17, "diciotto" to 18, "diciannove" to 19
    )
    private val tens = listOf(
        "venti" to 20, "vent" to 20, "trenta" to 30, "trent" to 30, "quaranta" to 40, "quarant" to 40,
        "cinquanta" to 50, "cinquant" to 50, "sessanta" to 60, "sessant" to 60, "settanta" to 70,
        "settant" to 70, "ottanta" to 80, "ottant" to 80, "novanta" to 90, "novant" to 90
    )

    fun parseNumber(w: String): Int? {
        if (w.isEmpty()) return null
        w.toIntOrNull()?.let { return it }
        small[w]?.let { return it }
        if (w.startsWith("mille")) return rest(w.substring(5))?.let { 1000 + it }
        val mila = w.indexOf("mila")
        if (mila > 0) {
            val head = parseNumber(w.substring(0, mila)) ?: return null
            val tail = rest(w.substring(mila + 4)) ?: return null
            return head * 1000 + tail
        }
        if (w.startsWith("cento")) return rest(w.substring(5))?.let { 100 + it }
        val cento = w.indexOf("cento")
        if (cento > 0) {
            val head = small[w.substring(0, cento)] ?: return null
            val tail = rest(w.substring(cento + 5)) ?: return null
            return head * 100 + tail
        }
        for ((t, v) in tens) {
            if (w.startsWith(t)) {
                val tail = rest(w.substring(t.length)) ?: continue
                if (tail < 10) return v + tail
            }
        }
        return null
    }

    private fun rest(s: String): Int? = if (s.isEmpty()) 0 else parseNumber(s)

    /** "imposta un timer di cinque minuti" -> "imposta 1 timer di 5 minuti" (solo parole-numero) */
    fun numbers(text: String): String =
        text.split(" ").joinToString(" ") { w -> parseNumber(w)?.toString() ?: w }

    /** Spezza un testo lungo in pezzi adatti alla sintesi vocale. */
    fun chunks(text: String, maxLen: Int = 350): List<String> {
        val sentences = text.split(Regex("(?<=[.!?;:])\\s+"))
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for (s in sentences) {
            if (sb.length + s.length > maxLen && sb.isNotEmpty()) { out.add(sb.toString().trim()); sb.clear() }
            if (s.length > maxLen) {
                s.chunked(maxLen).forEach { out.add(it) }
            } else sb.append(s).append(' ')
        }
        if (sb.isNotBlank()) out.add(sb.toString().trim())
        return out
    }

    /** Toglie markdown, emoji e simboli che la voce leggerebbe male. */
    fun forSpeech(s: String): String =
        s.replace(Regex("[*#_`>|~]"), " ")
            .replace(Regex("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}]"), "")
            .replace(Regex("\\s+"), " ").trim()

    fun firstSentences(s: String, n: Int): String =
        s.split(Regex("(?<=[.!?])\\s+")).take(n).joinToString(" ").trim()
}
