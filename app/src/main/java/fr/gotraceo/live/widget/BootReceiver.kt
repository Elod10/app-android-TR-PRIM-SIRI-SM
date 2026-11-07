package fr.gotraceo.live.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reprogramme l'actualisation périodique après un redémarrage ou une mise à jour de l'application. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                WidgetUpdater.renderAll(context)
                WidgetUpdater.scheduleTick(context)
            }
        }
    }
}
