package it.samuele.jarvis

import android.app.Application
import it.samuele.jarvis.core.Prefs
import it.samuele.jarvis.service.Notifications

/** Punto di avvio dell'app: carica le impostazioni e crea i canali delle notifiche. */
class JarvisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Notifications.createChannels(this)
    }
}
