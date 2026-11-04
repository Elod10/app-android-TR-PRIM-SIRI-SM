package fr.trprim.siri.widget

import fr.trprim.siri.data.Departure
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Mise en forme des passages (commune au widget et à l'application). */
object DepartureFormat {

    private val hourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val hourMinuteSecond: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun clock(epochMs: Long, withSeconds: Boolean = false): String {
        val t = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
        return (if (withSeconds) hourMinuteSecond else hourMinute).format(t)
    }

    /** Temps d'attente : « 3 min », « Imminent », « À quai »… */
    fun waitText(d: Departure, now: Long, mode: String): String {
        if (d.isCancelled) return "Supprimé"
        if (d.atStop) return if (mode == "rail" || mode == "metro") "À quai" else "À l'arrêt"
        val t = d.displayTime ?: return "—"
        val minutes = Math.floorDiv(t - now, 60_000L)
        return when {
            minutes <= 0 -> "Imminent"
            minutes < 60 -> "$minutes min"
            else -> clock(t)
        }
    }

    /** Ligne de détails : heure, retard, voie, code mission. */
    fun details(d: Departure, mode: String): String {
        val parts = ArrayList<String>()
        d.displayTime?.let { parts += clock(it) }
        if (d.isCancelled) parts += "supprimé"
        else if (d.delayMinutes > 0) parts += "retard +${d.delayMinutes} min"
        d.platform?.let { parts += if (mode == "rail") "Voie $it" else "Quai $it" }
        if (mode == "rail") d.journeyNote?.let { parts += it }
        return parts.joinToString(" · ")
    }

    fun isLate(d: Departure): Boolean = d.isCancelled || d.delayMinutes >= 2
}
