package it.samuele.jarvis.core

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Bacheca degli eventi: il servizio pubblica (stato, frasi, livello del microfono…)
 * e l'interfaccia ascolta. Tiene uno storico breve per quando si riapre l'app.
 */
object Bus {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val history = ArrayDeque<String>()

    @Volatile var mode: Mode = Mode.OFF
        private set

    fun on(l: (String) -> Unit) { listeners.add(l) }
    fun off(l: (String) -> Unit) { listeners.remove(l) }
    val hasListeners: Boolean get() = listeners.isNotEmpty()

    fun emit(type: String, data: JSONObject = JSONObject(), keep: Boolean = false) {
        data.put("type", type)
        val json = data.toString()
        if (keep) synchronized(history) {
            history.addLast(json)
            while (history.size > 80) history.removeFirst()
        }
        if (listeners.isEmpty()) return
        main.post { for (l in listeners) l(json) }
    }

    fun history(): List<String> = synchronized(history) { history.toList() }

    // --- scorciatoie ---
    fun state(m: Mode, detail: String = "") {
        mode = m
        emit("state", JSONObject().put("mode", m.name.lowercase()).put("detail", detail))
    }

    /** kind: "user" | "jarvis" | "sys" | "err" */
    fun log(kind: String, text: String) {
        if (text.isBlank()) return
        emit("log", JSONObject().put("kind", kind).put("text", text).put("t", System.currentTimeMillis()), keep = true)
    }

    fun partial(text: String) = emit("partial", JSONObject().put("text", text))
    fun level(v: Float) = emit("level", JSONObject().put("v", v.toDouble()))

    fun card(title: String, body: String, url: String? = null) {
        val o = JSONObject().put("title", title).put("body", body)
        if (url != null) o.put("url", url)
        emit("card", o, keep = true)
    }

    fun progress(label: String, pct: Int, done: Boolean = false, error: String? = null) {
        val o = JSONObject().put("label", label).put("pct", pct).put("done", done)
        if (error != null) o.put("error", error)
        emit("progress", o)
    }
}
