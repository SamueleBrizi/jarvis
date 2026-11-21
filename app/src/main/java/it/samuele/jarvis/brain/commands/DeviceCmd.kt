package it.samuele.jarvis.brain.commands

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import it.samuele.jarvis.brain.Command
import it.samuele.jarvis.brain.Reply
import it.samuele.jarvis.brain.Utterance
import it.samuele.jarvis.core.Prefs
import it.samuele.jarvis.util.Text

/** Telefono: torcia, batteria, volume, pannelli Wi-Fi/Bluetooth, fotocamera. */
object DeviceCmd : Command {
    override val name = "dispositivo"

    override fun handle(u: Utterance, ctx: Context): Reply? {
        if (u.has("torcia", "flash", "luce del telefono")) {
            val on = !u.has("spegni", "disattiva", "togli")
            return if (torch(ctx, on)) Reply(if (on) "Torcia accesa." else "Torcia spenta.")
            else Reply("Non riesco a controllare la torcia.")
        }
        if (u.has("batteria", "carica del telefono", "quanta carica")) {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val charging = bm.isCharging
            return Reply("La batteria è al $pct percento" + (if (charging) ", ed è in carica." else "."))
        }
        if (u.has("volume", "muto", "silenzioso", "silenzia")) return volume(u, ctx)
        if (u.has("wifi", "wi fi", "wireless")) {
            val i = if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS)
            open(ctx, i)
            return Reply("Ecco il pannello Wi-Fi. Android non permette alle app di attivarlo da sole.")
        }
        if (u.has("bluetooth")) {
            open(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            return Reply("Le apro le impostazioni Bluetooth.")
        }
        if (u.has("luminosita")) {
            open(ctx, Intent(Settings.ACTION_DISPLAY_SETTINGS))
            return Reply("Ecco le impostazioni dello schermo.")
        }
        if (u.has("scatta una foto", "fai una foto", "apri la fotocamera", "apri fotocamera", "fotocamera")) {
            open(ctx, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            return Reply("Fotocamera pronta.")
        }
        return null
    }

    private fun torch(ctx: Context, on: Boolean): Boolean = try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        if (id != null) { cm.setTorchMode(id, on); true } else false
    } catch (e: Exception) { false }

    private fun volume(u: Utterance, ctx: Context): Reply {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val s = AudioManager.STREAM_MUSIC
        val max = am.getStreamMaxVolume(s)
        val n = Text.numbers(u.n)
        Regex("(\\d{1,3})\\s*(%|percento|per cento)?").find(n.substringAfter("volume", ""))?.let {
            val p = it.groupValues[1].toInt().coerceIn(0, 100)
            am.setStreamVolume(s, (max * p / 100f).toInt(), AudioManager.FLAG_SHOW_UI)
            return Reply("Volume al $p percento.")
        }
        return when {
            u.has("muto", "silenzioso", "silenzia", "togli l audio") -> {
                am.setStreamVolume(s, 0, AudioManager.FLAG_SHOW_UI); Reply("")
            }
            u.has("massimo", "tutto") -> { am.setStreamVolume(s, max, AudioManager.FLAG_SHOW_UI); Reply("Volume al massimo.") }
            u.has("alza", "aumenta", "piu alto", "piu forte") -> {
                repeat(2) { am.adjustStreamVolume(s, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI) }
                Reply("Fatto.")
            }
            u.has("abbassa", "diminuisci", "piu basso", "piu piano") -> {
                repeat(2) { am.adjustStreamVolume(s, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI) }
                Reply("Fatto.")
            }
            else -> {
                val p = am.getStreamVolume(s) * 100 / maxOf(1, max)
                Reply("Il volume è al $p percento, ${Prefs.title}.")
            }
        }
    }

    private fun open(ctx: Context, i: Intent) {
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }
}
