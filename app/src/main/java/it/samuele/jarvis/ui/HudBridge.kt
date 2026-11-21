package it.samuele.jarvis.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import android.webkit.JavascriptInterface
import it.samuele.jarvis.ai.LocalLlm
import it.samuele.jarvis.brain.commands.NotesCmd
import it.samuele.jarvis.core.Bus
import it.samuele.jarvis.core.Prefs
import it.samuele.jarvis.models.ModelManager
import it.samuele.jarvis.service.JarvisService
import it.samuele.jarvis.web.WebController
import org.json.JSONArray
import org.json.JSONObject

/** Funzioni che l'HUD (JavaScript) può chiamare: window.Android.xxx() */
class HudBridge(private val a: MainActivity) {

    private fun perm(p: String) = a.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    @JavascriptInterface fun ready() = a.onHudReady()

    @JavascriptInterface
    fun getStatus(): String {
        val pm = a.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        val perms = JSONObject()
            .put("mic", perm(Manifest.permission.RECORD_AUDIO))
            .put("contacts", perm(Manifest.permission.READ_CONTACTS))
            .put("call", perm(Manifest.permission.CALL_PHONE))
            .put("notif", if (android.os.Build.VERSION.SDK_INT >= 33) perm(Manifest.permission.POST_NOTIFICATIONS) else true)
            .put("overlay", Settings.canDrawOverlays(a))
            .put("battery", pm.isIgnoringBatteryOptimizations(a.packageName))
        return JSONObject()
            .put("running", JarvisService.running)
            .put("mode", Bus.mode.name.lowercase())
            .put("models", ModelManager.status(a))
            .put("downloading", ModelManager.busy)
            .put("perms", perms)
            .put("llm", JSONObject().put("state", LocalLlm.state.name.lowercase()).put("error", LocalLlm.error))
            .put("settings", Prefs.toJson())
            .put("version", a.packageManager.getPackageInfo(a.packageName, 0).versionName)
            .toString()
    }

    @JavascriptInterface
    fun setSetting(key: String, value: String) {
        Prefs.putFromText(key, value)
    }

    /** Dopo aver cambiato le impostazioni: il servizio ricarica i motori. */
    @JavascriptInterface
    fun applySettings() {
        if (JarvisService.running) JarvisService.send(a, JarvisService.ACTION_RELOAD)
    }

    @JavascriptInterface fun start() = a.runOnUiThread { a.startJarvis() }

    @JavascriptInterface fun stop() {
        if (JarvisService.running) JarvisService.send(a, JarvisService.ACTION_STOP)
    }

    @JavascriptInterface fun listen() = a.runOnUiThread {
        if (!a.hasMic()) a.requestPerms() else JarvisService.send(a, JarvisService.ACTION_LISTEN)
    }

    @JavascriptInterface fun sendText(text: String) = a.runOnUiThread {
        if (!a.hasMic()) { a.requestPerms(); return@runOnUiThread }
        JarvisService.send(a, JarvisService.ACTION_TEXT, text)
    }

    @JavascriptInterface fun hush() { JarvisService.instance?.stopSpeaking() }

    @JavascriptInterface fun downloadModels() {
        Thread {
            val ok = ModelManager.downloadVoice(a)
            if (ok) a.runOnUiThread { a.startJarvis() }
        }.start()
    }

    @JavascriptInterface fun importLlm() = a.runOnUiThread { a.pickLlm() }

    @JavascriptInterface fun deleteLlm() {
        LocalLlm.unload()
        ModelManager.deleteLlm(a)
        Bus.log("sys", "Modello IA cancellato.")
    }

    @JavascriptInterface fun requestPerms() = a.runOnUiThread { a.requestPerms() }
    @JavascriptInterface fun openOverlay() = a.runOnUiThread { a.openOverlaySettings() }
    @JavascriptInterface fun openBattery() = a.runOnUiThread { a.openBatterySettings() }
    @JavascriptInterface fun openAssistant() = a.runOnUiThread { a.openAssistantSettings() }

    @JavascriptInterface fun openBrowser(url: String) = a.runOnUiThread {
        WebController.open(a, url.ifBlank { "https://duckduckgo.com/?kl=it-it" })
    }

    /** Apre un link in Chrome (serve per scaricare il modello IA da Hugging Face). */
    @JavascriptInterface fun openExternal(url: String) = a.runOnUiThread {
        try {
            a.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (_: Exception) {}
    }

    @JavascriptInterface fun notes(): String = JSONArray(NotesCmd.all(a)).toString()
}
