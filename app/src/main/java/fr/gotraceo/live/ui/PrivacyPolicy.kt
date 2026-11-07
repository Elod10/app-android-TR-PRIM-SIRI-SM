package fr.gotraceo.live.ui

import android.app.Activity
import fr.gotraceo.live.R

/**
 * La politique de confidentialité, publiée avec le code source. Google Play l'exige dans l'appli, à la
 * même adresse que dans la Play Console.
 */
object PrivacyPolicy {

    const val URL = "https://github.com/Elod10/traceo-live/blob/main/PRIVACY.md"

    fun open(activity: Activity) = Browser.open(activity, URL, R.string.privacy_no_browser)
}
