package fr.gotraceo.live.ui

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets
import kotlin.math.max

/**
 * Android 15 et plus dessinent l'appli sous les barres système (bord à bord), sans possibilité de
 * s'y soustraire pour une appli qui cible Android 16. Les écrans gardent leur mise en page
 * habituelle : leur contenu est décalé de la hauteur des barres, de l'encoche et du clavier,
 * et le fond de la fenêtre (même couleur que l'écran) passe sous les barres.
 */
object SystemBars {

    fun pad(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        // Une feuille flottante (À proximité) est placée par le système, hors des barres.
        if (activity.window.isFloating) return
        val content = activity.findViewById<View>(android.R.id.content) ?: return
        content.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            // Le clavier remplace la barre de navigation en bas (adjustResize n'agit plus en bord à bord).
            val keyboard = insets.getInsets(WindowInsets.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, max(bars.bottom, keyboard.bottom))
            WindowInsets.CONSUMED
        }
        content.requestApplyInsets()
    }
}
