package fr.trprim.siri.widget

import fr.trprim.siri.ui.UiText
import kotlin.math.max

/**
 * Texte de la notification d'un widget, qui reste affichée sur l'écran de verrouillage. Repliée, elle
 * doit se lire d'un coup d'œil : l'attente d'abord, puis l'arrêt et le sens. Dépliée, elle détaille.
 */
data class NotificationText(
    /** Première ligne, en gras : les attentes, « Imminent, puis 3 min ». */
    val title: String,
    /** Seconde ligne de la notification repliée : « Fort d'Aubervilliers → La Courneuve ». */
    val text: String,
    /** Une ligne par sens (ou par ligne de transport, pour À proximité), quand elle est dépliée. */
    val lines: List<String>,
    /** Titre de la notification dépliée : « Métro 7 · Fort d'Aubervilliers ». */
    val expandedTitle: String,
    /** Heure du relevé, courte, dans l'en-tête de la notification repliée : « PRIM via Tracéo 00:58 ». */
    val subText: String?,
    /** Source et heure en toutes lettres, dans l'en-tête de la notification dépliée. */
    val summary: String?,
)

/** Met en mots le contenu d'un widget, pour sa notification. */
object NotificationContent {

    /** Longueur au-delà de laquelle un nom de sens est raccourci dans la notification repliée. */
    private const val COMPACT_NAME_LENGTH = 16

    /**
     * @param lineTitle nom complet de la ligne, « Métro 13 » ou « RER B »
     * @param noticeText texte affiché à la place des passages, ou null si le widget n'a rien à montrer
     * @return null quand il n'y a pas de notification à afficher (widget non configuré)
     */
    fun build(model: WidgetModel, lineTitle: String?, noticeText: (Notice) -> String?): NotificationText? {
        val notice = model.notice
        if (notice?.kind == NoticeKind.NOT_CONFIGURED) return null
        val proximity = model.format == WidgetFormat.PROXIMITE
        val expandedTitle = if (proximity) model.stopName else titleOf(model, lineTitle)
        val lines = when {
            notice != null -> listOfNotNull(noticeText(notice))
            proximity -> model.proximity.map(::proximityLine)
            else -> model.groups.map(::directionLine)
        }
        if (lines.isEmpty()) return null
        val (title, text) = when {
            notice != null -> expandedTitle to lines.first()
            proximity -> foldedProximity(model) ?: (expandedTitle to lines.first())
            else -> foldedLine(model)
        }
        return NotificationText(title, text, lines, expandedTitle, shortSourceOf(model), summaryOf(model))
    }

    /**
     * Un sens : « Imminent, puis 3 min » / « Fort d'Aubervilliers → La Courneuve-8-Mai-1945 ».
     * Deux sens : « Imminent · 5 min » / « La Courneuve-8-Mai-1945 · Mairie d'Ivry », dans le même ordre.
     */
    private fun foldedLine(model: WidgetModel): Pair<String, String> {
        val groups = model.groups
        if (groups.size >= 2) {
            return groups.joinToString(" · ") { wait(it.first) }.capitalized() to
                groups.joinToString(" · ") { compact(it.label) }
        }
        val group = groups.first()
        val then = group.next?.let { ", puis ${wait(it)}" }.orEmpty()
        val place = listOfNotNull(model.stopName.takeIf { it.isNotEmpty() }, group.label).joinToString(" → ")
        return "${wait(group.first)}${details(group.first)}$then".capitalized() to place
    }

    /** « Métro 7 · Imminent » / « vers La Courneuve · Fort d'Aubervilliers · 120 m » */
    private fun foldedProximity(model: WidgetModel): Pair<String, String>? {
        val row = model.proximity.firstOrNull { it.directions.isNotEmpty() } ?: return null
        val direction = row.directions.first()
        val name = row.line?.let(UiText::lineTitle) ?: row.badge.label
        return "$name · ${wait(direction.first).capitalized()}" to "vers ${compact(direction.label)} · ${row.place}"
    }

    /** « Métro 13 · Brochant » : la pastille seule ne suffit pas toujours, l'icône pouvant être masquée. */
    private fun titleOf(model: WidgetModel, lineTitle: String?): String {
        val line = lineTitle ?: model.line?.label
        return listOfNotNull(line, model.stopName.takeIf { it.isNotEmpty() }).joinToString(" · ")
    }

    /** « (voie 2, retard 3 min) », ou rien. */
    private fun details(first: Passage): String {
        val details = listOfNotNull(
            first.platform?.trim()?.takeIf { it.isNotEmpty() && first.kind != WaitKind.CANCELLED }
                ?.let { "${first.platformLabel.lowercase()} $it" },
            if (first.isDelayed) "retard ${first.delayMinutes} min" else null,
        )
        return if (details.isEmpty()) "" else " (${details.joinToString(", ")})"
    }

    private fun String.capitalized(): String = replaceFirstChar { it.uppercaseChar() }

    /** « Vers Châtillon – Montrouge : 3 min (voie 2), puis 9 min » */
    fun directionLine(group: DirectionGroup): String {
        val then = group.next?.let { ", puis ${wait(it)}" }.orEmpty()
        return "Vers ${group.label} : ${wait(group.first)}${details(group.first)}$then"
    }

    /** « Métro 13, Brochant · 120 m : Châtillon 3 min · Saint-Denis 5 min » */
    fun proximityLine(row: ProximityRow): String {
        val name = row.line?.let(UiText::lineTitle) ?: row.badge.label
        val directions = row.directions.joinToString(" · ") { "${compact(it.label)} ${wait(it.first)}" }
            .ifEmpty { "aucun passage" }
        return "$name, ${row.place} : $directions"
    }

    /** Attente telle qu'on la lit dans une phrase : « 3 min », « 17:05 », « à quai »… */
    fun wait(passage: Passage): String = when (passage.kind) {
        WaitKind.MINUTES -> "${max(0, passage.minutes)} min"
        WaitKind.CLOCK -> passage.clock
        WaitKind.IMMINENT -> "imminent"
        WaitKind.AT_STOP -> passage.atStopLabel.lowercase()
        WaitKind.CANCELLED -> "supprimé"
    }

    private fun compact(name: String): String =
        WidgetContent.compactName(name) { it.length <= COMPACT_NAME_LENGTH }

    /** Heure du relevé, à lire en un coup d'œil : « PRIM via Tracéo 00:58 », « 00:58 », « Non actualisé, 00:58 ». */
    private fun shortSourceOf(model: WidgetModel): String? {
        val time = model.timeLabel.takeIf { it.isNotEmpty() && !model.refreshing } ?: return null
        return when {
            model.stale -> "Non actualisé, $time"
            model.sourceDetail != null -> "${model.sourceDetail} $time" + if (model.oldSnapshot) ", ancien" else ""
            else -> time
        }
    }

    private fun summaryOf(model: WidgetModel): String? {
        val time = model.timeLabel.takeIf { it.isNotEmpty() && !model.refreshing } ?: return null
        return when {
            model.stale -> "Actualisation impossible, données de $time"
            model.sourceDetail != null -> {
                val old = if (model.oldSnapshot) ", ancien" else ""
                "${model.sourceDetail} · relevé de $time$old"
            }
            else -> "Actualisé à $time"
        }
    }
}
