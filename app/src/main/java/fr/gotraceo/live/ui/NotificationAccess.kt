package fr.gotraceo.live.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import fr.gotraceo.live.R
import fr.gotraceo.live.widget.PassageNotifications

/** Autorisation d'afficher la notification d'un widget, demandée au moment où on l'active. */
object NotificationAccess {

    const val REQUEST_CODE = 41

    /**
     * Vrai si la notification peut déjà s'afficher. Sinon, demande la permission (réponse dans
     * onRequestPermissionsResult, avec [REQUEST_CODE]) ou ouvre les réglages des notifications.
     */
    fun ensure(activity: Activity): Boolean {
        if (PassageNotifications.canPost(activity)) return true
        if (needsPermission(activity)) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
        } else {
            // Permission accordée, mais notifications coupées dans les réglages d'Android.
            openSettings(activity)
        }
        return false
    }

    /** À appeler depuis onRequestPermissionsResult : vrai si la notification peut maintenant s'afficher. */
    fun onResult(activity: Activity, permissions: Array<out String>, grantResults: IntArray): Boolean {
        val index = permissions.indexOf(Manifest.permission.POST_NOTIFICATIONS)
        if (index >= 0 && grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED) {
            if (PassageNotifications.canPost(activity)) return true
            openSettings(activity)
            return false
        }
        // Refus définitif : Android ne montre plus la demande, seuls les réglages permettent d'y revenir.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            openSettings(activity)
        } else {
            Toast.makeText(activity, R.string.notify_denied, Toast.LENGTH_LONG).show()
        }
        return false
    }

    private fun needsPermission(activity: Activity): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    private fun openSettings(activity: Activity) {
        Toast.makeText(activity, R.string.notify_blocked, Toast.LENGTH_LONG).show()
        try {
            activity.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName),
            )
        } catch (e: ActivityNotFoundException) {
            // Le toast suffit : certains téléphones n'ont pas cet écran.
        }
    }
}
