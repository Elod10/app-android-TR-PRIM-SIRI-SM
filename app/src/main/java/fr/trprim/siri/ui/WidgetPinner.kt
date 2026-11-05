package fr.trprim.siri.ui

import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle

/** Ajout d'un widget à l'écran d'accueil depuis l'application. */
object WidgetPinner {

    private const val REQUEST_CODE = 1

    /**
     * Demande au lanceur d'ajouter le widget. Une fois l'ajout accepté, le système ouvre la
     * configuration en lui passant l'identifiant du nouveau widget.
     *
     * @return faux si le lanceur ne sait pas ajouter de widget à la demande
     */
    fun request(context: Context, provider: Class<*>): Boolean {
        val manager = context.getSystemService(AppWidgetManager::class.java) ?: return false
        if (!manager.isRequestPinAppWidgetSupported) return false
        val callback = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            configureIntent(context).putExtra(ConfigureActivity.EXTRA_FROM_PIN, true),
            // Modifiable : c'est le système qui ajoute l'identifiant du widget à cet Intent.
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag(),
            creatorOptions(),
        )
        return manager.requestPinAppWidget(ComponentName(context, provider), null, callback)
    }

    fun configureIntent(context: Context): Intent =
        Intent(context, ConfigureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun mutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

    /** Android 15 n'autorise plus d'office le lancement d'un écran par un PendingIntent. */
    private fun creatorOptions(): Bundle? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ActivityOptions.makeBasic()
                .setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
        } else {
            null
        }
}
