package fr.trprim.siri.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import fr.trprim.siri.R
import fr.trprim.siri.data.Directions
import fr.trprim.siri.data.WidgetConfig
import fr.trprim.siri.data.WidgetState
import fr.trprim.siri.ui.ConfigureActivity
import fr.trprim.siri.ui.MainActivity

/** Construit les RemoteViews d'un widget à partir de sa configuration et de son dernier état. */
object WidgetRenderer {

    private const val HEADER_HEIGHT_DP = 64
    private const val ROW_HEIGHT_DP = 38

    fun build(
        context: Context,
        widgetId: Int,
        config: WidgetConfig?,
        state: WidgetState?,
        hasApiKey: Boolean,
        refreshing: Boolean,
        options: Bundle?,
        now: Long = System.currentTimeMillis(),
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_departures)
        views.removeAllViews(R.id.widget_rows)

        val configureIntent = configurePendingIntent(context, widgetId)
        views.setOnClickPendingIntent(R.id.widget_settings, configureIntent)

        if (config == null) {
            views.setViewVisibility(R.id.widget_badge, View.GONE)
            views.setTextViewText(R.id.widget_stop, context.getString(R.string.widget_not_configured))
            views.setTextViewText(R.id.widget_direction, context.getString(R.string.widget_tap_to_configure))
            views.setViewVisibility(R.id.widget_refresh, View.GONE)
            views.setViewVisibility(R.id.widget_progress, View.GONE)
            showMessage(views, context.getString(R.string.widget_tap_to_configure))
            views.setTextViewText(R.id.widget_updated, "")
            views.setOnClickPendingIntent(R.id.widget_root, configureIntent)
            return views
        }

        // En-tête : pastille de ligne, arrêt, sens.
        views.setViewVisibility(R.id.widget_badge, View.VISIBLE)
        views.setImageViewBitmap(R.id.widget_badge, BadgeRenderer.render(context, config.line))
        views.setContentDescription(R.id.widget_badge, config.line.name)
        views.setTextViewText(R.id.widget_stop, config.stop.name)
        views.setTextViewText(R.id.widget_direction, config.directionLabel.ifBlank { Directions.ALL_DIRECTIONS })
        views.setViewVisibility(R.id.widget_refresh, if (refreshing) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_progress, if (refreshing) View.VISIBLE else View.GONE)

        val refreshIntent = refreshPendingIntent(context, widgetId)
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent)

        if (!hasApiKey) {
            showMessage(views, context.getString(R.string.widget_missing_key))
            views.setTextViewText(R.id.widget_updated, "")
            views.setOnClickPendingIntent(R.id.widget_root, mainPendingIntent(context))
            return views
        }
        views.setOnClickPendingIntent(R.id.widget_root, refreshIntent)

        val upcoming = state?.let { Directions.upcoming(it.departures, now) }.orEmpty()
        val rows = maxRows(options)
        if (upcoming.isEmpty()) {
            val message = when {
                state == null -> context.getString(R.string.widget_loading)
                state.error != null -> state.error
                else -> context.getString(R.string.widget_no_departure)
            }
            showMessage(views, message)
        } else {
            views.setViewVisibility(R.id.widget_message, View.GONE)
            views.setViewVisibility(R.id.widget_rows, View.VISIBLE)
            val normalColor = context.getColor(R.color.widget_accent)
            val lateColor = context.getColor(R.color.widget_warning)
            upcoming.take(rows).forEach { d ->
                val row = RemoteViews(context.packageName, R.layout.widget_row)
                row.setTextViewText(R.id.row_destination, d.destination)
                row.setTextViewText(R.id.row_details, DepartureFormat.details(d, config.line.mode))
                row.setTextViewText(R.id.row_wait, DepartureFormat.waitText(d, now, config.line.mode))
                row.setTextColor(R.id.row_wait, if (DepartureFormat.isLate(d)) lateColor else normalColor)
                views.addView(R.id.widget_rows, row)
            }
        }

        views.setTextViewText(R.id.widget_updated, footer(context, state, refreshing, upcoming.isNotEmpty()))
        return views
    }

    private fun footer(context: Context, state: WidgetState?, refreshing: Boolean, hasRows: Boolean): String {
        if (refreshing) return context.getString(R.string.widget_refreshing)
        if (state == null || state.fetchedAt <= 0L) return ""
        val updated = context.getString(R.string.widget_updated_at, DepartureFormat.clock(state.fetchedAt))
        return if (state.error != null && hasRows) "⚠ " + context.getString(R.string.widget_stale) + " · " + updated else updated
    }

    private fun showMessage(views: RemoteViews, message: String) {
        views.setViewVisibility(R.id.widget_rows, View.GONE)
        views.setViewVisibility(R.id.widget_message, View.VISIBLE)
        views.setTextViewText(R.id.widget_message, message)
    }

    /** Nombre de lignes de passages affichables selon la taille du widget. */
    fun maxRows(options: Bundle?): Int {
        val height = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) ?: 0
        if (height <= 0) return 3
        return ((height - HEADER_HEIGHT_DP) / ROW_HEIGHT_DP).coerceIn(1, 8)
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
