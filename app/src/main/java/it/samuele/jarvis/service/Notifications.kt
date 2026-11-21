package it.samuele.jarvis.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import it.samuele.jarvis.R
import it.samuele.jarvis.ui.MainActivity

/** Notifiche: quella fissa "Jarvis in ascolto" (obbligatoria per ascoltare in background) e i promemoria. */
object Notifications {
    const val CH_LISTEN = "jarvis_listen"
    const val CH_INFO = "jarvis_info"
    const val ID_LISTEN = 1
    const val ID_BOOT = 2

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_LISTEN, "Jarvis in ascolto", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Necessaria per ascoltare \"Jarvis\" anche a schermo spento"
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_INFO, "Avvisi di Jarvis", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun openApp(ctx: Context, extra: String? = null): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (extra != null) i.putExtra(extra, true)
        return PendingIntent.getActivity(ctx, extra.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun listening(ctx: Context, text: String): Notification {
        val stop = PendingIntent.getService(ctx, 10,
            Intent(ctx, JarvisService::class.java).setAction(JarvisService.ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val talk = PendingIntent.getService(ctx, 11,
            Intent(ctx, JarvisService::class.java).setAction(JarvisService.ACTION_LISTEN), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(ctx, CH_LISTEN)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setColor(Color.parseColor("#3DE8FF"))
            .setContentTitle("Jarvis è attivo")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openApp(ctx))
            .addAction(Notification.Action.Builder(null, "Parla", talk).build())
            .addAction(Notification.Action.Builder(null, "Spegni", stop).build())
            .build()
    }

    fun update(ctx: Context, text: String) {
        ctx.getSystemService(NotificationManager::class.java).notify(ID_LISTEN, listening(ctx, text))
    }

    /** Dopo il riavvio Android non permette di riaccendere il microfono da solo: chiedo un tocco. */
    fun bootReminder(ctx: Context) {
        val n = Notification.Builder(ctx, CH_INFO)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setContentTitle("Riattiva Jarvis")
            .setContentText("Tocca per rimettere Jarvis in ascolto")
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, "autostart"))
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_BOOT, n)
    }

    fun alert(ctx: Context, text: String) {
        val n = Notification.Builder(ctx, CH_INFO)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setContentTitle("Jarvis")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify((System.currentTimeMillis() % 100000).toInt() + 100, n)
    }
}
