package fr.trprim.siri.widget

import android.content.ComponentName
import android.content.Context

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

object WidgetProviders {

    private val fixedFormats: Map<String, WidgetFormat> = mapOf(
        PastilleWidgetProvider::class.java.name to WidgetFormat.PASTILLE,
        UnSensWidgetProvider::class.java.name to WidgetFormat.UN_SENS,
        GrandChiffreWidgetProvider::class.java.name to WidgetFormat.GRAND_CHIFFRE,
        FriseWidgetProvider::class.java.name to WidgetFormat.FRISE,
        CompactWidgetProvider::class.java.name to WidgetFormat.COMPACT,
        DeuxSensWidgetProvider::class.java.name to WidgetFormat.DEUX_SENS,
        DeuxSensLargeWidgetProvider::class.java.name to WidgetFormat.DEUX_SENS_LARGE,
        TrainWidgetProvider::class.java.name to WidgetFormat.TRAIN,
        ListeWidgetProvider::class.java.name to WidgetFormat.LISTE,
    )

    /** Format imposé par le fournisseur, ou null pour l'entrée automatique. */
    fun formatOf(providerClassName: String?): WidgetFormat? = fixedFormats[providerClassName]

    /** Tous les fournisseurs de widgets de l'application. */
    fun components(context: Context): List<ComponentName> =
        (fixedFormats.keys + DeparturesWidgetProvider::class.java.name).map { ComponentName(context, it) }
}
