package it.samuele.jarvis.models

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import it.samuele.jarvis.core.Bus
import it.samuele.jarvis.core.Config
import it.samuele.jarvis.core.Prefs
import it.samuele.jarvis.util.Http
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/** Scarica e controlla i modelli offline: Vosk (voce), openWakeWord ("Hey Jarvis"), IA locale (.task). */
object ModelManager {

    fun voskDir(ctx: Context) = File(ctx.filesDir, Config.VOSK_DIR)
    fun owwDir(ctx: Context) = File(ctx.filesDir, Config.OWW_DIR)
    fun llmFile(ctx: Context) = File(File(ctx.filesDir, Config.LLM_DIR), Config.LLM_FILE)

    fun voskReady(ctx: Context) = File(voskDir(ctx), "am").isDirectory && File(voskDir(ctx), "conf").isDirectory
    fun owwReady(ctx: Context) = Config.OWW_FILES.all { File(owwDir(ctx), it).length() > 1000 }
    fun llmReady(ctx: Context) = llmFile(ctx).length() > 1_000_000

    fun freeMb(ctx: Context): Long {
        val s = StatFs(ctx.filesDir.absolutePath)
        return s.availableBytes / (1024 * 1024)
    }

    private fun dirSizeMb(f: File): Long =
        if (!f.exists()) 0 else f.walkTopDown().filter { it.isFile }.sumOf { it.length() } / (1024 * 1024)

    fun status(ctx: Context): JSONObject = JSONObject()
        .put("vosk", voskReady(ctx))
        .put("oww", owwReady(ctx))
        .put("llm", llmReady(ctx))
        .put("llmName", Prefs.str("llmName"))
        .put("llmMb", llmFile(ctx).length() / (1024 * 1024))
        .put("voskMb", dirSizeMb(voskDir(ctx)))
        .put("freeMb", freeMb(ctx))

    @Volatile var busy = false
        private set

    /** Scarica i modelli vocali che mancano (circa 50 MB di download, 95 MB occupati). */
    fun downloadVoice(ctx: Context): Boolean {
        if (busy) return false
        busy = true
        try {
            if (!owwReady(ctx)) {
                val dir = owwDir(ctx).apply { mkdirs() }
                Config.OWW_FILES.forEachIndexed { i, name ->
                    val dest = File(dir, name)
                    if (dest.length() > 1000) return@forEachIndexed
                    val tmp = File(dir, "$name.part")
                    Http.download(Config.OWW_BASE + name, tmp) { p ->
                        Bus.progress("Parola di attivazione ${i + 1}/3", p)
                    }
                    tmp.renameTo(dest)
                }
            }
            if (!voskReady(ctx)) {
                if (freeMb(ctx) < 200) {
                    Bus.progress("Spazio insufficiente", 0, done = true, error = "Servono almeno 200 MB liberi")
                    return false
                }
                val zip = File(ctx.cacheDir, "vosk.zip")
                Http.download(Config.VOSK_URL, zip) { p -> Bus.progress("Voce italiana (Vosk)", p) }
                Bus.progress("Estrazione voce italiana…", 100)
                val tmp = File(ctx.filesDir, Config.VOSK_DIR + ".tmp")
                tmp.deleteRecursively()
                unzipStripRoot(zip, tmp)
                zip.delete()
                voskDir(ctx).deleteRecursively()
                if (!tmp.renameTo(voskDir(ctx))) throw Exception("impossibile spostare il modello")
            }
            Bus.progress("Modelli vocali pronti", 100, done = true)
            return true
        } catch (e: Exception) {
            Bus.progress("Download non riuscito", 0, done = true, error = e.message ?: "errore")
            return false
        } finally {
            busy = false
        }
    }

    /** Lo zip di Vosk contiene una cartella principale: la saltiamo. */
    private fun unzipStripRoot(zip: File, dest: File) {
        dest.mkdirs()
        val base = dest.canonicalPath
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                val name = e.name.substringAfter('/', "")
                if (name.isNotEmpty()) {
                    val out = File(dest, name)
                    if (!out.canonicalPath.startsWith(base)) throw Exception("zip non valido")
                    if (e.isDirectory) out.mkdirs()
                    else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zis.copyTo(it) }
                    }
                }
                e = zis.nextEntry
            }
        }
    }

    /** Copia dentro Jarvis il file dell'IA scelto dall'utente (es. Gemma3-1B-IT ... .task). */
    fun importLlm(ctx: Context, uri: Uri): Boolean {
        if (busy) return false
        busy = true
        try {
            var name = "modello.task"
            var size = -1L
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0) name = c.getString(ni) ?: name
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
            val lower = name.lowercase()
            if (!lower.endsWith(".task") && !lower.endsWith(".bin")) {
                Bus.progress("File non adatto", 0, done = true, error = "Serve un file .task (es. Gemma3-1B-IT_q4 .task)")
                return false
            }
            val sizeMb = size / (1024 * 1024)
            val old = llmFile(ctx)
            val freeAfterDelete = freeMb(ctx) + old.length() / (1024 * 1024)
            if (size > 0 && freeAfterDelete < sizeMb + 150) {
                Bus.progress("Spazio insufficiente", 0, done = true,
                    error = "Il modello pesa $sizeMb MB, liberi solo $freeAfterDelete MB")
                return false
            }
            old.delete()
            val tmp = File(old.parentFile, "model.part").apply { parentFile?.mkdirs() }
            val input = ctx.contentResolver.openInputStream(uri) ?: throw Exception("file non leggibile")
            input.use { inp ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(1 shl 20)
                    var done = 0L
                    var last = -1
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (size > 0) {
                            val p = (done * 100 / size).toInt()
                            if (p != last) { last = p; Bus.progress("Copia IA locale", p) }
                        }
                    }
                }
            }
            if (!tmp.renameTo(old)) throw Exception("impossibile salvare il modello")
            Prefs.put("llmName", name)
            Bus.progress("IA importata: ora puoi cancellare il file originale da Download", 100, done = true)
            return true
        } catch (e: Exception) {
            Bus.progress("Importazione non riuscita", 0, done = true, error = e.message ?: "errore")
            return false
        } finally {
            busy = false
        }
    }

    fun deleteLlm(ctx: Context) {
        llmFile(ctx).delete()
        Prefs.put("llmName", "")
    }
}
