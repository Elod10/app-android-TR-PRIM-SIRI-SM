package fr.gotraceo.live.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import fr.gotraceo.live.data.Line
import fr.gotraceo.live.data.TransportMode

/** Libellés partagés par les écrans de l'application. */
object UiText {

    const val PRIM_PORTAL_URL = "https://prim.iledefrance-mobilites.fr/"

    /** « Métro 4 », « RER B », « Bus 38 »… */
    fun lineTitle(line: Line): String {
        val prefix = when {
            line.mode == "metro" -> "Métro"
            line.isRer() -> "RER"
            line.mode == "rail" -> "Train"
            line.mode == "tram" -> "Tram"
            line.mode == "bus" -> "Bus"
            line.mode == "cableway" -> "Câble"
            line.mode == "funicular" -> "Funiculaire"
            else -> ""
        }
        val name = line.name.trim()
        return if (prefix.isNotEmpty() && name.length <= 6 && !name.startsWith(prefix, ignoreCase = true)) {
            "$prefix $name"
        } else {
            name
        }
    }

    fun modeSubtitle(mode: TransportMode): String = when (mode) {
        TransportMode.METRO -> "Lignes 1 à 14"
        TransportMode.RER -> "RER A, B, C, D, E"
        TransportMode.TRAIN -> "Transilien, TER"
        TransportMode.TRAM -> "Lignes de tramway"
        TransportMode.BUS -> "RATP, Île-de-France Mobilités, Noctilien…"
        TransportMode.OTHER -> "Téléphérique, funiculaire de Montmartre"
    }

    /** Pastille de texte d'un mode de transport. */
    fun modeChip(mode: TransportMode): String = when (mode) {
        TransportMode.METRO -> "M"
        TransportMode.RER -> "RER"
        TransportMode.TRAIN -> "Train"
        TransportMode.TRAM -> "T"
        TransportMode.BUS -> "Bus"
        TransportMode.OTHER -> "Câble"
    }

    fun openUrl(activity: Activity, url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, url, Toast.LENGTH_LONG).show()
        }
    }
}
