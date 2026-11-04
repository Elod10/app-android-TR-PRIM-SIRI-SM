package fr.trprim.siri.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import fr.trprim.siri.data.Line
import fr.trprim.siri.data.TransportMode

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

    /** Pseudo-ligne utilisée pour dessiner l'icône d'un mode de transport. */
    fun modeBadge(mode: TransportMode): Line {
        val (label, apiMode, color) = when (mode) {
            TransportMode.METRO -> Triple("M", "metro", "003CA6")
            TransportMode.RER -> Triple("RER", "rail-rer", "0064B0")
            TransportMode.TRAIN -> Triple("Train", "rail", "7A2E8C")
            TransportMode.TRAM -> Triple("T", "tram", "0B7A75")
            TransportMode.BUS -> Triple("Bus", "bus", "2E7D32")
            TransportMode.OTHER -> Triple("Câble", "cableway", "546E7A")
        }
        return Line(id = "", name = label, shortName = label, mode = apiMode, color = color, textColor = "FFFFFF")
    }

    fun openUrl(activity: Activity, url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, url, Toast.LENGTH_LONG).show()
        }
    }
}
