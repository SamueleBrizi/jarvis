package it.samuele.jarvis.brain.commands

import android.content.Context
import it.samuele.jarvis.brain.Command
import it.samuele.jarvis.brain.Reply
import it.samuele.jarvis.brain.Utterance
import it.samuele.jarvis.util.Http
import it.samuele.jarvis.util.Text
import org.json.JSONArray
import org.json.JSONObject

/** "Chi è…", "cos'è…", "parlami di…": risponde con Wikipedia in italiano. */
object KnowledgeCmd : Command {
    override val name = "conoscenza"

    override fun handle(u: Utterance, ctx: Context): Reply? {
        val m = Regex("^(?:chi e|chi era|chi sono|chi erano|cos e|cosa e|che cos e|che cosa e|cosa sono|che cosa sono|dimmi qualcosa su|parlami di|parlami del|parlami della|cerca su wikipedia|wikipedia)\\s+(.+)$")
            .find(u.n) ?: return null
        val topic = Text.stripArticles(m.groupValues[1]).trim()
        if (topic.isBlank()) return null
        val search = JSONArray(Http.get("https://it.wikipedia.org/w/api.php?action=opensearch&limit=1&namespace=0&format=json&search=${Http.enc(topic)}"))
        val title = search.optJSONArray(1)?.optString(0).orEmpty()
        if (title.isBlank()) return null // lascio la domanda all'IA o al web
        val sum = JSONObject(Http.get("https://it.wikipedia.org/api/rest_v1/page/summary/${Http.enc(title.replace(' ', '_'))}"))
        val extract = sum.optString("extract")
        if (extract.isBlank()) return null
        it.samuele.jarvis.core.Bus.card(title, extract.take(300), "https://it.wikipedia.org/wiki/${Http.enc(title.replace(' ', '_'))}")
        return Reply(Text.firstSentences(extract, 2), followUp = true)
    }
}
