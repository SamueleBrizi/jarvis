package it.samuele.jarvis.web

import android.text.Html
import it.samuele.jarvis.util.Http
import java.net.URI
import java.net.URLDecoder

/**
 * Navigazione "da solo": cerca su DuckDuckGo, apre i risultati e legge il testo
 * delle pagine come farebbe una persona. Nessuna API a pagamento.
 */
object WebAgent {
    data class Result(val title: String, val url: String, val snippet: String) {
        val domain: String get() = try { URI(url).host?.removePrefix("www.") ?: url } catch (_: Exception) { url }
    }

    @Volatile var lastResults: List<Result> = emptyList()
        private set
    @Volatile var lastQuery = ""
        private set

    fun search(query: String): List<Result> {
        lastQuery = query
        var res = try { searchHtml(query) } catch (_: Exception) { emptyList() }
        if (res.isEmpty()) res = try { searchLite(query) } catch (_: Exception) { emptyList() }
        res = res.filter { !it.url.contains("duckduckgo.com/y.js") && it.url.startsWith("http") }
        lastResults = res
        return res
    }

    private fun clean(html: String): String =
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()

    private fun realUrl(href: String): String {
        val h = if (href.startsWith("//")) "https:$href" else href
        val m = Regex("[?&]uddg=([^&]+)").find(h) ?: return h
        return URLDecoder.decode(m.groupValues[1], "UTF-8")
    }

    private fun searchHtml(q: String): List<Result> {
        val html = Http.get("https://html.duckduckgo.com/html/?q=${Http.enc(q)}&kl=it-it")
        val titles = Regex("<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
            .findAll(html).toList()
        val snippets = Regex("class=\"result__snippet\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
            .findAll(html).map { clean(it.groupValues[1]) }.toList()
        return titles.mapIndexed { i, m ->
            Result(clean(m.groupValues[2]), realUrl(Html.fromHtml(m.groupValues[1], Html.FROM_HTML_MODE_LEGACY).toString()),
                snippets.getOrElse(i) { "" })
        }.take(8)
    }

    private fun searchLite(q: String): List<Result> {
        val html = Http.get("https://lite.duckduckgo.com/lite/?q=${Http.enc(q)}&kl=it-it")
        val links = Regex("<a[^>]*href=\"([^\"]+)\"[^>]*class=['\"]result-link['\"][^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
            .findAll(html).toList()
        val snippets = Regex("<td[^>]*class=['\"]result-snippet['\"][^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL)
            .findAll(html).map { clean(it.groupValues[1]) }.toList()
        return links.mapIndexed { i, m ->
            Result(clean(m.groupValues[2]), realUrl(m.groupValues[1]), snippets.getOrElse(i) { "" })
        }.take(8)
    }

    /** Scarica una pagina e tiene solo il testo utile (paragrafi). */
    fun readPage(url: String, maxChars: Int = 4000): String {
        var html = Http.get(url, timeout = 9000, maxBytes = 1_500_000)
        html = html.replace(Regex("<(script|style|nav|header|footer|aside|form|noscript|svg)[^>]*>.*?</\\1>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), " ")
        val paragraphs = Regex("<p[^>]*>(.*?)</p>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(html).map { clean(it.groupValues[1]) }.filter { it.length > 60 }.toList()
        val text = if (paragraphs.isNotEmpty()) paragraphs.joinToString("\n")
        else clean(html.replace(Regex("<[^>]+>"), " "))
        return text.take(maxChars)
    }

    /** Cerca e prova a leggere i primi risultati: restituisce (risultato, testo della pagina). */
    fun research(query: String): Pair<Result, String>? {
        val results = search(query)
        if (results.isEmpty()) return null
        for (r in results.take(3)) {
            if (r.url.contains("youtube.com") || r.url.endsWith(".pdf")) continue
            val text = try { readPage(r.url, 2200) } catch (_: Exception) { "" }
            if (text.length > 200) return r to text
        }
        return results[0] to results[0].snippet
    }
}
