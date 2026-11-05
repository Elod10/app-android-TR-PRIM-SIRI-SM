package fr.trprim.siri.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/** Stockage local : clé API, réglages et configuration/état de chaque widget. */
class Prefs(context: Context) {

    private val app = context.applicationContext
    private val settings: SharedPreferences = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val widgets: SharedPreferences = app.getSharedPreferences("widgets", Context.MODE_PRIVATE)
    private val formats: SharedPreferences = app.getSharedPreferences("prim_formats", Context.MODE_PRIVATE)

    /** Formats de requête PRIM acceptés, mémorisés entre deux actualisations. */
    val primFormats: PrimClient.FormatCache = object : PrimClient.FormatCache {
        override fun get(key: String): String? = formats.getString(key, null)
        override fun put(key: String, value: String) = formats.edit().putString(key, value).apply()
        override fun remove(key: String) = formats.edit().remove(key).apply()
    }

    var apiKey: String
        get() = settings.getString(KEY_API, "")?.trim().orEmpty()
        set(value) = settings.edit().putString(KEY_API, value.trim()).apply()

    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    /** Intervalle d'actualisation automatique en minutes (0 = manuel uniquement). */
    var refreshMinutes: Int
        get() = settings.getInt(KEY_REFRESH, DEFAULT_REFRESH_MINUTES)
        set(value) = settings.edit().putInt(KEY_REFRESH, value).apply()

    fun config(widgetId: Int): WidgetConfig? {
        val raw = widgets.getString(configKey(widgetId), null) ?: return null
        return try {
            WidgetConfig.fromJson(JSONObject(raw)).copy(widgetId = widgetId)
        } catch (e: Exception) {
            null
        }
    }

    fun saveConfig(config: WidgetConfig) {
        val editor = widgets.edit()
            .putString(configKey(config.widgetId), config.toJson().toString())
            .remove(stateKey(config.widgetId))
        if (!config.auto) editor.remove(autoKey(config.widgetId))
        editor.apply()
    }

    /** Position et fenêtre de choix du mode automatique, pour ce widget. */
    fun auto(widgetId: Int): AutoState {
        val raw = widgets.getString(autoKey(widgetId), null) ?: return AutoState()
        return try {
            AutoState.fromJson(JSONObject(raw))
        } catch (e: Exception) {
            AutoState()
        }
    }

    fun saveAuto(widgetId: Int, state: AutoState) {
        widgets.edit().putString(autoKey(widgetId), state.toJson().toString()).apply()
    }

    /** Dernier état du format À proximité, pour ce widget. */
    fun proximity(widgetId: Int): ProximityState? {
        val raw = widgets.getString(proximityKey(widgetId), null) ?: return null
        return try {
            ProximityState.fromJson(JSONObject(raw))
        } catch (e: Exception) {
            null
        }
    }

    fun saveProximity(widgetId: Int, state: ProximityState) {
        widgets.edit().putString(proximityKey(widgetId), state.toJson().toString()).apply()
    }

    /** Lignes favorites du mode automatique. */
    val favoriteLines: Set<String>
        get() = settings.getStringSet(KEY_FAVORITE_LINES, null)?.toSet().orEmpty()

    /** Ajoute ou retire une ligne des favoris. Renvoie vrai si elle est maintenant favorite. */
    fun toggleFavoriteLine(lineId: String): Boolean {
        val current = favoriteLines
        val favorite = lineId !in current
        val next = if (favorite) current + lineId else current - lineId
        settings.edit().putStringSet(KEY_FAVORITE_LINES, next).apply()
        return favorite
    }

    /** Ligne choisie à chaque endroit par l'usager. Ces positions restent sur le téléphone. */
    var placeChoices: PlaceChoices
        get() = PlaceChoices.fromJson(settings.getString(KEY_PLACE_CHOICES, null))
        set(value) = settings.edit().putString(KEY_PLACE_CHOICES, value.toJson()).apply()

    fun state(widgetId: Int): WidgetState? {
        val raw = widgets.getString(stateKey(widgetId), null) ?: return null
        return try {
            WidgetState.fromJson(JSONObject(raw))
        } catch (e: Exception) {
            null
        }
    }

    fun saveState(widgetId: Int, state: WidgetState) {
        widgets.edit().putString(stateKey(widgetId), state.toJson().toString()).apply()
    }

    fun registerWidgetsListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        widgets.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterWidgetsListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        widgets.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun deleteWidget(widgetId: Int) {
        widgets.edit()
            .remove(configKey(widgetId))
            .remove(stateKey(widgetId))
            .remove(autoKey(widgetId))
            .remove(proximityKey(widgetId))
            .apply()
    }

    /** Supprime les données des widgets qui n'existent plus. */
    fun retainWidgets(existingIds: Set<Int>) {
        val editor = widgets.edit()
        widgets.all.keys.forEach { key ->
            val id = key.substringAfter('_').toIntOrNull() ?: return@forEach
            if (id !in existingIds) editor.remove(key)
        }
        editor.apply()
    }

    companion object {
        private const val KEY_API = "prim_api_key"
        private const val KEY_REFRESH = "refresh_minutes"
        private const val KEY_FAVORITE_LINES = "favorite_lines"
        private const val KEY_PLACE_CHOICES = "place_choices"
        const val DEFAULT_REFRESH_MINUTES = 2
        val REFRESH_CHOICES = listOf(0, 1, 2, 5, 10, 15, 30)

        private fun configKey(id: Int) = "config_$id"
        private fun stateKey(id: Int) = "state_$id"
        private fun autoKey(id: Int) = "auto_$id"
        private fun proximityKey(id: Int) = "proximity_$id"
    }
}
