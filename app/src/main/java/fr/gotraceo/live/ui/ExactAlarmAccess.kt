package fr.gotraceo.live.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Autorisation « Alarmes et rappels » (SCHEDULE_EXACT_ALARM). Accordée d'office jusqu'à Android 13 ;
 * à partir d'Android 14, l'usager l'accorde dans un écran des réglages : il n'existe pas de fenêtre
 * système pour la demander.
 */
object ExactAlarmAccess {

    /** Ouvre l'écran « Alarmes et rappels » de l'appli, ou à défaut sa fiche dans les réglages. */
    fun openSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val app = Uri.fromParts("package", activity.packageName, null)
        try {
            activity.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, app))
        } catch (e: ActivityNotFoundException) {
            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app))
        }
    }
}
