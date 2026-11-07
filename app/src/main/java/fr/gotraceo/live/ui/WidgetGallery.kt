package fr.gotraceo.live.ui

import fr.gotraceo.live.R
import fr.gotraceo.live.data.WidgetTheme
import fr.gotraceo.live.widget.WidgetEntry
import fr.gotraceo.live.widget.WidgetFormat
import fr.gotraceo.live.widget.WidgetProviders

/** Une rubrique de la galerie des formats. */
class GallerySection(val titleRes: Int, val entries: List<WidgetEntry>)

/** Rangement des formats dans la galerie : par usage, plutôt que par taille. */
object WidgetGallery {

    val sections: List<GallerySection> = listOf(
        section(R.string.gallery_position, WidgetFormat.PROXIMITE),
        section(R.string.gallery_two_ways, WidgetFormat.DEUX_SENS_LARGE, WidgetFormat.COMPACT, WidgetFormat.DEUX_SENS),
        section(R.string.gallery_one_way, WidgetFormat.FRISE, WidgetFormat.GRAND_CHIFFRE, WidgetFormat.UN_SENS, WidgetFormat.PASTILLE),
        section(R.string.gallery_rail, WidgetFormat.TRAIN, WidgetFormat.LISTE),
    )

    /** Le widget sans format imposé, proposé à part car il n'a pas d'aperçu propre. */
    val adaptive: WidgetEntry = WidgetProviders.catalog.first { it.format == null }

    private fun section(titleRes: Int, vararg formats: WidgetFormat) =
        GallerySection(titleRes, formats.map { format -> WidgetProviders.catalog.first { it.format == format } })
}

/** Couleurs proposées pour un widget. */
object ThemeChoices {

    private val ALL = listOf(WidgetTheme.AUTO, WidgetTheme.DARK, WidgetTheme.LIGHT, WidgetTheme.LINE)

    /** La couleur de ligne n'est pas proposée sur la liste : ses retards en couleur seraient illisibles. */
    fun available(format: WidgetFormat?): List<WidgetTheme> =
        ALL.filter { it != WidgetTheme.LINE || format != WidgetFormat.LISTE }

    /** La couleur à cocher en ouvrant l'écran : celle du widget si elle est encore proposée. */
    fun initial(saved: WidgetTheme?, format: WidgetFormat?): WidgetTheme {
        val fallback = WidgetFormat.defaultTheme(format)
        val wanted = saved ?: fallback
        return if (wanted in available(format)) wanted else fallback
    }
}
