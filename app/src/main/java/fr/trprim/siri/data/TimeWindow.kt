package fr.trprim.siri.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Plage horaire d'un widget : des jours de la semaine, une heure de début et une heure de fin, en
 * minutes après minuit. Une fin avant le début passe minuit (23:00 – 01:00) : la plage commence
 * alors un des jours choisis et finit le lendemain.
 *
 * Pendant la plage, le widget a sa notification et, si on l'a demandé, suit la position sans qu'on
 * le touche. En dehors, il reste un widget ordinaire.
 */
data class TimeWindow(
    val days: Set<DayOfWeek>,
    val startMinute: Int,
    val endMinute: Int,
) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY) { "Début hors de la journée : $startMinute" }
        require(endMinute in 0 until MINUTES_PER_DAY) { "Fin hors de la journée : $endMinute" }
    }

    /** Vrai si la plage se termine le lendemain. Une fin égale au début couvre 24 heures. */
    val crossesMidnight: Boolean get() = endMinute <= startMinute

    fun isActive(at: ZonedDateTime): Boolean {
        val minute = at.hour * 60 + at.minute
        val day = at.dayOfWeek
        return if (crossesMidnight) {
            (day in days && minute >= startMinute) || (day.minus(1) in days && minute < endMinute)
        } else {
            day in days && minute >= startMinute && minute < endMinute
        }
    }

    /** Fin de la plage en cours, ou null en dehors de la plage. */
    fun currentEnd(at: ZonedDateTime): ZonedDateTime? {
        if (!isActive(at)) return null
        val minute = at.hour * 60 + at.minute
        val date = if (crossesMidnight && minute >= startMinute) at.toLocalDate().plusDays(1) else at.toLocalDate()
        return date.atTime(endMinute / 60, endMinute % 60).atZone(at.zone)
    }

    /** « Mardi · 16:00 – 16:30 », « En semaine · 07:45 – 08:15 »… */
    fun label(): String = "${daysLabel(days)} · ${clock(startMinute)} – ${clock(endMinute)}"

    fun toJson(): JSONObject = JSONObject()
        .put("days", JSONArray(days.sorted().map { it.name }))
        .put("start", startMinute)
        .put("end", endMinute)

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        /** Proposée quand on ouvre le réglage : un trajet de fin d'après-midi en semaine. */
        val DEFAULT = TimeWindow(
            setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            startMinute = 16 * 60,
            endMinute = 16 * 60 + 30,
        )

        private val WEEKDAYS = DEFAULT.days
        private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        fun fromJson(o: JSONObject): TimeWindow? = try {
            val days = o.optJSONArray("days").toStringList().mapNotNull { name ->
                DayOfWeek.entries.firstOrNull { it.name == name }
            }.toSet()
            if (days.isEmpty()) null else TimeWindow(days, o.getInt("start"), o.getInt("end"))
        } catch (e: Exception) {
            null
        }

        /** Même écriture que les heures de passage : « 07:45 ». */
        fun clock(minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)

        /** Initiale d'un jour, pour les boutons : « L », « M », « M », « J »… */
        fun initial(day: DayOfWeek): String = dayName(day).take(1).uppercase(Locale.FRENCH)

        fun dayName(day: DayOfWeek): String = when (day) {
            DayOfWeek.MONDAY -> "lundi"
            DayOfWeek.TUESDAY -> "mardi"
            DayOfWeek.WEDNESDAY -> "mercredi"
            DayOfWeek.THURSDAY -> "jeudi"
            DayOfWeek.FRIDAY -> "vendredi"
            DayOfWeek.SATURDAY -> "samedi"
            DayOfWeek.SUNDAY -> "dimanche"
        }

        fun daysLabel(days: Set<DayOfWeek>): String {
            val text = when (days) {
                DayOfWeek.entries.toSet() -> "tous les jours"
                WEEKDAYS -> "en semaine"
                WEEKEND -> "le week-end"
                else -> {
                    val names = days.sorted().map(::dayName)
                    // Au-delà de deux jours, les abréviations tiennent sur une ligne.
                    if (names.size <= 2) names.joinToString(" et ") else names.joinToString(", ") { it.take(3) + "." }
                }
            }
            return text.replaceFirstChar { it.titlecase(Locale.FRENCH) }
        }
    }
}
