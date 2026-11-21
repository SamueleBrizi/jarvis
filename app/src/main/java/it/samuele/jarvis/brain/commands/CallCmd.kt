package it.samuele.jarvis.brain.commands

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import it.samuele.jarvis.brain.Command
import it.samuele.jarvis.brain.Reply
import it.samuele.jarvis.brain.Utterance
import it.samuele.jarvis.core.Prefs
import it.samuele.jarvis.util.Http
import it.samuele.jarvis.util.Text

/** Chiamate, SMS e WhatsApp ai contatti della rubrica. */
object CallCmd : Command {
    override val name = "chiamate"

    data class Contact(val name: String, val number: String)

    override fun handle(u: Utterance, ctx: Context): Reply? {
        val n = u.n
        // WhatsApp: "scrivi su whatsapp a marco che arrivo tra poco"
        if (u.has("whatsapp")) {
            val m = Regex("whatsapp (?:a|ad) ([a-z ]+?)(?: (?:che|dicendo|dicendogli|dicendole|scrivendo) (.+))?$").find(n)
                ?: return Reply("A chi devo scrivere su WhatsApp?", followUp = true)
            val c = find(ctx, m.groupValues[1]) ?: return notFound(m.groupValues[1])
            val msg = m.groupValues[2]
            val url = "https://api.whatsapp.com/send?phone=${intl(c.number)}" + (if (msg.isNotBlank()) "&text=${Http.enc(msg)}" else "")
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.whatsapp")
            if (!open(ctx, i)) open(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            return Reply("Apro la chat WhatsApp con ${c.name}" + (if (msg.isNotBlank()) ": il messaggio è pronto, tocchi invia." else "."))
        }
        // SMS: "manda un messaggio a mamma dicendo che arrivo"
        val sms = Regex("(?:manda|invia|scrivi) (?:un )?(?:messaggio|sms)? ?(?:a|ad) ([a-z ]+?)(?: (?:che|dicendo|dicendogli|dicendole|con scritto) (.+))?$").find(n)
        if (sms != null && u.has("messaggio", "sms")) {
            val c = find(ctx, sms.groupValues[1]) ?: return notFound(sms.groupValues[1])
            val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${c.number}")).putExtra("sms_body", sms.groupValues[2])
            open(ctx, i)
            return Reply("Messaggio per ${c.name} pronto, tocchi invia.")
        }
        // Chiamata: "chiama mamma", "telefona a luca"
        val call = Regex("^(?:chiama|telefona a|telefona|fai una chiamata a|chiamami) (.+)$").find(n) ?: return null
        val who = Text.stripArticles(call.groupValues[1])
        val digits = Text.numbers(who).replace(" ", "")
        val c = if (digits.matches(Regex("\\+?\\d{3,15}"))) Contact(digits, digits) else find(ctx, who) ?: return notFound(who)
        val canCall = ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val i = Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(c.number)}"))
        return Reply("Chiamo ${c.name}.", after = { open(ctx, i) })
    }

    private fun notFound(who: String) = Reply("Non trovo $who in rubrica, ${Prefs.title}.")

    private fun intl(num: String): String {
        var d = num.replace(Regex("[^0-9+]"), "")
        if (d.startsWith("+")) return d.drop(1)
        if (d.startsWith("00")) return d.drop(2)
        if (d.length == 10 && d.startsWith("3")) d = "39$d" // cellulare italiano
        return d
    }

    /** Cerca il contatto più somigliante al nome detto. */
    fun find(ctx: Context, spoken: String): Contact? {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val q = Text.norm(Text.stripArticles(spoken))
        if (q.isBlank()) return null
        var best: Contact? = null
        var bestScore = 0f
        val cur = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        ) ?: return null
        cur.use {
            while (it.moveToNext()) {
                val name = it.getString(0) ?: continue
                val num = it.getString(1) ?: continue
                val nn = Text.norm(name)
                var s = Text.similar(nn, q)
                if (nn == q) s = 1.1f
                else if (nn.split(" ").contains(q)) s = maxOf(s, 0.9f)
                else if (nn.startsWith(q) || q.startsWith(nn)) s = maxOf(s, 0.8f)
                else for (w in nn.split(" ")) s = maxOf(s, Text.similar(w, q) - 0.05f)
                if (s > bestScore) { bestScore = s; best = Contact(name, num) }
            }
        }
        return if (bestScore >= 0.68f) best else null
    }

    private fun open(ctx: Context, i: Intent): Boolean = try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) { false }
}
