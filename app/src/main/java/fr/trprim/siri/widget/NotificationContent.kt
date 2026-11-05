package fr.trprim.siri.widget

import fr.trprim.siri.ui.UiText
import kotlin.math.max

/** Texte de la notification d'un widget, qui reste affichée sur l'écran de verrouillage. */
data class NotificationText(
    val title: String,
    /** Ligne affichée quand la notification est repliée. */
    val text: String,
    /** Une ligne par sens (ou par ligne de transport, pour À proximité), quand elle est dépliée. */
    val lines: List<String>,
    /** Source et heure des passages, dans l'en-tête de la notification. */
    val subText: String?,
)

/** Texte de la tuile des réglages rapides. */
data class TileText(
    val label: String,
    /** Seconde ligne, affichée depuis Android 10. */
    val subtitle: String?,
    /** Tout le contenu, pour les lecteurs d'écran. */
    val description: String,
    /** Faux quand la tuile n'a pas de passage à montrer : elle apparaît alors éteinte. */
    val active: Boolean,
)

/** Met en mots le contenu d'un widget, pour sa notification et pour la tuile des réglages rapides. */
object NotificationContent {

    /** Longueur au-delà de laquelle un nom de sens est raccourci dans la notification repliée. */
    private const val COMPACT_NAME_LENGTH = 16

    /**
     * @param lineTitle nom complet de la ligne, « Métro 13 » ou « RER B »
     * @param noticeText texte affiché à la place des passages, ou null si le widget n'a rien à montrer
     * @return null quand il n'y a pas de notification à afficher (widget non configuré)
     */
    fun build(model: WidgetModel, lineTitle: String?, noticeText: (Notice) -> String?): NotificationText? {
        if (model.notice?.kind == NoticeKind.NOT_CONFIGURED) return null
        val proximity = model.format == WidgetFormat.PROXIMITE
        val title = if (proximity) model.stopName else titleOf(model, lineTitle)
        val lines = when {
            model.notice != null -> listOfNotNull(noticeText(model.notice))
            proximity -> model.proximity.map(::proximityLine)
            else -> model.groups.map(::directionLine)
        }
        if (lines.isEmpty()) return null
        val text = when {
            model.notice != null || proximity || model.groups.size < 2 -> lines.first()
            else -> model.groups.joinToString(" · ") { group ->
                "${compact(group.label)} ${wait(group.first)}"
            }
        }
        return NotificationText(title, text, lines, subTextOf(model))
    }

    /** « Métro 13 · Brochant » : la pastille seule ne suffit pas toujours, l'icône pouvant être masquée. */
    private fun titleOf(model: WidgetModel, lineTitle: String?): String {
        val line = lineTitle ?: model.line?.label
        return listOfNotNull(line, model.stopName.takeIf { it.isNotEmpty() }).joinToString(" · ")
    }

    /** « Vers Châtillon – Montrouge : 3 min (voie 2), puis 9 min » */
    fun directionLine(group: DirectionGroup): String {
        val first = group.first
        val details = listOfNotNull(
            first.platform?.trim()?.takeIf { it.isNotEmpty() && first.kind != WaitKind.CANCELLED }
                ?.let { "${first.platformLabel.lowercase()} $it" },
            if (first.isDelayed) "retard ${first.delayMinutes} min" else null,
        )
        val detail = if (details.isEmpty()) "" else " (${details.joinToString(", ")})"
        val then = group.next?.let { ", puis ${wait(it)}" }.orEmpty()
        return "Vers ${group.label} : ${wait(first)}$detail$then"
    }

    /** « Métro 13, Brochant · 120 m : Châtillon 3 min · Saint-Denis 5 min » */
    fun proximityLine(row: ProximityRow): String {
        val name = row.line?.let(UiText::lineTitle) ?: row.badge.label
        val directions = row.directions.joinToString(" · ") { "${compact(it.label)} ${wait(it.first)}" }
            .ifEmpty { "aucun passage" }
        return "$name, ${row.place} : $directions"
    }

    /**
     * Texte de la tuile des réglages rapides : deux lignes courtes, l'attente en premier.
     * Un sens : « 3 min, puis 9 » / « Métro 13 → Châtillon ». Deux sens : un sens par ligne.
     */
    fun tile(model: WidgetModel, lineTitle: String?, noticeText: (Notice) -> String?): TileText? {
        val full = build(model, lineTitle, noticeText) ?: return null
        val description = (listOf(full.title) + full.lines).joinToString(". ")
        val line = lineTitle ?: model.line?.label
        model.notice?.let { notice ->
            return TileText(line ?: model.stopName, noticeText(notice), description, active = false)
        }
        if (model.format == WidgetFormat.PROXIMITE) {
            val row = model.proximity.firstOrNull { it.directions.isNotEmpty() }
                ?: return TileText(model.stopName, full.text, description, active = false)
            val direction = row.directions.first()
            val name = row.line?.let(UiText::lineTitle) ?: row.badge.label
            return TileText("$name · ${wait(direction.first)}", "vers ${compact(direction.label)}", description, active = true)
        }
        val groups = model.groups
        return if (groups.size >= 2) {
            TileText(
                "${compact(groups[0].label)} ${wait(groups[0].first)}",
                "${compact(groups[1].label)} ${wait(groups[1].first)}",
                description,
                active = true,
            )
        } else {
            val group = groups.firstOrNull() ?: return TileText(line ?: model.stopName, full.text, description, active = false)
            val then = group.next?.let { ", ${it.thenLabel()}" }.orEmpty()
            val towards = listOfNotNull(line, compact(group.label)).joinToString(" → ")
            TileText("${wait(group.first)}$then", towards, description, active = true)
        }
    }

    /**
     * Texte de la pastille d'une Live Update dans la barre d'état : le prochain passage, très court.
     * Null quand il n'y a pas de passage à annoncer.
     */
    fun chip(model: WidgetModel): String? {
        if (model.notice != null) return null
        val first = if (model.format == WidgetFormat.PROXIMITE) {
            model.proximity.firstNotNullOfOrNull { it.directions.firstOrNull()?.first }
        } else {
            model.groups.mapNotNull { group -> group.passages.firstOrNull { it.kind != WaitKind.CANCELLED } }
                .minByOrNull { it.railMinutes }
        } ?: return null
        return when (first.kind) {
            WaitKind.MINUTES -> "${max(0, first.minutes)} min"
            WaitKind.CLOCK -> first.clock
            WaitKind.IMMINENT, WaitKind.AT_STOP -> "<1 min"
            WaitKind.CANCELLED -> null
        }
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

    private fun subTextOf(model: WidgetModel): String? {
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
