package it.samuele.jarvis.audio

import android.media.AudioManager
import android.media.ToneGenerator

/** Piccoli suoni di conferma (bip di attivazione, fine ascolto). */
object Sfx {
    private var tone: ToneGenerator? = null

    private fun gen(): ToneGenerator? {
        if (tone == null) tone = try { ToneGenerator(AudioManager.STREAM_MUSIC, 70) } catch (e: Exception) { null }
        return tone
    }

    fun wake() { gen()?.startTone(ToneGenerator.TONE_PROP_BEEP2, 120) }
    fun end() { gen()?.startTone(ToneGenerator.TONE_PROP_ACK, 90) }
    fun error() { gen()?.startTone(ToneGenerator.TONE_PROP_NACK, 150) }

    fun release() { tone?.release(); tone = null }
}
