package fr.trprim.siri.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import fr.trprim.siri.R
import fr.trprim.siri.data.WidgetConfig
import fr.trprim.siri.data.WidgetState
import fr.trprim.siri.ui.ConfigureActivity
import fr.trprim.siri.ui.MainActivity

/** Construit les RemoteViews d'un widget à partir de sa configuration et de son dernier état. */
object WidgetRenderer {

    private const val DEFAULT_WIDTH_DP = 348f
    private const val DEFAULT_HEIGHT_DP = 192f

    /**
     * @param fixedFormat format choisi dans la liste des widgets, ou null pour l'adapter à la taille
     * @param renderScale réduit la définition de l'image si elle est trop lourde pour le lanceur
     */
    fun build(
        context: Context,
        widgetId: Int,
        config: WidgetConfig?,
        state: WidgetState?,
        hasApiKey: Boolean,
        refreshing: Boolean,
        options: Bundle?,
        now: Long = System.currentTimeMillis(),
        fixedFormat: WidgetFormat? = null,
        renderScale: Float = 1f,
    ): RemoteViews {
        val (width, height) = widgetSize(context, options)
        val model = WidgetModels.build(config, state, hasApiKey, refreshing, width, height, now, fixedFormat)
        val density = context.resources.displayMetrics.density * renderScale

        val views = RemoteViews(context.packageName, R.layout.widget_canvas)
        views.setImageViewBitmap(R.id.widget_image, WidgetPainter.paint(context, model, width, height, density))
        views.setContentDescription(R.id.widget_image, describe(context, model))
        val click = when {
            config == null -> configurePendingIntent(context, widgetId)
            !hasApiKey -> mainPendingIntent(context)
            else -> refreshPendingIntent(context, widgetId)
        }
        views.setOnClickPendingIntent(R.id.widget_root, click)
        return views
    }

    /** Taille actuelle du widget en dp, selon l'orientation de l'écran. */
    private fun widgetSize(context: Context, options: Bundle?): Pair<Float, Float> {
        val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        val maxWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0) ?: 0
        val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
        val maxHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) ?: 0
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val width = if (landscape) maxWidth else minWidth
        val height = if (landscape) minHeight else maxHeight
        return Pair(
            (if (width > 0) width else maxOf(minWidth, maxWidth)).takeIf { it > 0 }?.toFloat() ?: DEFAULT_WIDTH_DP,
            (if (height > 0) height else maxOf(minHeight, maxHeight)).takeIf { it > 0 }?.toFloat() ?: DEFAULT_HEIGHT_DP,
        )
    }

    /** Le widget étant une image, sa description porte tout le contenu pour les lecteurs d'écran. */
    private fun describe(context: Context, model: WidgetModel): String {
        val notice = model.notice ?: return WidgetContent.describe(model)
        return when (notice.kind) {
            NoticeKind.NOT_CONFIGURED ->
                context.getString(R.string.widget_not_configured) + ". " + context.getString(R.string.widget_tap_to_configure)
            NoticeKind.MISSING_KEY ->
                context.getString(R.string.widget_missing_key_title) + ". " + context.getString(R.string.widget_missing_key_body)
            NoticeKind.LOADING -> context.getString(R.string.widget_loading)
            NoticeKind.NO_DEPARTURE -> model.stopName + ". " + context.getString(R.string.widget_no_departure)
            NoticeKind.ERROR -> model.stopName + ". " + notice.detail.orEmpty()
        }
    }

    fun refreshPendingIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, DeparturesWidgetProvider::class.java)
            .setAction(WidgetUpdater.ACTION_REFRESH)
            .setData(Uri.parse("trprim://widget/refresh/$widgetId"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        return PendingIntent.getBroadcast(
            context, widgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun configurePendingIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, ConfigureActivity::class.java)
            .setData(Uri.parse("trprim://widget/configure/$widgetId"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .putExtra(ConfigureActivity.EXTRA_FROM_WIDGET, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return PendingIntent.getActivity(
            context, widgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun mainPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun providerComponent(context: Context) = ComponentName(context, DeparturesWidgetProvider::class.java)
}
