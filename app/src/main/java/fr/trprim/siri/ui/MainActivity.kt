package fr.trprim.siri.ui

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import fr.trprim.siri.R
import fr.trprim.siri.data.DataSources
import fr.trprim.siri.data.Directions
import fr.trprim.siri.data.PassageRoute
import fr.trprim.siri.data.Prefs
import fr.trprim.siri.data.PrimClient
import fr.trprim.siri.data.PrimException
import fr.trprim.siri.data.WidgetState
import fr.trprim.siri.widget.BadgeRenderer
import fr.trprim.siri.widget.DepartureFormat
import fr.trprim.siri.widget.WidgetProviders
import fr.trprim.siri.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Écran principal : clé API PRIM facultative, fréquence d'actualisation et liste des widgets. */
class MainActivity : Activity() {

    private val scope = MainScope()
    private lateinit var prefs: Prefs

    private lateinit var keyInput: EditText
    private lateinit var keyStatus: TextView
    private lateinit var keyTest: Button
    private lateinit var sourceStatus: TextView
    private lateinit var about: TextView
    private lateinit var widgetsContainer: LinearLayout
    private lateinit var widgetsEmpty: TextView
    private var appVersion: String? = null

    /** Clé affichée dans le champ, pour le resynchroniser si elle change ailleurs (configuration d'un widget). */
    private var shownKey = ""

    private val widgetsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refreshWidgetList() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        keyInput = findViewById(R.id.key_input)
        keyStatus = findViewById(R.id.key_status)
        keyTest = findViewById(R.id.key_test)
        widgetsContainer = findViewById(R.id.widgets_container)
        widgetsEmpty = findViewById(R.id.widgets_empty)
        sourceStatus = findViewById(R.id.source_status)
        about = findViewById(R.id.about)
        about.movementMethod = LinkMovementMethod.getInstance()
        findViewById<TextView>(R.id.key_help).movementMethod = LinkMovementMethod.getInstance()

        shownKey = prefs.apiKey
        keyInput.setText(shownKey)
        findViewById<CheckBox>(R.id.key_show).setOnCheckedChangeListener { _, show ->
            val variation = if (show) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD
            keyInput.inputType = InputType.TYPE_CLASS_TEXT or variation or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            keyInput.typeface = Typeface.MONOSPACE
            keyInput.setSelection(keyInput.text.length)
        }
        findViewById<Button>(R.id.key_save).setOnClickListener { saveKey() }
        keyTest.setOnClickListener { testKey() }
        findViewById<Button>(R.id.key_portal).setOnClickListener { UiText.openUrl(this, UiText.PRIM_PORTAL_URL) }

        findViewById<Button>(R.id.add_widget).setOnClickListener { requestPinWidget() }

        appVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
    }

    override fun onResume() {
        super.onResume()
        prefs.registerWidgetsListener(widgetsListener)
        val storedKey = prefs.apiKey
        if (storedKey != shownKey) {
            shownKey = storedKey
            keyInput.setText(storedKey)
        }
        renderSource()
        refreshWidgetList()
    }

    override fun onPause() {
        prefs.unregisterWidgetsListener(widgetsListener)
        super.onPause()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // --- Clé API -----------------------------------------------------------------------------

    /** Enregistre la clé saisie. Un champ vide retire la clé : les widgets reviennent sur Tracéo. */
    private fun saveKey() {
        val key = keyInput.text.toString().trim()
        val changed = key != prefs.apiKey
        prefs.apiKey = key
        shownKey = key
        showKeyStatus(getString(if (key.isEmpty()) R.string.main_key_removed else R.string.main_key_saved), error = false)
        if (changed) {
            renderSource()
            WidgetUpdater.renderAll(this)
            WidgetUpdater.requestRefresh(this)
        }
    }

    private fun testKey() {
        // « Tester » avec un champ vide ne doit pas retirer la clé enregistrée.
        if (keyInput.text.isNullOrBlank()) {
            showKeyStatus(getString(R.string.main_key_empty), error = true)
            return
        }
        saveKey()
        val key = prefs.apiKey
        keyTest.isEnabled = false
        showKeyStatus(getString(R.string.main_key_testing), error = false)
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    PrimClient(key).testKey()
                    null
                } catch (e: PrimException) {
                    e
                }
            }
            keyTest.isEnabled = true
            if (error == null) {
                showKeyStatus(getString(R.string.main_key_ok), error = false)
            } else {
                showKeyStatus(error.message.orEmpty(), error = true)
            }
        }
    }

    private fun showKeyStatus(message: String, error: Boolean) {
        keyStatus.visibility = View.VISIBLE
        keyStatus.text = message
        keyStatus.setTextColor(getColor(if (error) R.color.error_text else R.color.success_text))
    }

    // --- Source des données ---------------------------------------------------------------------

    /** La source active, les mentions légales et les fréquences proposées dépendent de la clé. */
    private fun renderSource() {
        val hasKey = prefs.hasApiKey
        sourceStatus.setText(if (hasKey) R.string.main_source_prim else R.string.main_source_traceo)
        about.text = TextUtils.concat(
            getText(if (hasKey) R.string.main_about_prim else R.string.main_about_traceo),
            appVersion?.let { "\n\nVersion $it" }.orEmpty(),
        )
        setupRefreshSpinner(hasKey)
    }

    // --- Fréquence d'actualisation --------------------------------------------------------------

    private fun setupRefreshSpinner(hasKey: Boolean) {
        val spinner = findViewById<Spinner>(R.id.refresh_spinner)
        val choices = DataSources.refreshChoices(Prefs.REFRESH_CHOICES, hasKey)
        val labels = choices.map { minutes ->
            when (minutes) {
                0 -> getString(R.string.refresh_manual)
                1 -> getString(R.string.refresh_every_minute)
                else -> getString(R.string.refresh_every_n, minutes)
            }
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val current = choices.indexOf(DataSources.refreshMinutes(prefs.refreshMinutes, hasKey)).takeIf { it >= 0 }
            ?: choices.indexOf(Prefs.DEFAULT_REFRESH_MINUTES)
        spinner.setSelection(current)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val minutes = choices[position]
                // Comparer à l'intervalle appliqué conserve « Chaque minute » pour le retour d'une clé.
                if (minutes != DataSources.refreshMinutes(prefs.refreshMinutes, hasKey)) {
                    prefs.refreshMinutes = minutes
                    WidgetUpdater.scheduleTick(this@MainActivity)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    // --- Widgets ------------------------------------------------------------------------------

    private fun refreshWidgetList() {
        val ids = WidgetUpdater.widgetIds(this)
        widgetsContainer.removeAllViews()
        widgetsEmpty.visibility = if (ids.isEmpty()) View.VISIBLE else View.GONE
        val now = System.currentTimeMillis()

        ids.forEachIndexed { index, id ->
            val view = layoutInflater.inflate(R.layout.item_widget, widgetsContainer, false)
            val config = prefs.config(id)
            val state = prefs.state(id)

            val badge = view.findViewById<ImageView>(R.id.item_widget_badge)
            val title = view.findViewById<TextView>(R.id.item_widget_title)
            val subtitle = view.findViewById<TextView>(R.id.item_widget_subtitle)
            val departures = view.findViewById<TextView>(R.id.item_widget_departures)
            val refresh = view.findViewById<Button>(R.id.item_widget_refresh)

            if (config == null) {
                badge.visibility = View.GONE
                title.text = getString(R.string.main_widget_not_configured, index + 1)
                subtitle.text = getString(R.string.widget_tap_to_configure)
                departures.visibility = View.GONE
                refresh.visibility = View.GONE
            } else {
                badge.setImageBitmap(BadgeRenderer.render(this, config.line))
                title.text = config.stop.name
                subtitle.text = UiText.lineTitle(config.line) + " · " +
                    config.directionLabel.ifBlank { Directions.ALL_DIRECTIONS }
                val upcoming = state?.let { Directions.upcoming(it.departures, now) }.orEmpty()
                val reconfigure = DataSources.route(config.stop.stopIds, prefs.hasApiKey) == PassageRoute.RECONFIGURE
                if (reconfigure) refresh.visibility = View.GONE
                departures.text = when {
                    reconfigure -> getString(R.string.widget_reconfigure_title)
                    upcoming.isNotEmpty() -> upcoming.take(4).joinToString("\n") { d ->
                        DepartureFormat.waitText(d, now, config.line.mode) + "  ·  " + d.destination
                    } + state?.let(::dataTimeLine).orEmpty()
                    state?.error != null -> state.error
                    state != null -> getString(R.string.widget_no_departure)
                    else -> getString(R.string.main_widget_no_data)
                }
                refresh.setOnClickListener { WidgetUpdater.requestRefresh(this, id) }
            }
            view.findViewById<Button>(R.id.item_widget_configure).setOnClickListener {
                startActivity(
                    Intent(this, ConfigureActivity::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                )
            }
            view.findViewById<View>(R.id.item_widget_divider).visibility =
                if (index == ids.lastIndex) View.GONE else View.VISIBLE
            widgetsContainer.addView(view)
        }
    }

    /** Fin du résumé d'un widget : heure du relevé ou, en direct, de la dernière mise à jour. */
    private fun dataTimeLine(state: WidgetState): String {
        val at = if (state.isSnapshot) state.dataAt else state.fetchedAt
        val time = at?.takeIf { it > 0 }?.let { DepartureFormat.clock(it, withSeconds = true) } ?: return ""
        val label = when {
            !state.isSnapshot -> R.string.widget_updated_at
            state.fresh == false -> R.string.widget_snapshot_old
            else -> R.string.widget_snapshot_at
        }
        return "\n" + getString(label, time)
    }

    /** Propose les widgets disponibles, puis demande au lanceur d'ajouter celui qui est choisi. */
    private fun requestPinWidget() {
        val manager = getSystemService(AppWidgetManager::class.java)
        if (manager == null || !manager.isRequestPinAppWidgetSupported) {
            showPinUnsupported()
            return
        }
        val choices = WidgetChoiceAdapter(WidgetProviders.catalog)
        AlertDialog.Builder(this)
            .setTitle(R.string.main_add_widget_title)
            .setAdapter(choices) { _, position ->
                val provider = ComponentName(this, choices.getItem(position).provider)
                if (!manager.requestPinAppWidget(provider, null, null)) showPinUnsupported()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showPinUnsupported() {
        Toast.makeText(this, R.string.main_add_widget_unsupported, Toast.LENGTH_LONG).show()
    }
}
