package fr.gotraceo.live.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import fr.gotraceo.live.R

/** Soutien facultatif : la page Buy Me a Coffee s'ouvre dans le navigateur, l'appli ne gère aucun paiement. */
object Support {

    const val URL = "https://buymeacoffee.com/traceo"

    fun open(activity: Activity) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(URL)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.support_no_browser, Toast.LENGTH_LONG).show()
        }
    }
}
