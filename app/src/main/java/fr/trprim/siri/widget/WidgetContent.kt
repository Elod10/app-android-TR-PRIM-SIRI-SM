package fr.trprim.siri.widget

import fr.trprim.siri.data.DataSources
import fr.trprim.siri.data.Departure
import fr.trprim.siri.data.Directions
import fr.trprim.siri.data.Line
import fr.trprim.siri.data.PassageRoute
import fr.trprim.siri.data.TraceoClient
import fr.trprim.siri.data.WidgetConfig
import fr.trprim.siri.data.WidgetState
import fr.trprim.siri.data.WidgetTheme
import fr.trprim.siri.data.normalizeLabel
import kotlin.math.max

/**
 * Les neuf formats du widget. Les dimensions sont celles de la maquette (en dp) : le dessin
 * part de ces valeurs et s'adapte ensuite à la taille réelle attribuée par le lanceur.
 */
enum class WidgetFormat(
    val baseWidth: Float,
    val baseHeight: Float,
    val radius: Float,
    /** Vrai pour les formats à grand chiffre, en couleur de ligne avec le thème automatique. */
    val lineColorByDefault: Boolean,
) {
    PASTILLE(84f, 92f, 22f, true),
    UN_SENS(172f, 92f, 24f, false),
    GRAND_CHIFFRE(172f, 192f, 28f, true),
    FRISE(348f, 92f, 28f, false),
    COMPACT(348f, 92f, 28f, false),
    DEUX_SENS(172f, 192f, 28f, false),
    TRAIN(172f, 192f, 28f, false),
    DEUX_SENS_LARGE(348f, 192f, 28f, true),
    LISTE(348f, 192f, 28f, false);

    /** Vrai pour les formats pensés pour afficher les deux sens côte à côte. */
    val showsBothDirections: Boolean
        get() = this == COMPACT || this == DEUX_SENS || this == DEUX_SENS_LARGE

    /** Vrai pour les formats qui n'affichent qu'un sens : la configuration n'en accepte qu'un. */
    val followsOneDirection: Boolean
        get() = this == PASTILLE || this == UN_SENS || this == GRAND_CHIFFRE || this == FRISE || this == TRAIN

    companion object {
        private const val NARROW_BELOW = 130f
        private const val WIDE_FROM = 250f
        private const val TALL_FROM = 150f
        private const val VERY_TALL_FROM = 250f

        /** Choisit le format selon la taille du widget, le type de ligne et le sens configuré. */
        fun select(widthDp: Float, heightDp: Float, rail: Boolean, singleDirection: Boolean): WidgetFormat {
            val tall = heightDp >= TALL_FROM
            return when {
                widthDp < NARROW_BELOW -> PASTILLE
                widthDp < WIDE_FROM -> when {
                    !tall -> UN_SENS
                    !singleDirection -> DEUX_SENS
                    rail -> TRAIN
                    else -> GRAND_CHIFFRE
                }
                !tall -> if (singleDirection) FRISE else COMPACT
                rail || singleDirection || heightDp >= VERY_TALL_FROM -> LISTE
                else -> DEUX_SENS_LARGE
            }
        }
    }
}

enum class WaitKind { MINUTES, CLOCK, IMMINENT, AT_STOP, CANCELLED }

/** Un passage prêt à être affiché. */
data class Passage(
    val kind: WaitKind,
    val minutes: Int,
    val clock: String,
    val destination: String,
    val directionKey: String,
    val stopId: String,
    val platform: String?,
    val platformLabel: String,
    /** Code mission (trains uniquement). */
    val mission: String?,
    val delayMinutes: Int,
    val atStopLabel: String,
) {
    /** Le retard n'est signalé en couleur qu'à partir de 2 minutes. */
    val isDelayed: Boolean get() = kind != WaitKind.CANCELLED && delayMinutes >= 2

    /** Position du passage sur la frise. */
    val railMinutes: Int
        get() = when (kind) {
            WaitKind.IMMINENT, WaitKind.AT_STOP -> 0
            else -> max(0, minutes)
        }

    /** Attente abrégée : « 5 », « <1 » ou « 00:12 ». */
    val shortWait: String
        get() = when (kind) {
            WaitKind.IMMINENT, WaitKind.AT_STOP -> "<1"
            WaitKind.CLOCK -> clock
            else -> max(0, minutes).toString()
        }

    /** Libellé du passage suivant : « puis 5 » ou, avec l'unité, « puis 5 min ». */
    fun thenLabel(withUnit: Boolean = false): String {
        val unit = if (withUnit && kind != WaitKind.CLOCK) " min" else ""
        return "puis $shortWait$unit"
    }

    /** Libellé du passage suivant qui tient dans la place disponible : « puis 5 », sinon « 5 ». */
    fun thenLabelFitting(fits: (String) -> Boolean): String? = listOf(thenLabel(), shortWait).firstOrNull(fits)

    /** Détail lisible à voix haute (lecteurs d'écran). */
    val spokenWait: String
        get() = when (kind) {
            WaitKind.MINUTES -> if (minutes <= 1) "$minutes minute" else "$minutes minutes"
            WaitKind.CLOCK -> "à $clock"
            WaitKind.IMMINENT -> "imminent"
            WaitKind.AT_STOP -> atStopLabel.lowercase()
            WaitKind.CANCELLED -> "supprimé"
        }
}

/** Les passages d'un sens ; [passages] n'est jamais vide. */
data class DirectionGroup(val label: String, val passages: List<Passage>) {
    val first: Passage get() = passages.first()

    /** Prochain passage maintenu après [first]. */
    val next: Passage? get() = passages.drop(1).firstOrNull { it.kind != WaitKind.CANCELLED }

    /** Passages à placer sur la frise. */
    val railPassages: List<Passage> get() = WidgetContent.railOf(first, passages.drop(1))
}

enum class BadgeShape { CIRCLE, SQUARE, BUS, TRAM }

data class LineBadge(val label: String, val shape: BadgeShape)

enum class NoticeKind { NOT_CONFIGURED, RECONFIGURE, LOADING, NO_DEPARTURE, ERROR }

/** Message affiché à la place des passages. */
data class Notice(val kind: NoticeKind, val detail: String? = null)

/** Tout ce qu'il faut pour dessiner un widget. */
data class WidgetModel(
    val format: WidgetFormat,
    val palette: Palette,
    val line: LineBadge?,
    val stopName: String,
    val passages: List<Passage>,
    val groups: List<DirectionGroup>,
    /** Heure du relevé ou, en direct, de la dernière actualisation ; « … » pendant une actualisation. */
    val timeLabel: String,
    /** Vrai si les passages affichés datent d'avant un échec d'actualisation. */
    val stale: Boolean,
    val refreshing: Boolean,
    val notice: Notice?,
    /** Source à citer à côté de l'heure, quand les passages viennent d'un relevé et non du direct. */
    val sourceLabel: String? = null,
    /** Vrai si le relevé dépasse le délai habituel de la source. */
    val oldSnapshot: Boolean = false,
)

object WidgetContent {

    private const val MAX_RAIL_STOPS = 4
    private const val RAIL_HORIZON_MINUTES = 60

    private val parenthesis = Regex("\\s*\\([^)]*\\)")
    private val genericPrefix =
        Regex("^(Château|Porte|Mairie|Gare|Pont|Parc|Place|Cité)\\s+(de la\\s+|de l['’]|du\\s+|des\\s+|de\\s+|d['’])", RegexOption.IGNORE_CASE)
    private val tokenSeparator = Regex("[\\s\\-–]+")

    fun passage(d: Departure, now: Long, mode: String): Passage {
        val time = d.displayTime
        val minutes = if (time == null) 0 else Math.floorDiv(time - now, 60_000L).toInt()
        val kind = when {
            d.isCancelled -> WaitKind.CANCELLED
            d.atStop -> WaitKind.AT_STOP
            minutes <= 0 -> WaitKind.IMMINENT
            minutes < 60 -> WaitKind.MINUTES
            else -> WaitKind.CLOCK
        }
        val rail = mode == "rail"
        return Passage(
            kind = kind,
            minutes = minutes,
            clock = time?.let { DepartureFormat.clock(it) }.orEmpty(),
            destination = d.destination,
            directionKey = d.directionKey,
            stopId = d.stopId,
            platform = d.platform,
            platformLabel = if (rail) "Voie" else "Quai",
            mission = if (rail) d.journeyNote else null,
            delayMinutes = d.delayMinutes,
            atStopLabel = if (rail || mode == "metro") "À quai" else "À l'arrêt",
        )
    }

    /**
     * Regroupe les passages par sens et garde les deux sens les plus proches, toujours dans le
     * même ordre pour que les colonnes ne s'échangent pas d'une actualisation à l'autre.
     */
    fun directionGroups(passages: List<Passage>): List<DirectionGroup> {
        var groups = passages.groupBy { normalizeLabel(it.directionKey) }.values
        // Plusieurs destinations par sens (branches du RER) : un quai correspond alors à un sens.
        if (groups.size > 2 && passages.map { it.stopId }.distinct().size >= 2) {
            groups = passages.groupBy { it.stopId }.values
        }
        return groups
            .sortedBy { group -> group.firstOrNull { it.kind != WaitKind.CANCELLED }?.railMinutes ?: Int.MAX_VALUE }
            .take(2)
            .map { DirectionGroup(shortName(it.first().destination), it) }
            .sortedBy { normalizeLabel(it.label) }
    }

    /** Prochain passage maintenu dans le même sens que [first]. */
    fun nextPassage(passages: List<Passage>, first: Passage): Passage? =
        followers(passages, first).firstOrNull()

    /** Passages à placer sur la frise : le prochain, puis ceux qui le suivent dans le même sens. */
    fun railPassages(passages: List<Passage>, first: Passage): List<Passage> =
        railOf(first, followers(passages, first))

    /** Limite la frise aux passages de l'heure à venir, sans les trains supprimés. */
    fun railOf(first: Passage, following: List<Passage>): List<Passage> =
        listOf(first) + following
            .filter { it.kind != WaitKind.CANCELLED && it.kind != WaitKind.CLOCK && it.minutes < RAIL_HORIZON_MINUTES }
            .take(MAX_RAIL_STOPS - 1)

    private fun followers(passages: List<Passage>, first: Passage): List<Passage> {
        val key = normalizeLabel(first.directionKey)
        return passages
            .dropWhile { it !== first }
            .drop(1)
            .filter { it.kind != WaitKind.CANCELLED && normalizeLabel(it.directionKey) == key }
    }

    /**
     * Abscisses des points de la frise.
     *
     * @param minutes attentes triées, la première étant le prochain passage
     * @param span durée représentée, commune aux frises d'un même widget
     * @param secondMin abscisse minimale du deuxième point, pour ne pas toucher le grand chiffre
     * @param minGap écart minimal entre deux points, pour que leurs libellés restent lisibles
     * @return les abscisses des points qui tiennent avant [end]
     */
    fun railCenters(
        minutes: List<Int>,
        start: Float,
        end: Float,
        span: Int,
        minGap: Float,
        secondMin: Float = start + minGap,
    ): List<Float> {
        if (minutes.isEmpty()) return emptyList()
        val perMinute = if (span > 0) (end - start) / span else 0f
        val centers = arrayListOf(start)
        for (i in 1 until minutes.size) {
            val ideal = start + (minutes[i] - minutes[0]) * perMinute
            val floor = if (i == 1) secondMin else centers.last() + minGap
            val x = max(ideal, floor)
            if (x > end + 0.5f) break
            centers += x
        }
        return centers
    }

    /** Retire les précisions entre parenthèses : « La Défense (Grande Arche) » → « La Défense ». */
    fun shortName(name: String): String = name.replace(parenthesis, "").trim().ifEmpty { name }

    /**
     * Version courte d'un nom pour les très petits formats : « Château de Vincennes » →
     * « Vincennes », « Boissy-Saint-Léger » → « Boissy ». Renvoie la première version qui tient.
     */
    fun compactName(name: String, fits: (String) -> Boolean): String {
        val short = shortName(name)
        if (fits(short)) return short
        val withoutPrefix = short.replace(genericPrefix, "").trim().ifEmpty { short }
        if (fits(withoutPrefix)) return withoutPrefix
        val firstToken = withoutPrefix.split(tokenSeparator).firstOrNull().orEmpty()
        return if (firstToken.length >= 3 && fits(firstToken)) firstToken else withoutPrefix
    }

    /** Coupe un texte trop long en terminant par « … ». */
    fun ellipsize(text: String, maxWidth: Float, measure: (String) -> Float): String {
        if (measure(text) <= maxWidth) return text
        var end = text.length
        while (end > 0) {
            val candidate = text.substring(0, end).trimEnd() + "…"
            if (measure(candidate) <= maxWidth) return candidate
            end--
        }
        return "…"
    }

    /** Répartit un texte sur plusieurs lignes ; la dernière est coupée si elle ne tient pas. */
    fun wrapLines(text: String, maxWidth: Float, maxLines: Int, measure: (String) -> Float): List<String> {
        val words = text.trim().split(Regex("\\s+"))
        val lines = ArrayList<String>()
        var index = 0
        while (index < words.size && lines.size < maxLines) {
            if (lines.size == maxLines - 1) {
                lines += ellipsize(words.subList(index, words.size).joinToString(" "), maxWidth, measure)
                return lines
            }
            var current = words[index++]
            while (index < words.size && measure("$current ${words[index]}") <= maxWidth) {
                current += " " + words[index++]
            }
            lines += ellipsize(current, maxWidth, measure)
        }
        return lines
    }

    /** Résumé du widget pour les lecteurs d'écran. */
    fun describe(model: WidgetModel): String {
        val parts = ArrayList<String>()
        model.line?.let { parts += "Ligne ${it.label}" }
        if (model.stopName.isNotEmpty()) parts += model.stopName
        val header = parts.joinToString(", ")
        val body = model.groups.joinToString(" ") { group ->
            val then = group.next?.let { ", puis ${it.spokenWait}" }.orEmpty()
            val delay = if (group.first.isDelayed) ", retard de ${group.first.delayMinutes} minutes" else ""
            "Vers ${group.label} : ${group.first.spokenWait}$delay$then."
        }
        val stale = if (model.stale) " Actualisation impossible, données de ${model.timeLabel}." else ""
        return "$header. $body$stale${describeSource(model)}".trim()
    }

    private fun describeSource(model: WidgetModel): String {
        val source = model.sourceLabel ?: return ""
        val time = model.timeLabel.takeIf { it.isNotEmpty() }?.let { " de $it" }.orEmpty()
        val old = if (model.oldSnapshot) ", ancien" else ""
        return " Relevé $source$time$old."
    }
}

/** Assemble le modèle d'un widget à partir de sa configuration et de son dernier état. */
object WidgetModels {

    fun build(
        config: WidgetConfig?,
        state: WidgetState?,
        hasApiKey: Boolean,
        refreshing: Boolean,
        widthDp: Float,
        heightDp: Float,
        now: Long,
        fixedFormat: WidgetFormat? = null,
    ): WidgetModel {
        val line = config?.line
        val rail = line?.mode == "rail"
        val format = fixedFormat ?: WidgetFormat.select(widthDp, heightDp, rail, config?.isSingleDirection ?: true)
        val palette = Palettes.resolve(config?.theme ?: WidgetTheme.AUTO, format, line)

        val upcoming = if (config != null && state != null) Directions.upcoming(state.departures, now) else emptyList()
        val passages = upcoming.map { WidgetContent.passage(it, now, line?.mode.orEmpty()) }
        val notice = when {
            config == null -> Notice(NoticeKind.NOT_CONFIGURED)
            DataSources.route(config.stop.stopIds, hasApiKey) == PassageRoute.RECONFIGURE -> Notice(NoticeKind.RECONFIGURE)
            passages.isNotEmpty() -> null
            state == null -> Notice(NoticeKind.LOADING)
            state.error != null -> Notice(NoticeKind.ERROR, state.error)
            else -> Notice(NoticeKind.NO_DEPARTURE)
        }
        // Un relevé se date à l'heure de la source, pas à celle du téléphone.
        val snapshot = state?.takeIf { it.isSnapshot }
        val shownAt = if (snapshot != null) snapshot.dataAt ?: 0L else state?.fetchedAt ?: 0L
        return WidgetModel(
            format = format,
            palette = palette,
            line = line?.let(::badgeOf),
            stopName = config?.stop?.name.orEmpty(),
            passages = passages,
            groups = WidgetContent.directionGroups(passages),
            timeLabel = when {
                refreshing -> "…"
                shownAt > 0L -> DepartureFormat.clock(shownAt)
                else -> ""
            },
            stale = state?.error != null && passages.isNotEmpty(),
            refreshing = refreshing,
            notice = notice,
            sourceLabel = if (snapshot != null && !refreshing) TraceoClient.SOURCE_NAME else null,
            oldSnapshot = snapshot?.fresh == false && !refreshing,
        )
    }

    private fun badgeOf(line: Line): LineBadge {
        val raw = line.shortName.trim().ifEmpty { line.name.trim() }
        val label = (if (line.isRer()) raw.removePrefix("RER").trim().ifEmpty { raw } else raw).take(7)
        val shape = when {
            line.mode == "metro" || line.isRer() -> BadgeShape.CIRCLE
            line.mode == "rail" -> BadgeShape.SQUARE
            line.mode == "tram" -> BadgeShape.TRAM
            else -> BadgeShape.BUS
        }
        return LineBadge(label, shape)
    }
}
