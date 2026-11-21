package it.samuele.jarvis.util

import it.samuele.jarvis.core.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Richieste web semplici (meteo, Wikipedia, ricerche, Home Assistant, download modelli). */
object Http {

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun open(url: String, timeout: Int, headers: Map<String, String>): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeout
        c.readTimeout = timeout
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", Config.USER_AGENT)
        c.setRequestProperty("Accept-Language", "it-IT,it;q=0.9,en;q=0.5")
        for ((k, v) in headers) c.setRequestProperty(k, v)
        return c
    }

    fun get(url: String, headers: Map<String, String> = emptyMap(), timeout: Int = 10000, maxBytes: Int = 2_000_000): String {
        var c = open(url, timeout, headers)
        // segue anche i redirect http -> https
        var hops = 0
        while (c.responseCode in 300..399 && hops < 5) {
            val loc = c.getHeaderField("Location") ?: break
            c.disconnect()
            c = open(URL(URL(url), loc).toString(), timeout, headers)
            hops++
        }
        if (c.responseCode >= 400) throw Exception("HTTP ${c.responseCode}")
        c.inputStream.use { input ->
            val buf = ByteArray(16384)
            val out = java.io.ByteArrayOutputStream()
            while (out.size() < maxBytes) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
            return String(out.toByteArray(), Charsets.UTF_8)
        }
    }

    fun post(url: String, body: String, headers: Map<String, String> = emptyMap(), timeout: Int = 10000): String {
        val c = open(url, timeout, headers)
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        if (c.responseCode >= 400) throw Exception("HTTP ${c.responseCode}")
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    /** Scarica un file mostrando la percentuale. */
    fun download(url: String, dest: File, onProgress: (Int) -> Unit) {
        var c = open(url, 20000, emptyMap())
        var hops = 0
        while (c.responseCode in 300..399 && hops < 6) {
            val loc = c.getHeaderField("Location") ?: break
            c.disconnect()
            c = open(URL(URL(url), loc).toString(), 20000, emptyMap())
            hops++
        }
        if (c.responseCode >= 400) throw Exception("HTTP ${c.responseCode} su $url")
        val total = c.contentLengthLong
        dest.parentFile?.mkdirs()
        c.inputStream.use { input ->
            dest.outputStream().use { out ->
                val buf = ByteArray(65536)
                var done = 0L
                var last = -1
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) {
                        val p = (done * 100 / total).toInt()
                        if (p != last) { last = p; onProgress(p) }
                    }
                }
            }
        }
    }
}
