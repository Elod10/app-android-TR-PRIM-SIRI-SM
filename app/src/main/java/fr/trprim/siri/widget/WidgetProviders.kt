package fr.trprim.siri.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import fr.trprim.siri.R

// Le lanceur propose une entrée par fournisseur de widget : chaque format a donc le sien, en plus
// de DeparturesWidgetProvider, l'entrée automatique qui choisit le format selon la taille du widget.
class PastilleWidgetProvider : DeparturesWidgetProvider()
class UnSensWidgetProvider : DeparturesWidgetProvider()
class GrandChiffreWidgetProvider : DeparturesWidgetProvider()
class FriseWidgetProvider : DeparturesWidgetProvider()
class CompactWidgetProvider : DeparturesWidgetProvider()
class DeuxSensWidgetProvider : DeparturesWidgetProvider()
class DeuxSensLargeWidgetProvider : DeparturesWidgetProvider()
class TrainWidgetProvider : DeparturesWidgetProvider()
class ListeWidgetProvider : DeparturesWidgetProvider()
class ProximiteWidgetProvider : DeparturesWidgetProvider()

/** Un widget proposé à l'utilisateur. */
class WidgetEntry(
    val provider: Class<out DeparturesWidgetProvider>,
    /** Format imposé, ou null pour l'entrée automatique. */
    val format: WidgetFormat?,
    val nameRes: Int,
    val descriptionRes: Int,
    val previewRes: Int,
    /** Taille de départ, en cellules de l'écran d'accueil. */
    val size: String,
)

object WidgetProviders {

    /** Les widgets de l'application, dans l'ordre où l'application les propose. */
    val catalog: List<WidgetEntry> = listOf(
        WidgetEntry(
            PastilleWidgetProvider::class.java, WidgetFormat.PASTILLE,
            R.string.widget_pastille_name, R.string.widget_pastille_description, R.drawable.preview_pastille, "1×1",
        ),
        WidgetEntry(
            UnSensWidgetProvider::class.java, WidgetFormat.UN_SENS,
            R.string.widget_un_sens_name, R.string.widget_un_sens_description, R.drawable.preview_un_sens, "2×1",
        ),
        WidgetEntry(
            GrandChiffreWidgetProvider::class.java, WidgetFormat.GRAND_CHIFFRE,
            R.string.widget_grand_chiffre_name, R.string.widget_grand_chiffre_description, R.drawable.preview_grand_chiffre, "2×2",
        ),
        WidgetEntry(
            FriseWidgetProvider::class.java, WidgetFormat.FRISE,
            R.string.widget_frise_name, R.string.widget_frise_description, R.drawable.preview_frise, "4×1",
        ),
        WidgetEntry(
            CompactWidgetProvider::class.java, WidgetFormat.COMPACT,
            R.string.widget_compact_name, R.string.widget_compact_description, R.drawable.preview_compact, "4×1",
        ),
        WidgetEntry(
            DeuxSensWidgetProvider::class.java, WidgetFormat.DEUX_SENS,
            R.string.widget_deux_sens_name, R.string.widget_deux_sens_description, R.drawable.preview_deux_sens, "2×2",
        ),
        WidgetEntry(
            DeuxSensLargeWidgetProvider::class.java, WidgetFormat.DEUX_SENS_LARGE,
            R.string.widget_deux_sens_large_name, R.string.widget_deux_sens_large_description, R.drawable.preview_deux_sens_large, "4×2",
        ),
        WidgetEntry(
            TrainWidgetProvider::class.java, WidgetFormat.TRAIN,
            R.string.widget_train_name, R.string.widget_train_description, R.drawable.preview_train, "2×2",
        ),
        WidgetEntry(
            ListeWidgetProvider::class.java, WidgetFormat.LISTE,
            R.string.widget_liste_name, R.string.widget_liste_description, R.drawable.preview_liste, "4×2",
        ),
        WidgetEntry(
            ProximiteWidgetProvider::class.java, WidgetFormat.PROXIMITE,
            R.string.widget_proximite_name, R.string.widget_proximite_description, R.drawable.preview_proximite, "4×2",
        ),
        WidgetEntry(
            DeparturesWidgetProvider::class.java, null,
            R.string.widget_auto_name, R.string.widget_auto_description, R.drawable.preview_deux_sens_large, "4×2",
        ),
    )

    /** Format imposé par le fournisseur, ou null pour l'entrée automatique. */
    fun formatOf(providerClassName: String?): WidgetFormat? =
        catalog.firstOrNull { it.provider.name == providerClassName }?.format

    fun isProximity(context: Context, widgetId: Int): Boolean {
        val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId) ?: return false
        return formatOf(info.provider?.className) == WidgetFormat.PROXIMITE
    }

    /** Tous les fournisseurs de widgets de l'application. */
    fun components(context: Context): List<ComponentName> =
        catalog.map { ComponentName(context, it.provider) }
}
