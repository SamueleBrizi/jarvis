package it.samuele.jarvis.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import it.samuele.jarvis.core.Bus
import it.samuele.jarvis.core.Prefs
import it.samuele.jarvis.models.ModelManager
import it.samuele.jarvis.util.Text

/**
 * IA locale sul telefono (MediaPipe LLM Inference): nessun server, nessun costo.
 * Consigliato sul Galaxy A34: Gemma 3 1B int4 (~550 MB).
 * Le risposte arrivano a pezzi, così Jarvis inizia a parlare prima di aver finito di "pensare".
 */
object LocalLlm {
    enum class State { NONE, LOADING, READY, ERROR }

    @Volatile var state = State.NONE
        private set
    @Volatile var error = ""
        private set

    private var engine: LlmInference? = null
    @Volatile private var listener: ((String, Boolean) -> Unit)? = null
    @Volatile var busy = false
        private set

    private val history = ArrayDeque<Pair<String, String>>()
    private var lastUse = 0L

    val ready: Boolean get() = state == State.READY && engine != null

    @Synchronized
    fun load(ctx: Context) {
        if (state == State.LOADING || state == State.READY) return
        if (!Prefs.bool("llmEnabled") || !ModelManager.llmReady(ctx)) { state = State.NONE; return }
        state = State.LOADING
        Bus.emit("llm", org.json.JSONObject().put("state", "loading"))
        try {
            val backend = if (Prefs.str("llmBackend") == "gpu") LlmInference.Backend.GPU else LlmInference.Backend.CPU
            val opts = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(ModelManager.llmFile(ctx).absolutePath)
                .setMaxTokens(Prefs.int("llmMaxTokens").coerceIn(256, 4096))
                .setMaxTopK(40)
                .setPreferredBackend(backend)
                .setResultListener { partial, done -> listener?.invoke(partial ?: "", done) }
                .build()
            engine = LlmInference.createFromOptions(ctx.applicationContext, opts)
            state = State.READY
            error = ""
            Bus.log("sys", "IA locale pronta (${Prefs.str("llmName").ifBlank { "modello" }}).")
        } catch (e: Throwable) {
            state = State.ERROR
            error = e.message ?: e.toString()
            Bus.log("err", "IA locale non caricata: $error")
        }
        Bus.emit("llm", org.json.JSONObject().put("state", state.name.lowercase()).put("error", error))
    }

    @Synchronized
    fun unload() {
        try { engine?.close() } catch (_: Throwable) {}
        engine = null
        state = State.NONE
        history.clear()
    }

    private fun format(): String {
        val f = Prefs.str("llmFormat")
        if (f != "auto") return f
        val n = Prefs.str("llmName").lowercase()
        return when {
            n.contains("gemma") -> "gemma"
            n.contains("qwen") || n.contains("smol") || n.contains("phi") -> "chatml"
            n.contains("llama") -> "chatml"
            else -> "gemma"
        }
    }

    private fun system(): String {
        val t = Prefs.title
        return "Sei Jarvis, l'assistente personale di Samuele, ispirato al maggiordomo digitale di Iron Man. " +
            "Rispondi sempre in italiano, in modo naturale, cordiale e conciso: al massimo tre frasi brevi. " +
            "Ti rivolgi all'utente chiamandolo \"$t\". Le tue risposte vengono lette ad alta voce: " +
            "non usare elenchi puntati, asterischi, emoji, simboli o codice. " +
            "Se non sei sicuro di un'informazione dillo con onestà."
    }

    private fun buildPrompt(question: String): String {
        if (System.currentTimeMillis() - lastUse > 5 * 60_000) history.clear()
        val sys = system()
        return when (format()) {
            "chatml" -> buildString {
                append("<|im_start|>system\n").append(sys).append("<|im_end|>\n")
                for ((q, a) in history) {
                    append("<|im_start|>user\n").append(q).append("<|im_end|>\n")
                    append("<|im_start|>assistant\n").append(a).append("<|im_end|>\n")
                }
                append("<|im_start|>user\n").append(question).append("<|im_end|>\n<|im_start|>assistant\n")
            }
            "raw" -> "$sys\n\nDomanda: $question\nRisposta:"
            else -> buildString {
                var first = true
                for ((q, a) in history) {
                    append("<start_of_turn>user\n")
                    if (first) { append(sys).append("\n\n"); first = false }
                    append(q).append("<end_of_turn>\n<start_of_turn>model\n").append(a).append("<end_of_turn>\n")
                }
                append("<start_of_turn>user\n")
                if (first) append(sys).append("\n\n")
                append(question).append("<end_of_turn>\n<start_of_turn>model\n")
            }
        }
    }

    /**
     * Fa una domanda. onSentence riceve una frase alla volta (da leggere subito),
     * onDone riceve la risposta completa. remember=false per riassunti/ricerche.
     */
    fun ask(question: String, remember: Boolean, onSentence: (String) -> Unit, onDone: (String) -> Unit): Boolean {
        val eng = engine ?: return false
        if (busy) return false
        busy = true
        val full = StringBuilder()
        val pending = StringBuilder()
        listener = { chunk, done ->
            full.append(chunk)
            pending.append(chunk)
            // appena c'è una frase completa la passo alla voce
            while (true) {
                val m = Regex("[.!?](\\s|$)").find(pending) ?: break
                if (m.range.first < 15 && !done) break
                val sentence = pending.substring(0, m.range.first + 1)
                pending.delete(0, m.range.first + 1)
                val clean = cleanup(sentence)
                if (clean.isNotBlank()) onSentence(clean)
            }
            if (done) {
                val rest = cleanup(pending.toString())
                if (rest.isNotBlank()) onSentence(rest)
                val answer = cleanup(full.toString())
                if (remember) {
                    history.addLast(question to answer)
                    while (history.size > 3) history.removeFirst()
                }
                lastUse = System.currentTimeMillis()
                busy = false
                listener = null
                onDone(answer)
            }
        }
        return try {
            eng.generateResponseAsync(buildPrompt(question))
            true
        } catch (e: Throwable) {
            busy = false
            listener = null
            Bus.log("err", "IA: ${e.message}")
            false
        }
    }

    private fun cleanup(s: String): String =
        Text.forSpeech(s.replace("<end_of_turn>", "").replace("<|im_end|>", "").replace("<eos>", ""))

    fun forget() = history.clear()
}
