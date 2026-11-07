package fr.gotraceo.live.widget

import fr.gotraceo.live.ui.UiText
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
    /** Heure du relevé, courte, dans l'en-tête de la notification repliée : « Tracéo 00:58 ». */
    val subText: String?,
    /** Source et heure en toutes lettres, dans l'en-tête de la notification dépliée. */
    val summary: String?,
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
    /** Prochain passage dessiné à la place de l'icône, ou null pour l'icône de l'application. */
    val icon: TileIcon? = null,
)

/**
 * Icône de la tuile : le prochain passage, seul contenu visible quand la tuile est petite.
 * « 24 » au-dessus de « min », « <1 » pour un passage imminent, ou une heure seule, « 17:05 ».
 */
data class TileIcon(val value: String, val unit: String?)

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

    /**
     * Texte de la tuile des réglages rapides : deux lignes courtes, l'attente en premier.
     * Un sens : « 3 min, puis 9 » / « Métro 13 → Châtillon ». Deux sens : un sens par ligne.
     */
    fun tile(model: WidgetModel, lineTitle: String?, noticeText: (Notice) -> String?): TileText? =
        tileText(model, lineTitle, noticeText)?.copy(icon = tileIcon(model))

    private fun tileText(model: WidgetModel, lineTitle: String?, noticeText: (Notice) -> String?): TileText? {
        val full = build(model, lineTitle, noticeText) ?: return null
        val description = (listOf(full.expandedTitle) + full.lines).joinToString(". ")
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
        val first = nextPassage(model) ?: return null
        return when (first.kind) {
            WaitKind.MINUTES -> "${max(0, first.minutes)} min"
            WaitKind.CLOCK -> first.clock
            WaitKind.IMMINENT, WaitKind.AT_STOP -> "<1 min"
            WaitKind.CANCELLED -> null
        }
    }

    /** Prochain passage écrit dans l'icône de la tuile. Null quand il n'y a pas de passage à annoncer. */
    fun tileIcon(model: WidgetModel): TileIcon? {
        val first = nextPassage(model) ?: return null
        return when (first.kind) {
            WaitKind.MINUTES -> TileIcon("${max(0, first.minutes)}", "min")
            WaitKind.CLOCK -> TileIcon(first.clock, null)
            WaitKind.IMMINENT, WaitKind.AT_STOP -> TileIcon("<1", "min")
            WaitKind.CANCELLED -> null
        }
    }

    /** Le passage le plus proche, tous sens confondus ; pour À proximité, celui de la première ligne. */
    private fun nextPassage(model: WidgetModel): Passage? {
        if (model.notice != null) return null
        return if (model.format == WidgetFormat.PROXIMITE) {
            model.proximity.firstNotNullOfOrNull { it.directions.firstOrNull()?.first }
        } else {
            model.groups.mapNotNull { group -> group.passages.firstOrNull { it.kind != WaitKind.CANCELLED } }
                .minByOrNull { it.railMinutes }
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

    /** Heure du relevé, à lire en un coup d'œil : « Tracéo 00:58 », ou « PRIM 00:58 » avec une clé ; « Non actualisé, 00:58 ». */
    private fun shortSourceOf(model: WidgetModel): String? {
        val time = model.timeLabel.takeIf { it.isNotEmpty() && !model.refreshing } ?: return null
        return when {
            model.stale -> "Non actualisé, $time"
            model.sourceLabel != null -> "${model.sourceLabel} $time" + if (model.oldSnapshot) ", ancien" else ""
            else -> "PRIM $time"
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
