package it.samuele.jarvis.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import it.samuele.jarvis.core.Config
import kotlin.math.sqrt

/**
 * Microfono sempre acceso (16 kHz mono). Consegna blocchi da 80 ms a chi ascolta:
 * prima al riconoscitore della parola "Jarvis", poi a quello dei comandi.
 */
class AudioInput(private val onFrame: (ShortArray) -> Unit) {
    @Volatile private var running = false
    private var record: AudioRecord? = null
    private var thread: Thread? = null

    val isRunning: Boolean get() = running

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val min = AudioRecord.getMinBufferSize(
            Config.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val size = maxOf(min, Config.FRAME * 2 * 4)
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, Config.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size
            )
        } catch (e: Exception) { return false }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return false }
        record = rec
        rec.startRecording()
        running = true
        thread = Thread({ loop(rec) }, "jarvis-mic").apply { priority = Thread.MAX_PRIORITY; start() }
        return true
    }

    private fun loop(rec: AudioRecord) {
        val buf = ShortArray(Config.FRAME)
        while (running) {
            var off = 0
            while (off < Config.FRAME && running) {
                val n = rec.read(buf, off, Config.FRAME - off)
                if (n <= 0) { try { Thread.sleep(20) } catch (_: InterruptedException) {}; continue }
                off += n
            }
            if (off == Config.FRAME) {
                try { onFrame(buf.copyOf()) } catch (e: Throwable) { android.util.Log.e("Jarvis", "frame", e) }
            }
        }
    }

    fun stop() {
        running = false
        try { thread?.join(500) } catch (_: Exception) {}
        thread = null
        try { record?.stop() } catch (_: Exception) {}
        record?.release()
        record = null
    }

    companion object {
        /** livello del volume 0..1 per animare il nucleo dell'HUD */
        fun level(buf: ShortArray): Float {
            var sum = 0.0
            for (s in buf) sum += (s * s).toDouble()
            val rms = sqrt(sum / buf.size)
            return (rms / 6000.0).coerceIn(0.0, 1.0).toFloat()
        }
    }
}
