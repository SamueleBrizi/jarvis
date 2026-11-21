package it.samuele.jarvis.voice.wake

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import it.samuele.jarvis.core.Config
import java.io.File
import java.nio.FloatBuffer

/**
 * "Hey Jarvis" con openWakeWord, tutto offline e leggero (pensato per stare acceso sempre).
 * Catena: audio -> melspettrogramma -> embedding (96 valori) -> classificatore "hey_jarvis".
 * Replica la versione in streaming della libreria Python originale.
 */
class OpenWakeWordEngine(dir: File, @Volatile var threshold: Float) : WakeEngine {
    override val label = "Hey Jarvis (openWakeWord)"

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(1) }
    private val melSession = env.createSession(File(dir, "melspectrogram.onnx").absolutePath, opts)
    private val embSession = env.createSession(File(dir, "embedding_model.onnx").absolutePath, opts)
    private val wwSession = env.createSession(File(dir, "hey_jarvis_v0.1.onnx").absolutePath, opts)
    private val melIn = melSession.inputNames.iterator().next()
    private val embIn = embSession.inputNames.iterator().next()
    private val wwIn = wwSession.inputNames.iterator().next()

    private val ctx = 480                                  // 3 passi da 160 campioni di contesto
    private val raw = FloatArray(Config.FRAME + ctx)        // 1760 campioni
    private val mel = ArrayDeque<FloatArray>()              // finestre da 32 bande
    private val feats = ArrayDeque<FloatArray>()            // embedding da 96
    private var predictions = 0

    @Volatile var lastScore = 0f
        private set

    init { reset() }

    override fun reset() {
        raw.fill(0f)
        mel.clear()
        repeat(76) { mel.addLast(FloatArray(32) { 1f }) }
        feats.clear()
        predictions = 0
        lastScore = 0f
    }

    override fun process(frame: ShortArray): WakeResult? {
        // scorre il buffer: tiene gli ultimi 480 campioni + il nuovo blocco
        System.arraycopy(raw, Config.FRAME, raw, 0, ctx)
        for (i in 0 until Config.FRAME) raw[ctx + i] = frame[i].toFloat()

        // 1) melspettrogramma
        OnnxTensor.createTensor(env, FloatBuffer.wrap(raw), longArrayOf(1, raw.size.toLong())).use { t ->
            melSession.run(mapOf(melIn to t)).use { r ->
                val fb = (r.get(0) as OnnxTensor).floatBuffer
                val n = fb.remaining() / 32
                for (f in 0 until n) mel.addLast(FloatArray(32) { fb.get() / 10f + 2f })
            }
        }
        while (mel.size > 76) mel.removeFirst()

        // 2) embedding sulle ultime 76 finestre
        val flat = FloatArray(76 * 32)
        var k = 0
        for (row in mel) for (v in row) flat[k++] = v
        OnnxTensor.createTensor(env, FloatBuffer.wrap(flat), longArrayOf(1, 76, 32, 1)).use { t ->
            embSession.run(mapOf(embIn to t)).use { r ->
                val fb = (r.get(0) as OnnxTensor).floatBuffer
                feats.addLast(FloatArray(96) { if (fb.hasRemaining()) fb.get() else 0f })
            }
        }
        while (feats.size > 16) feats.removeFirst()
        if (feats.size < 16) return null

        // 3) classificatore "hey jarvis" sugli ultimi 16 embedding (~1,3 s)
        val f2 = FloatArray(16 * 96)
        k = 0
        for (row in feats) for (v in row) f2[k++] = v
        var score = 0f
        OnnxTensor.createTensor(env, FloatBuffer.wrap(f2), longArrayOf(1, 16, 96)).use { t ->
            wwSession.run(mapOf(wwIn to t)).use { r ->
                score = (r.get(0) as OnnxTensor).floatBuffer.get(0)
            }
        }
        lastScore = score
        predictions++
        if (predictions <= 5) return null // le prime previsioni non sono affidabili
        if (score >= threshold) {
            reset()
            return WakeResult(score)
        }
        return null
    }

    override fun close() {
        try { melSession.close(); embSession.close(); wwSession.close() } catch (_: Exception) {}
    }
}
