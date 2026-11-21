package it.samuele.jarvis.web

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import it.samuele.jarvis.util.Http
import org.json.JSONArray
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Telecomando vocale del browser di Jarvis: apri, cerca, scorri, leggi, indietro… */
object WebController {
    private val main = Handler(Looper.getMainLooper())
    private var activityRef: WeakReference<BrowserActivity>? = null

    fun attach(a: BrowserActivity) { activityRef = WeakReference(a) }
    fun detach(a: BrowserActivity) { if (activityRef?.get() === a) activityRef = null }

    private val web: WebView? get() = activityRef?.get()?.webView
    val isOpen: Boolean get() = activityRef?.get() != null

    fun searchUrl(q: String) = "https://duckduckgo.com/?q=${Http.enc(q)}&kl=it-it&kp=-1"

    /** Apre il browser (anche dallo sfondo, se è concesso "Mostra sopra le altre app"). */
    fun open(ctx: Context, url: String) {
        val i = Intent(ctx, BrowserActivity::class.java)
            .putExtra("url", url)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        ctx.startActivity(i)
    }

    private fun js(code: String, cb: ((String?) -> Unit)? = null) {
        main.post {
            val w = web
            if (w == null) cb?.invoke(null) else w.evaluateJavascript(code) { cb?.invoke(it) }
        }
    }

    /** Esegue JS e aspetta il risultato (max 4 s). Da chiamare fuori dal thread principale. */
    private fun jsBlocking(code: String): String? {
        val latch = CountDownLatch(1)
        var out: String? = null
        js(code) { out = it; latch.countDown() }
        latch.await(4, TimeUnit.SECONDS)
        return out
    }

    private fun decodeJsString(v: String?): String? {
        if (v == null || v == "null") return null
        return try { JSONArray("[$v]").getString(0) } catch (_: Exception) { v }
    }

    fun scroll(down: Boolean) = js("window.scrollBy({top:${if (down) "" else "-"}window.innerHeight*0.8,behavior:'smooth'})")
    fun top() = js("window.scrollTo({top:0,behavior:'smooth'})")
    fun back() = main.post { web?.let { if (it.canGoBack()) it.goBack() } }
    fun forward() = main.post { web?.let { if (it.canGoForward()) it.goForward() } }
    fun reload() = main.post { web?.reload() }
    fun close() = main.post { activityRef?.get()?.finish() }
    fun load(url: String) = main.post { web?.loadUrl(url) }

    fun title(): String = decodeJsString(jsBlocking("document.title")) ?: ""

    /** Il testo principale della pagina aperta. */
    fun pageText(): String? = decodeJsString(jsBlocking("""
        (function(){
          var el = document.querySelector('article') || document.querySelector('main') || document.body;
          var ps = Array.from(el.querySelectorAll('p')).map(function(p){return p.innerText.trim();}).filter(function(t){return t.length>60;});
          var t = ps.length > 2 ? ps.join('\n') : el.innerText;
          return t.replace(/\s+\n/g,'\n').slice(0, 6000);
        })()
    """.trimIndent()))

    /** I risultati della pagina di ricerca aperta: lista (titolo, link). */
    fun results(): List<Pair<String, String>> {
        val raw = jsBlocking("""
            JSON.stringify(Array.from(document.querySelectorAll('a[data-testid="result-title-a"], h2 a, a.result__a'))
              .filter(function(a){return a.href && a.href.indexOf('http')===0;})
              .slice(0,8).map(function(a){return [a.innerText.trim(), a.href];}))
        """.trimIndent())
        val s = decodeJsString(raw) ?: return emptyList()
        return try {
            val arr = JSONArray(s)
            (0 until arr.length()).map { arr.getJSONArray(it).getString(0) to arr.getJSONArray(it).getString(1) }
        } catch (_: Exception) { emptyList() }
    }
}
