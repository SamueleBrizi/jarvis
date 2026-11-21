package it.samuele.jarvis.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import it.samuele.jarvis.core.Prefs

/** All'accensione del telefono ricorda di riattivare Jarvis con un tocco. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Prefs.init(ctx)
        if (Prefs.bool("autoStart")) {
            Notifications.createChannels(ctx)
            Notifications.bootReminder(ctx)
        }
    }
}
