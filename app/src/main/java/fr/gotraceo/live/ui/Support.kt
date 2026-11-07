package fr.gotraceo.live.ui

import android.app.Activity
import fr.gotraceo.live.R

/** Soutien facultatif : la page Buy Me a Coffee s'ouvre dans le navigateur, l'appli ne gère aucun paiement. */
object Support {

    const val URL = "https://buymeacoffee.com/traceo"

    fun open(activity: Activity) = Browser.open(activity, URL, R.string.support_no_browser)
}
