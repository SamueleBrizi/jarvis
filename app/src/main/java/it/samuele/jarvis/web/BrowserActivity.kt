package it.samuele.jarvis.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import it.samuele.jarvis.service.JarvisService

/** Il browser di Jarvis: una pagina web comandabile a voce ("scorri giù", "leggi la pagina"…). */
class BrowserActivity : Activity() {
    lateinit var webView: WebView
        private set
    private lateinit var bar: TextView
    private lateinit var progress: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cyan = Color.parseColor("#3DE8FF")
        val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt() }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#02080F"))
            fitsSystemWindows = true
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(8), dp(8))
        }
        bar = TextView(this).apply {
            setTextColor(cyan)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            text = "JARVIS · WEB"
        }
        val mic = TextView(this).apply {
            text = "🎙"
            textSize = 20f
            setPadding(dp(14), dp(4), dp(10), dp(4))
            setOnClickListener {
                startService(Intent(this@BrowserActivity, JarvisService::class.java).setAction(JarvisService.ACTION_LISTEN))
            }
        }
        val close = TextView(this).apply {
            text = "✕"
            setTextColor(cyan)
            textSize = 20f
            setPadding(dp(10), dp(4), dp(6), dp(4))
            setOnClickListener { finish() }
        }
        top.addView(bar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(mic)
        top.addView(close)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(cyan)
        }
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val u = request.url.toString()
                    if (u.startsWith("http")) return false
                    // intent:, tel:, mailto:… li apro con l'app giusta
                    try { startActivity(Intent(Intent.ACTION_VIEW, request.url)) } catch (_: Exception) {}
                    return true
                }
                override fun onPageFinished(view: WebView, url: String) {
                    bar.text = (view.title ?: url).uppercase()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress.progress = newProgress
                    progress.visibility = if (newProgress >= 100) android.view.View.INVISIBLE else android.view.View.VISIBLE
                }
            }
        }
        root.addView(top)
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        WebController.attach(this)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        val url = i?.getStringExtra("url")
        if (!url.isNullOrBlank()) webView.loadUrl(url)
        else if (webView.url == null) webView.loadUrl("https://duckduckgo.com/?kl=it-it")
    }

    @Deprecated("gestione del tasto indietro")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        WebController.detach(this)
        webView.destroy()
        super.onDestroy()
    }
}
