package fr.gotraceo.live.widget

import fr.gotraceo.live.data.Departure
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

}
