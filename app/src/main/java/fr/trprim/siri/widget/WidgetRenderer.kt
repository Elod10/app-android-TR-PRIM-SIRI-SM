package fr.trprim.siri.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import fr.trprim.siri.R
import fr.trprim.siri.data.AutoHint
import fr.trprim.siri.data.AutoMessage
import fr.trprim.siri.data.AutoState
import fr.trprim.siri.data.PositionReader
import fr.trprim.siri.data.WidgetConfig
import fr.trprim.siri.data.WidgetState
import fr.trprim.siri.ui.ConfigureActivity
import fr.trprim.siri.ui.NearbyChoiceActivity

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
        auto: AutoState? = null,
    ): RemoteViews {
        val (width, height) = widgetSize(context, options)
        val isAuto = config?.auto == true
        // Sans position exacte, le toucher mène à la configuration, qui la redemande.
        val canLocate = isAuto && PositionReader(context).hasPreciseAccess
        val hint = when {
            !isAuto -> null
            !canLocate -> AutoHint(AutoMessage.NEEDS_ACCESS.label, AutoMessage.NEEDS_ACCESS.short)
            else -> auto?.hint(now)
        }
        val model = WidgetModels.build(
            config, state, hasApiKey, refreshing, width, height, now, fixedFormat,
            autoHint = hint,
            autoDistanceM = auto?.distanceM.takeIf { isAuto },
        )
        val density = context.resources.displayMetrics.density * renderScale

        val views = RemoteViews(context.packageName, R.layout.widget_canvas)
        views.setImageViewBitmap(R.id.widget_image, WidgetPainter.paint(context, model, width, height, density))
        views.setContentDescription(R.id.widget_image, describe(context, model))
        val needsConfiguration = model.notice?.kind == NoticeKind.NOT_CONFIGURED || model.notice?.kind == NoticeKind.RECONFIGURE
        val click = when {
            needsConfiguration || (isAuto && !canLocate) -> configurePendingIntent(context, widgetId)
            isAuto && auto?.isChoiceOpen(now) == true -> choicePendingIntent(context, widgetId)
            isAuto -> locatePendingIntent(context, widgetId)
            else -> refreshPendingIntent(context, widgetId)
        }
        views.setOnClickPendingIntent(R.id.widget_root, click)
        return views
    }

    /** Le service doit partir directement du toucher : c'est ce qui lui donne accès à la position. */
    private fun locatePendingIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, AutoLocateService::class.java)
            .setData(Uri.parse("trprim://widget/locate/$widgetId"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        return PendingIntent.getForegroundService(
            context, widgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun choicePendingIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, NearbyChoiceActivity::class.java)
            .setData(Uri.parse("trprim://widget/choice/$widgetId"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return PendingIntent.getActivity(
            context, widgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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
            NoticeKind.RECONFIGURE ->
                context.getString(R.string.widget_reconfigure_title) + ". " + context.getString(R.string.widget_reconfigure_body)
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
}
