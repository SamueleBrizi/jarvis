package it.samuele.jarvis.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import it.samuele.jarvis.ai.LocalLlm
import it.samuele.jarvis.audio.AudioInput
import it.samuele.jarvis.audio.Sfx
import it.samuele.jarvis.brain.Brain
import it.samuele.jarvis.brain.Reply
import it.samuele.jarvis.core.Bus
import it.samuele.jarvis.core.Mode
import it.samuele.jarvis.core.Prefs
import it.samuele.jarvis.models.ModelManager
import it.samuele.jarvis.ui.MainActivity
import it.samuele.jarvis.voice.Speaker
import it.samuele.jarvis.voice.stt.CommandListener
import it.samuele.jarvis.voice.wake.OpenWakeWordEngine
import it.samuele.jarvis.voice.wake.VoskWakeEngine
import it.samuele.jarvis.voice.wake.WakeEngine
import it.samuele.jarvis.voice.wake.WakeResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.vosk.Model

/**
 * Il cuore di Jarvis: servizio in primo piano che tiene il microfono acceso
 * anche con l'app chiusa o lo schermo spento.
 *
 *   WAKE ──"Hey Jarvis"──▶ (Eccomi, signore) ──▶ COMMAND ──testo──▶ THINKING ──▶ SPEAKING ──▶ WAKE
 */
class JarvisService : Service() {

    companion object {
        const val ACTION_START = "jarvis.START"
        const val ACTION_STOP = "jarvis.STOP"
        const val ACTION_LISTEN = "jarvis.LISTEN"   // ascolta subito un comando (pulsante)
        const val ACTION_TEXT = "jarvis.TEXT"       // comando scritto
        const val ACTION_RELOAD = "jarvis.RELOAD"   // ricarica motori dopo cambio impostazioni/modelli
        const val EXTRA_TEXT = "text"

        @Volatile var instance: JarvisService? = null
            private set

        val running: Boolean get() = instance != null

        fun send(ctx: Context, action: String, text: String? = null) {
            val i = Intent(ctx, JarvisService::class.java).setAction(action)
            if (text != null) i.putExtra(EXTRA_TEXT, text)
            ContextCompat.startForegroundService(ctx, i)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())

    private lateinit var speaker: Speaker
    private var audio: AudioInput? = null
    private var voskModel: Model? = null
    @Volatile private var wake: WakeEngine? = null
    @Volatile private var command: CommandListener? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile private var mode = Mode.LOADING
    @Volatile private var streamOpen = false
    @Volatile private var afterSpeech: (() -> Unit)? = null
    @Volatile private var initializing = false
    private var levelTick = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        speaker = Speaker(this) { onSpeechDone() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android richiede di mostrare subito la notifica fissa
        if (!goForeground()) { stopSelf(); return START_NOT_STICKY }

        when (intent?.action) {
            ACTION_STOP -> { shutdown(); return START_NOT_STICKY }
            ACTION_RELOAD -> { speaker.applyVoice(); reload() }
            ACTION_LISTEN -> { ensureStarted(); main.postDelayed({ manualListen() }, if (audio == null) 2500L else 0L) }
            ACTION_TEXT -> {
                ensureStarted()
                intent?.getStringExtra(EXTRA_TEXT)?.let { if (it.isNotBlank()) handleText(it) }
            }
            else -> ensureStarted()
        }
        Prefs.put("autoStart", true)
        return START_STICKY
    }

    private fun goForeground(): Boolean {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Bus.log("err", "Serve il permesso del microfono.")
            return false
        }
        return try {
            val type = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            ServiceCompat.startForeground(this, Notifications.ID_LISTEN,
                Notifications.listening(this, "Avvio in corso…"), type)
            true
        } catch (e: Exception) {
            Bus.log("err", "Android ha bloccato l'avvio in background: apra l'app Jarvis. (${e.message})")
            false
        }
    }

    // ------------------------------------------------------------------ avvio motori
    private fun ensureStarted() {
        if (audio != null || initializing) return
        initializing = true
        setMode(Mode.LOADING, "Caricamento modelli…")
        scope.launch {
            try { setupEngines() } finally { initializing = false }
        }
        if (Prefs.bool("llmEnabled") && ModelManager.llmReady(this)) {
            scope.launch { LocalLlm.load(this@JarvisService) }
        }
    }

    private fun setupEngines() {
        val ctx = this
        if (!ModelManager.voskReady(ctx)) {
            setMode(Mode.IDLE, "Mancano i modelli vocali")
            Bus.log("sys", "Per ascoltarla devo scaricare i modelli vocali (circa 50 MB). Li trova nella schermata di configurazione.")
            Notifications.update(ctx, "Configura i modelli vocali")
            return
        }
        if (voskModel == null) voskModel = Model(ModelManager.voskDir(ctx).absolutePath)
        val model = voskModel ?: return
        command = CommandListener(model) { p -> Bus.partial(p) }
        wake = buildWake(model)

        acquireWakeLock()
        val a = AudioInput { frame -> onFrame(frame) }
        if (!a.start()) {
            setMode(Mode.IDLE, "Microfono occupato")
            Bus.log("err", "Non riesco ad aprire il microfono: forse è usato da un'altra app.")
            return
        }
        audio = a
        val w = wake
        val hint = if (w is OpenWakeWordEngine) "Dica «Hey Jarvis»" else "Dica «Jarvis»"
        Notifications.update(ctx, "$hint · ${w?.label ?: ""}")
        Bus.log("sys", "In ascolto. $hint.")
        setMode(Mode.WAKE)
    }

    private fun buildWake(model: Model): WakeEngine {
        val wantOww = Prefs.str("wakeEngine") == "oww"
        if (wantOww && ModelManager.owwReady(this)) {
            try {
                return OpenWakeWordEngine(ModelManager.owwDir(this), Prefs.float("wakeThreshold"))
            } catch (e: Throwable) {
                Bus.log("err", "openWakeWord non disponibile (${e.message}): uso Vosk.")
            }
        }
        return VoskWakeEngine(model, Prefs.str("wakeVariants"))
    }

    private fun reload() {
        scope.launch {
            val model = voskModel
            if (model == null) {
                audio?.stop(); audio = null
                ensureStarted()
                return@launch
            }
            val old = wake
            wake = buildWake(model)
            old?.close()
            (wake as? OpenWakeWordEngine)?.threshold = Prefs.float("wakeThreshold")
            if (!Prefs.bool("llmEnabled")) LocalLlm.unload()
            else if (!LocalLlm.ready && ModelManager.llmReady(this@JarvisService)) LocalLlm.load(this@JarvisService)
            Bus.log("sys", "Impostazioni applicate.")
            setMode(Mode.WAKE)
        }
    }

    // ------------------------------------------------------------------ audio
    private fun onFrame(frame: ShortArray) {
        val m = mode
        if (Bus.hasListeners && (m == Mode.WAKE || m == Mode.COMMAND) && (levelTick++ % 2 == 0)) {
            Bus.level(AudioInput.level(frame))
        }
        when (m) {
            Mode.WAKE -> wake?.process(frame)?.let { r -> main.post { onWake(r) } }
            Mode.COMMAND -> command?.process(frame)?.let { text -> mode = Mode.THINKING; main.post { onHeard(text) } }
            else -> {}
        }
    }

    private fun onWake(r: WakeResult) {
        if (mode != Mode.WAKE) return
        if (Prefs.bool("screenOnWake")) showHud()
        val trailing = r.trailingCommand
        if (!trailing.isNullOrBlank()) {   // "Jarvis apri YouTube" detto tutto insieme
            Sfx.wake()
            handleText(trailing)
            return
        }
        if (Prefs.str("greeting") == "voce") {
            setMode(Mode.SPEAKING)
            afterSpeech = { startCommand() }
            speaker.say(Prefs.str("greetingText").ifBlank { "Eccomi, signore." })
        } else {
            Sfx.wake()
            main.postDelayed({ startCommand() }, 150)
        }
    }

    private fun startCommand() {
        val c = command ?: run { setMode(Mode.WAKE); return }
        c.begin()
        Bus.partial("")
        setMode(Mode.COMMAND)
    }

    /** Pulsante microfono / notifica "Parla": salta la parola di attivazione. */
    private fun manualListen() {
        if (command == null) { Bus.log("sys", "Il riconoscimento vocale non è ancora pronto."); return }
        speaker.stop()
        Sfx.wake()
        startCommand()
    }

    private fun onHeard(text: String) {
        if (text.isBlank()) {
            Sfx.end()
            Bus.partial("")
            backToWake()
            return
        }
        handleText(text)
    }

    // ------------------------------------------------------------------ cervello
    fun handleText(text: String) {
        setMode(Mode.THINKING)
        Bus.partial("")
        Bus.log("user", text)
        scope.launch {
            val reply = try {
                Brain.process(text, this@JarvisService)
            } catch (e: Exception) {
                Reply("Si è verificato un errore: ${e.message ?: "sconosciuto"}. Controlli la connessione, ${Prefs.title}.")
            }
            main.post { deliver(reply) }
        }
    }

    private fun deliver(reply: Reply) {
        val stream = reply.stream
        if (stream != null) {
            streamOpen = true
            setMode(Mode.SPEAKING)
            afterSpeech = { finishTurn(reply) }
            var first = true
            val started = stream(
                { sentence -> main.post { speaker.say(sentence, flush = first); first = false } },
                { full ->
                    main.post {
                        Brain.lastSpeech = full
                        Bus.log("jarvis", full)
                        streamOpen = false
                        if (!speaker.isSpeaking) onSpeechDone()
                    }
                }
            )
            if (!started) {
                streamOpen = false
                afterSpeech = null
                deliver(Reply("La mia intelligenza artificiale è occupata, riprovi tra poco."))
            }
            return
        }
        if (reply.speech.isBlank()) {
            finishTurn(reply)
            return
        }
        Brain.lastSpeech = reply.speech
        Bus.log("jarvis", reply.speech)
        setMode(Mode.SPEAKING)
        afterSpeech = { finishTurn(reply) }
        speaker.say(reply.speech)
    }

    private fun onSpeechDone() {
        if (streamOpen) return
        val next = afterSpeech ?: return
        afterSpeech = null
        next()
    }

    private fun finishTurn(reply: Reply) {
        try { reply.after?.invoke() } catch (e: Exception) { Bus.log("err", e.message ?: "errore") }
        if (instance == null) return
        if (reply.followUp && Prefs.bool("followUp") && command != null) {
            main.postDelayed({ if (mode == Mode.SPEAKING || mode == Mode.THINKING) startCommand() }, 250)
            setMode(Mode.SPEAKING) // breve pausa per non sentire l'eco della propria voce
        } else backToWake()
    }

    private fun backToWake() {
        wake?.reset()
        setMode(if (audio != null) Mode.WAKE else Mode.IDLE)
    }

    /** Frasi spontanee (timer interno, avvisi). */
    fun announce(text: String) {
        main.post {
            Notifications.alert(this, text)
            if (mode == Mode.WAKE || mode == Mode.IDLE) {
                setMode(Mode.SPEAKING)
                afterSpeech = { backToWake() }
                Bus.log("jarvis", text)
                speaker.say(text)
            }
        }
    }

    fun schedule(delayMs: Long, text: String) = main.postDelayed({ announce(text) }, delayMs)

    /** Dall'interfaccia: zittisce Jarvis. */
    fun stopSpeaking() {
        afterSpeech = null
        streamOpen = false
        speaker.stop()
        backToWake()
    }

    // ------------------------------------------------------------------ varie
    private fun setMode(m: Mode, detail: String = "") {
        mode = m
        Bus.state(m, detail)
    }

    private fun showHud() {
        try {
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra("wake", true))
        } catch (_: Exception) {}
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jarvis:listen").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    fun shutdown() {
        Prefs.put("autoStart", false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        instance = null
        audio?.stop(); audio = null
        wake?.close(); wake = null
        command?.close(); command = null
        try { voskModel?.close() } catch (_: Exception) {}
        voskModel = null
        LocalLlm.unload()
        speaker.shutdown()
        Sfx.release()
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        main.removeCallbacksAndMessages(null)
        scope.cancel()
        Bus.state(Mode.OFF)
        Bus.log("sys", "Jarvis spento.")
        super.onDestroy()
    }
}
