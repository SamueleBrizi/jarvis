package it.samuele.jarvis.brain.commands

import android.content.Context
import it.samuele.jarvis.brain.Command
import it.samuele.jarvis.brain.Reply
import it.samuele.jarvis.brain.Utterance
import it.samuele.jarvis.core.Prefs
import org.json.JSONArray
import java.io.File

/** Note vocali salvate sul telefono. */
object NotesCmd : Command {
    override val name = "note"

    private fun file(ctx: Context) = File(ctx.filesDir, "notes.json")
    private fun load(ctx: Context): MutableList<String> {
        val f = file(ctx)
        if (!f.exists()) return mutableListOf()
        val a = try { JSONArray(f.readText()) } catch (_: Exception) { JSONArray() }
        return MutableList(a.length()) { a.getString(it) }
    }
    private fun save(ctx: Context, l: List<String>) = file(ctx).writeText(JSONArray(l).toString())

    fun all(ctx: Context): List<String> = load(ctx)

    override fun handle(u: Utterance, ctx: Context): Reply? {
        val raw = u.raw.trim()
        val add = Regex("^(prendi (una )?nota|annota|scrivi (una )?nota|segna|ricordami di|ricordami che|aggiungi (una )?nota)\\s*:?\\s*(.*)$",
            RegexOption.IGNORE_CASE).find(raw)
        if (add != null) {
            val text = add.groupValues.last().trim()
            if (text.isEmpty()) return Reply("Cosa devo annotare, ${Prefs.title}?", followUp = true)
            val l = load(ctx); l.add(text); save(ctx, l)
            return Reply("Annotato: $text.")
        }
        if (u.has("leggi le note", "leggimi le note", "quali note", "le mie note", "che note ho", "cosa ho annotato")) {
            val l = load(ctx)
            if (l.isEmpty()) return Reply("Non ha nessuna nota, ${Prefs.title}.")
            val list = l.takeLast(10).mapIndexed { i, s -> "${i + 1}: $s" }.joinToString(". ")
            return Reply("Ha ${l.size} note. $list.")
        }
        if (u.has("cancella l ultima nota", "elimina l ultima nota")) {
            val l = load(ctx)
            if (l.isEmpty()) return Reply("Non ci sono note da cancellare.")
            val r = l.removeAt(l.size - 1); save(ctx, l)
            return Reply("Ho cancellato la nota: $r.")
        }
        if (u.has("cancella le note", "cancella tutte le note", "elimina le note")) {
            save(ctx, emptyList())
            return Reply("Note cancellate.")
        }
        return null
    }
}
