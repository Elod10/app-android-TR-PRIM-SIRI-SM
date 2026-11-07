package fr.gotraceo.live.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Ouvre une page web dans le navigateur, ou dit `noBrowser` si aucun ne peut l'ouvrir. */
object Browser {

    fun open(activity: Activity, url: String, noBrowser: Int) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, noBrowser, Toast.LENGTH_LONG).show()
        }
    }
}
