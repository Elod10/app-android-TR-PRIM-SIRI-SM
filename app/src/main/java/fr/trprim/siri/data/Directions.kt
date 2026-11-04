package fr.trprim.siri.data

/**
 * Un choix de sens proposé à l'utilisateur.
 *
 * @param stopId point d'arrêt du référentiel (quai) concerné
 * @param key clé de sens ([Departure.directionKey]) ou null si aucun passage n'est connu
 *            actuellement pour ce point d'arrêt (on retiendra alors tous ses passages)
 */
data class DirectionOption(
    val stopId: String,
    val key: String?,
    val title: String,
    val subtitle: String?,
)

/** Logique de choix et de filtrage des sens (sans dépendance Android). */
object Directions {

    /**
     * Construit la liste des sens possibles à partir des passages observés en temps réel
     * sur chacun des points d'arrêt de l'arrêt choisi.
     *
     * @param results passages par point d'arrêt ; null si la requête a échoué
     */
    fun buildOptions(stop: Stop, results: Map<String, List<Departure>?>): List<DirectionOption> {
        val multi = stop.stopIds.size > 1
        val options = ArrayList<DirectionOption>()
        stop.stopIds.forEachIndexed { index, stopId ->
            val pointLabel = if (multi) "Point d'arrêt ${index + 1}" else null
            val deps = results[stopId]
            when {
                deps == null -> options += DirectionOption(
                    stopId, null,
                    title = pointLabel ?: "Tous les passages",
                    subtitle = "Sens inconnu : impossible de récupérer les passages",
                )
                deps.isEmpty() -> options += DirectionOption(
                    stopId, null,
                    title = pointLabel ?: "Tous les passages",
                    subtitle = "Aucun passage en ce moment : sens inconnu",
                )
                else -> {
                    val groups = LinkedHashMap<String, MutableList<Departure>>()
                    deps.sortedBy { it.displayTime ?: Long.MAX_VALUE }
                        .forEach { groups.getOrPut(normalizeLabel(it.directionKey)) { ArrayList() }.add(it) }
                    groups.values.forEach { group ->
                        val key = group.first().directionKey
                        val destinations = group.map { it.destination }
                            .distinctBy(::normalizeLabel)
                            .filter { normalizeLabel(it) != normalizeLabel(key) }
                        val platforms = group.mapNotNull { it.platform }.distinctBy(::normalizeLabel)
                        val details = ArrayList<String>()
                        if (destinations.isNotEmpty()) details += "Destinations : " + destinations.joinToString(", ")
                        if (platforms.isNotEmpty()) details += "Voie/quai " + platforms.joinToString(", ")
                        pointLabel?.let { details += it }
                        options += DirectionOption(
                            stopId, key,
                            title = "Vers $key",
                            subtitle = details.joinToString(" · ").ifEmpty { null },
                        )
                    }
                }
            }
        }
        return options
    }

    /** Sélection « tous les sens » : tous les points d'arrêt, sans filtre. */
    fun allDirections(stop: Stop): Map<String, Set<String>> =
        stop.stopIds.associateWith { emptySet<String>() }

    /** Convertit les options cochées en filtre enregistrable dans [WidgetConfig.directions]. */
    fun toSelection(options: List<DirectionOption>, selected: Collection<DirectionOption>): Map<String, Set<String>> {
        val result = LinkedHashMap<String, Set<String>>()
        options.groupBy { it.stopId }.forEach { (stopId, stopOptions) ->
            val chosen = stopOptions.filter { it in selected }
            if (chosen.isEmpty()) return@forEach
            result[stopId] = if (chosen.any { it.key == null } || chosen.size == stopOptions.size) {
                emptySet()
            } else {
                chosen.mapNotNull { it.key }.toCollection(LinkedHashSet())
            }
        }
        return result
    }

    /** Libellé court du sens retenu, affiché dans le widget. */
    fun label(options: List<DirectionOption>, selected: Collection<DirectionOption>, stop: Stop): String {
        if (selected.isEmpty()) return ALL_DIRECTIONS
        val selection = toSelection(options, selected)
        val coversAll = stop.stopIds.all { selection[it]?.isEmpty() == true }
        val keyed = options.filter { it.key != null }
        if (coversAll && (keyed.isEmpty() || selected.containsAll(keyed))) return ALL_DIRECTIONS
        val names = selected.mapNotNull { it.key }.distinctBy(::normalizeLabel)
        if (names.isEmpty()) return selected.joinToString(", ") { it.title }
        return "→ " + names.joinToString(", ")
    }

    /** Applique le filtre de sens d'un widget aux passages récupérés. */
    fun filter(departures: List<Departure>, selection: Map<String, Set<String>>): List<Departure> {
        if (selection.isEmpty()) return departures
        return departures.filter { d ->
            val keys = selection[d.stopId] ?: return@filter false
            keys.isEmpty() || keys.any { normalizeLabel(it) == normalizeLabel(d.directionKey) }
        }
    }

    /** Passages à venir triés par heure (on garde les véhicules encore à quai). */
    fun upcoming(departures: List<Departure>, now: Long, graceMs: Long = 60_000L): List<Departure> =
        departures
            .filter { d ->
                val leave = d.leaveTime ?: return@filter false
                d.atStop || leave >= now - graceMs
            }
            .distinctBy { Triple(normalizeLabel(it.destination), it.displayTime, it.stopId) }
            .sortedBy { it.displayTime ?: Long.MAX_VALUE }

    const val ALL_DIRECTIONS = "Tous les sens"
}
