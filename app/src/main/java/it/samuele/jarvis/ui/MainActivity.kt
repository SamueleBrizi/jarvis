package it.samuele.jarvis.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import it.samuele.jarvis.core.Bus
import it.samuele.jarvis.core.Prefs
import it.samuele.jarvis.models.ModelManager
import it.samuele.jarvis.service.JarvisService

/**
 * Schermata principale: mostra l'HUD olografico (HTML/CSS in assets/hud)
 * e fa da ponte con il servizio che ascolta.
 */
class MainActivity : Activity() {
    lateinit var web: WebView
        private set
    private val listener: (String) -> Unit = { json -> push(json) }

    companion object {
        const val REQ_PERMS = 1
        const val REQ_LLM = 2
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyWakeFlags(intent)
        web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#02080F"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            addJavascriptInterface(HudBridge(this@MainActivity), "Android")
        }
        setContentView(web)
        web.loadUrl("file:///android_asset/hud/index.html")
        Bus.on(listener)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyWakeFlags(intent)
    }

    private fun applyWakeFlags(i: Intent?) {
        if (i?.getBooleanExtra("wake", false) == true && Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    /** Chiamato dall'HUD quando la pagina è pronta. */
    fun onHudReady() {
        runOnUiThread {
            for (h in Bus.history()) push(h)
            val auto = intent?.getBooleanExtra("autostart", false) == true || Prefs.bool("autoStart")
            if (auto && hasMic() && ModelManager.voskReady(this) && !JarvisService.running) startJarvis()
            if (intent?.action == Intent.ACTION_ASSIST && hasMic()) JarvisService.send(this, JarvisService.ACTION_LISTEN)
        }
    }

    private fun push(json: String) {
        if (!::web.isInitialized) return
        web.evaluateJavascript("window.JarvisHUD && JarvisHUD.onEvent($json)", null)
    }

    fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun startJarvis() {
        if (!hasMic()) { requestPerms(); return }
        JarvisService.send(this, JarvisService.ACTION_START)
    }

    fun requestPerms() {
        val list = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(list.toTypedArray(), REQ_PERMS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        push("""{"type":"status"}""")
    }

    fun openOverlaySettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    @SuppressLint("BatteryLife")
    fun openBatterySettings() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val i = if (!pm.isIgnoringBatteryOptimizations(packageName))
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        try { startActivity(i) } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    fun openAssistantSettings() {
        try { startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    fun pickLlm() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        @Suppress("DEPRECATION")
        startActivityForResult(i, REQ_LLM)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == REQ_LLM && resultCode == RESULT_OK && uri != null) {
            Thread {
                if (ModelManager.importLlm(this, uri) && JarvisService.running) {
                    JarvisService.send(this, JarvisService.ACTION_RELOAD)
                }
                runOnUiThread { push("""{"type":"status"}""") }
            }.start()
        }
    }

    override fun onResume() {
        super.onResume()
        push("""{"type":"status"}""")
    }

    override fun onDestroy() {
        Bus.off(listener)
        web.destroy()
        super.onDestroy()
    }
}
