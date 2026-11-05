package fr.trprim.siri.ui

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import fr.trprim.siri.R
import fr.trprim.siri.data.AnchorPolicy
import fr.trprim.siri.data.DataSources
import fr.trprim.siri.data.DirectionOption
import fr.trprim.siri.data.Directions
import fr.trprim.siri.data.Line
import fr.trprim.siri.data.PassageRoute
import fr.trprim.siri.data.PositionFix
import fr.trprim.siri.data.PositionReader
import fr.trprim.siri.data.Prefs
import fr.trprim.siri.data.PrimClient
import fr.trprim.siri.data.PrimException
import fr.trprim.siri.data.RankedLine
import fr.trprim.siri.data.ReferentialRepository
import fr.trprim.siri.data.Stop
import fr.trprim.siri.data.TransportMode
import fr.trprim.siri.data.WidgetConfig
import fr.trprim.siri.data.WidgetTheme
import fr.trprim.siri.data.formatDistance
import fr.trprim.siri.data.normalizeLabel
import fr.trprim.siri.widget.AutoMode
import fr.trprim.siri.widget.WidgetFormat
import fr.trprim.siri.widget.WidgetProviders
import fr.trprim.siri.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Assistant de configuration d'un widget :
 * mode de transport → ligne → arrêt → sens, ou le mode automatique, qui suit la position.
 */
class ConfigureActivity : Activity() {

    companion object {
        const val EXTRA_FROM_WIDGET = "fr.trprim.siri.extra.FROM_WIDGET"

        private val THEME_CHOICES = listOf(
            WidgetTheme.AUTO to R.string.theme_auto,
            WidgetTheme.DARK to R.string.theme_dark,
            WidgetTheme.LIGHT to R.string.theme_light,
            WidgetTheme.LINE to R.string.theme_line,
        )

        private const val REQUEST_LOCATION = 1
        private const val AUTO_ROWS_SHOWN = 30
    }

    private enum class Step { KEY, MODE, LINE, STOP, DIRECTION, AUTO }

    /** Marqueur de l'option « Tous les sens ». */
    private object AllDirections

    /** Marqueur de la ligne « Automatique » de la liste des modes. */
    private object AutoRow

    private val scope = MainScope()
    private var loadJob: Job? = null

    private lateinit var prefs: Prefs
    private lateinit var repository: ReferentialRepository
    private lateinit var adapter: RowAdapter

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var existing: WidgetConfig? = null
    private var step = Step.MODE
    private var mode: TransportMode? = null
    private var line: Line? = null
    private var stop: Stop? = null
    private var viaShortcut = false
    private var directionOptions: List<DirectionOption> = emptyList()
    private var autoFix: PositionFix? = null
    private var autoFirst: RankedLine? = null

    /** Format choisi dans la liste des widgets du lanceur (null pour l'entrée automatique). */
    private val chosenFormat: WidgetFormat? by lazy {
        WidgetProviders.formatOf(AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider?.className)
    }

    /** Vrai si le format choisi n'affiche qu'un sens : un seul peut alors être coché. */
    private val oneDirectionOnly: Boolean get() = chosenFormat?.followsOneDirection == true

    private lateinit var breadcrumb: TextView
    private lateinit var stepHelp: TextView
    private lateinit var search: EditText
    private lateinit var list: ListView
    private lateinit var statusBox: View
    private lateinit var progress: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var retry: Button
    private lateinit var keyPanel: View
    private lateinit var keyInput: EditText
    private lateinit var keyStatus: TextView
    private lateinit var keyContinue: Button
    private lateinit var bottomBar: View
    private lateinit var themeSpinner: Spinner
    private lateinit var save: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Résultat par défaut : annulation (le lanceur retire alors le widget).
        setResult(RESULT_CANCELED, resultIntent())
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Toast.makeText(this, R.string.invalid_widget, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContentView(R.layout.activity_configure)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = Prefs(this)
        repository = ReferentialRepository(this)
        existing = prefs.config(widgetId)

        breadcrumb = findViewById(R.id.breadcrumb)
        stepHelp = findViewById(R.id.step_help)
        search = findViewById(R.id.search)
        list = findViewById(R.id.list)
        statusBox = findViewById(R.id.status_box)
        progress = findViewById(R.id.progress)
        statusText = findViewById(R.id.status_text)
        retry = findViewById(R.id.retry)
        keyPanel = findViewById(R.id.key_panel)
        keyInput = findViewById(R.id.key_input)
        keyStatus = findViewById(R.id.key_status)
        keyContinue = findViewById(R.id.key_continue)
        bottomBar = findViewById(R.id.bottom_bar)
        themeSpinner = findViewById(R.id.theme_spinner)
        save = findViewById(R.id.save)
        setupThemeSpinner()

        adapter = RowAdapter(this)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> onItemClicked(adapter.getItem(position)) }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s?.toString().orEmpty())
            }
        })
        retry.setOnClickListener { goTo(step, forceRefresh = true) }
        save.setOnClickListener { saveConfig() }
        findViewById<Button>(R.id.key_portal).setOnClickListener { UiText.openUrl(this, UiText.PRIM_PORTAL_URL) }
        keyContinue.setOnClickListener { submitKey() }
        findViewById<TextView>(R.id.key_help).movementMethod = LinkMovementMethod.getInstance()

        goTo(Step.MODE)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun resultIntent(): Intent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

    private fun setupThemeSpinner() {
        val labels = THEME_CHOICES.map { getString(it.second) }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        themeSpinner.adapter = adapter
        val current = existing?.theme ?: WidgetFormat.defaultTheme(chosenFormat)
        themeSpinner.setSelection(THEME_CHOICES.indexOfFirst { it.first == current }.coerceAtLeast(0))
    }

    // --- Navigation ------------------------------------------------------------------------

    private fun goTo(target: Step, forceRefresh: Boolean = false) {
        loadJob?.cancel()
        step = target
        search.setText("")
        adapter.setItems(emptyList())
        updateChrome()
        when (target) {
            Step.KEY -> showKeyPanel()
            Step.MODE -> showModes()
            Step.LINE -> loadLines(forceRefresh)
            Step.STOP -> loadStops(forceRefresh)
            Step.DIRECTION -> loadDirections()
            Step.AUTO -> startAuto()
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when (step) {
            Step.DIRECTION -> goTo(if (viaShortcut) Step.MODE else Step.STOP)
            Step.STOP -> goTo(Step.LINE)
            Step.LINE, Step.KEY, Step.AUTO -> goTo(Step.MODE)
            Step.MODE -> super.onBackPressed()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            @Suppress("DEPRECATION")
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateChrome() {
        val (index, label) = when (step) {
            Step.KEY -> 0 to getString(R.string.step_key)
            Step.MODE -> 1 to getString(R.string.step_mode)
            Step.LINE -> 2 to getString(R.string.step_line)
            Step.STOP -> 3 to getString(R.string.step_stop)
            Step.DIRECTION -> 4 to getString(R.string.step_direction)
            Step.AUTO -> 0 to getString(R.string.step_auto)
        }
        title = if (index == 0) label else getString(R.string.step_counter, index, label)

        val crumbs = ArrayList<String>()
        if (step.ordinal > Step.MODE.ordinal) mode?.let { crumbs += it.label }
        if (step.ordinal > Step.LINE.ordinal) line?.let { crumbs += UiText.lineTitle(it) }
        if (step.ordinal > Step.STOP.ordinal) stop?.let { crumbs += it.name }
        breadcrumb.text = crumbs.joinToString("  ›  ")
        breadcrumb.visibility = if (crumbs.isEmpty()) View.GONE else View.VISIBLE

        search.visibility = if (step == Step.LINE || step == Step.STOP) View.VISIBLE else View.GONE
        search.hint = getString(
            when {
                step == Step.LINE -> R.string.search_line_hint
                prefs.hasApiKey -> R.string.search_stop_hint
                // Les arrêts de Tracéo n'ont pas de commune.
                else -> R.string.search_stop_hint_no_town
            },
        )
        stepHelp.visibility = View.GONE
        bottomBar.visibility = if (step == Step.DIRECTION) View.VISIBLE else View.GONE
        keyPanel.visibility = if (step == Step.KEY) View.VISIBLE else View.GONE
        if (step != Step.KEY) hideKeyboard()
    }

    private fun showList() {
        statusBox.visibility = View.GONE
        list.visibility = View.VISIBLE
        list.setSelection(0)
    }

    private fun showLoading(message: String) {
        list.visibility = View.GONE
        statusBox.visibility = View.VISIBLE
        progress.visibility = View.VISIBLE
        statusText.text = message
        retry.visibility = View.GONE
    }

    private fun showMessage(message: String, canRetry: Boolean) {
        list.visibility = View.GONE
        statusBox.visibility = View.VISIBLE
        progress.visibility = View.GONE
        statusText.text = message
        retry.visibility = if (canRetry) View.VISIBLE else View.GONE
    }

    private fun hideKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java) ?: return
        imm.hideSoftInputFromWindow(search.windowToken, 0)
    }

    // --- Clé API facultative, proposée sous la liste des modes ---------------------------------

    private fun showKeyPanel() {
        list.visibility = View.GONE
        statusBox.visibility = View.GONE
        keyInput.setText(prefs.apiKey)
        keyStatus.visibility = View.GONE
        keyContinue.isEnabled = true
    }

    private fun submitKey() {
        val key = keyInput.text.toString().trim()
        if (key.isEmpty()) {
            goTo(Step.MODE)
            return
        }
        prefs.apiKey = key
        keyContinue.isEnabled = false
        showKeyStatus(getString(R.string.main_key_testing), error = false)
        loadJob = scope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    PrimClient(key).testKey()
                    null
                } catch (e: PrimException) {
                    e
                }
            }
            keyContinue.isEnabled = true
            if (error != null && error.kind == PrimException.Kind.AUTH) {
                showKeyStatus(error.message.orEmpty(), error = true)
            } else {
                if (error != null) Toast.makeText(this@ConfigureActivity, error.message, Toast.LENGTH_LONG).show()
                WidgetUpdater.renderAll(this@ConfigureActivity)
                WidgetUpdater.requestRefresh(this@ConfigureActivity)
                goTo(Step.MODE)
            }
        }
    }

    private fun showKeyStatus(message: String, error: Boolean) {
        keyStatus.visibility = View.VISIBLE
        keyStatus.text = message
        keyStatus.setTextColor(getColor(if (error) R.color.error_text else R.color.success_text))
    }

    // --- Étape 1 : mode ---------------------------------------------------------------------

    private fun showModes() {
        val hasKey = prefs.hasApiKey
        val items = ArrayList<RowItem>()
        items += RowItem(
            title = getString(R.string.auto_row),
            subtitle = getString(if (existing?.auto == true) R.string.auto_row_active else R.string.auto_row_sub),
            payload = AutoRow,
        )
        // Un arrêt choisi avec une clé retirée depuis n'existe pas chez Tracéo : impossible de le garder.
        existing?.takeIf { !it.auto && DataSources.route(it.stop.stopIds, hasKey) != PassageRoute.RECONFIGURE }?.let { cfg ->
            items += RowItem(
                title = getString(R.string.keep_current),
                subtitle = getString(R.string.keep_current_sub, UiText.lineTitle(cfg.line), cfg.stop.name),
                badge = cfg.line,
                payload = cfg,
            )
        }
        DataSources.modes(hasKey).forEach { m ->
            items += RowItem(m.label, UiText.modeSubtitle(m), badge = UiText.modeBadge(m), payload = m)
        }
        if (!hasKey) {
            items += RowItem(getString(R.string.key_optional_row), getString(R.string.key_optional_row_sub), payload = Step.KEY)
        }
        adapter.setItems(items)
        showList()
    }

    private fun onItemClicked(item: RowItem) {
        when (step) {
            Step.MODE -> when (val payload = item.payload) {
                is WidgetConfig -> {
                    mode = payload.mode
                    line = payload.line
                    stop = payload.stop
                    viaShortcut = true
                    goTo(Step.DIRECTION)
                }
                is TransportMode -> {
                    mode = payload
                    line = null
                    stop = null
                    viaShortcut = false
                    goTo(Step.LINE)
                }
                is Step -> goTo(payload)
                AutoRow -> {
                    mode = null
                    line = null
                    stop = null
                    goTo(Step.AUTO)
                }
            }
            Step.LINE -> (item.payload as? Line)?.let {
                line = it
                stop = null
                goTo(Step.STOP)
            }
            Step.STOP -> (item.payload as? Stop)?.let {
                stop = it
                goTo(Step.DIRECTION)
            }
            Step.DIRECTION -> toggleDirection(item)
            Step.AUTO -> {
                adapter.items.forEach { it.checked = it === item }
                adapter.notifyDataSetChanged()
                updateSaveEnabled()
            }
            Step.KEY -> Unit
        }
    }

    // --- Mode automatique ------------------------------------------------------------------------

    private fun startAuto() {
        if (PositionReader(this).hasPreciseAccess) {
            loadAuto()
            return
        }
        stepHelp.text = getString(R.string.auto_help)
        stepHelp.visibility = View.VISIBLE
        showLoading(getString(R.string.auto_loading_position))
        requestPermissions(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            REQUEST_LOCATION,
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_LOCATION || step != Step.AUTO) return
        val granted = permissions.filterIndexed { i, _ -> grantResults.getOrNull(i) == PackageManager.PERMISSION_GRANTED }
        when {
            Manifest.permission.ACCESS_FINE_LOCATION in granted -> loadAuto()
            Manifest.permission.ACCESS_COARSE_LOCATION in granted ->
                showMessage(getString(R.string.auto_error_precise), canRetry = true)
            else -> showMessage(getString(R.string.auto_error_permission), canRetry = true)
        }
    }

    private fun loadAuto() {
        save.isEnabled = false
        loadJob = scope.launch {
            val store = AutoMode.store(this@ConfigureActivity)
            if (store.savedAt() == null) {
                showLoading(getString(R.string.auto_loading_index))
                val error = withContext(Dispatchers.IO) {
                    try {
                        store.download()
                        null
                    } catch (e: IOException) {
                        e
                    }
                }
                if (error != null) {
                    showMessage(getString(R.string.auto_error_index, error.message ?: error.javaClass.simpleName), canRetry = true)
                    return@launch
                }
            }
            showLoading(getString(R.string.auto_loading_position))
            val fix = PositionReader(this@ConfigureActivity).read()
            if (fix == null || (fix.accuracyM ?: 0f) > AnchorPolicy.MAX_ACCURACY_M) {
                showMessage(getString(R.string.auto_error_position), canRetry = true)
                return@launch
            }
            val ranked = withContext(Dispatchers.IO) { AutoMode.ranking(this@ConfigureActivity, fix.point) }?.list.orEmpty()
            if (ranked.isEmpty()) {
                showMessage(getString(R.string.auto_error_no_stop), canRetry = true)
                return@launch
            }
            autoFix = fix
            autoFirst = ranked.first()
            val current = existing?.takeIf { it.auto }?.line?.id
            val preselected = ranked.firstOrNull { it.nearby.line.id == current } ?: ranked.first()
            adapter.setItems(
                ranked.take(AUTO_ROWS_SHOWN).map { row ->
                    RowItem(
                        title = UiText.lineTitle(row.nearby.line),
                        subtitle = getString(R.string.auto_row_detail, row.nearby.stopName, formatDistance(row.nearby.distanceM)),
                        badge = row.nearby.line,
                        checkable = true,
                        checked = row === preselected,
                        payload = row,
                    )
                },
            )
            showList()
            stepHelp.text = getString(R.string.auto_help)
            stepHelp.visibility = View.VISIBLE
            bottomBar.visibility = View.VISIBLE
            updateSaveEnabled()
        }
    }

    private fun saveAutoConfig() {
        val row = adapter.items.firstOrNull { it.checked }?.payload as? RankedLine ?: return
        val fix = autoFix ?: return
        AutoMode.saveConfiguration(
            context = this,
            widgetId = widgetId,
            nearby = row.nearby,
            fix = fix,
            theme = THEME_CHOICES[themeSpinner.selectedItemPosition].first,
            changedByUser = row !== autoFirst,
        )
        finishSaved()
    }

    // --- Étape 2 : ligne --------------------------------------------------------------------

    private fun loadLines(forceRefresh: Boolean) {
        val m = mode ?: return goTo(Step.MODE)
        showLoading(getString(R.string.loading_lines))
        loadJob = scope.launch {
            try {
                val lines = repository.lines(m, forceRefresh)
                if (lines.isEmpty()) {
                    showMessage(getString(R.string.no_result), canRetry = true)
                } else {
                    adapter.setItems(lines.map { l ->
                        RowItem(
                            title = UiText.lineTitle(l),
                            subtitle = l.subtitle().ifEmpty { null },
                            badge = l,
                            searchExtra = "${l.shortName} ${l.name} ${l.id}",
                            payload = l,
                        )
                    })
                    adapter.filter(search.text.toString())
                    showList()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(getString(R.string.error_referential, e.message ?: e.javaClass.simpleName), canRetry = true)
            }
        }
    }

    // --- Étape 3 : arrêt --------------------------------------------------------------------

    private fun loadStops(forceRefresh: Boolean) {
        val l = line ?: return goTo(Step.LINE)
        showLoading(getString(R.string.loading_stops))
        loadJob = scope.launch {
            try {
                val stops = repository.stops(l.id, forceRefresh)
                if (stops.isEmpty()) {
                    showMessage(getString(R.string.no_stop), canRetry = true)
                } else {
                    adapter.setItems(stops.map { s -> RowItem(title = s.name, subtitle = s.town, payload = s) })
                    adapter.filter(search.text.toString())
                    showList()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(getString(R.string.error_referential, e.message ?: e.javaClass.simpleName), canRetry = true)
            }
        }
    }

    // --- Étape 4 : sens ---------------------------------------------------------------------

    private fun loadDirections() {
        val l = line ?: return goTo(Step.LINE)
        val s = stop ?: return goTo(Step.STOP)
        save.isEnabled = false
        showLoading(getString(R.string.loading_directions))
        loadJob = scope.launch {
            val apiKey = prefs.apiKey
            val source = DataSources.passageSource(apiKey, prefs.primFormats)
            val results = withContext(Dispatchers.IO) {
                s.stopIds.map { id -> async { id to runCatching { source.passages(id, l.id) } } }.awaitAll()
            }
            directionOptions = Directions.buildOptions(
                s,
                results.associate { (id, result) ->
                    // Tracéo inclut les arrivées au terminus : elles ne sont pas un sens à proposer.
                    id to result.getOrNull()?.departures?.let { departures ->
                        if (apiKey.isBlank()) Directions.withoutTerminusArrivals(departures, s.name) else departures
                    }
                },
            )

            val allItem = RowItem(
                title = getString(R.string.direction_all),
                subtitle = getString(R.string.direction_all_sub),
                checkable = true,
                payload = AllDirections,
            )
            val optionItems = directionOptions.map { opt ->
                RowItem(title = opt.title, subtitle = opt.subtitle, checkable = true, payload = opt)
            }
            preselect(allItem, optionItems, l, s)
            adapter.setItems(if (oneDirectionOnly) optionItems else listOf(allItem) + optionItems)
            showList()

            val errors = results.mapNotNull { it.second.exceptionOrNull() }
            val help = getString(if (oneDirectionOnly) R.string.direction_help_single else R.string.direction_help)
            stepHelp.text = if (errors.isNotEmpty() && errors.size == results.size) {
                getString(R.string.error_directions, errors.first().message ?: "") + "\n\n" + help
            } else {
                help
            }
            stepHelp.visibility = View.VISIBLE
            updateSaveEnabled()
        }
    }

    /**
     * Coche les sens déjà choisis lors d'une précédente configuration du même arrêt. Pour un nouveau
     * widget, coche « Tous les sens » si son format affiche les deux sens, ou le seul sens proposé.
     */
    private fun preselect(allItem: RowItem, optionItems: List<RowItem>, l: Line, s: Stop) {
        val previous = existing?.takeIf { it.line.id == l.id && it.stop.stopIds == s.stopIds }?.directions
        val wasAllDirections = previous != null && s.stopIds.all { previous[it]?.isEmpty() == true }
        if (previous == null || wasAllDirections) {
            when {
                oneDirectionOnly -> optionItems.singleOrNull()?.checked = true
                wasAllDirections || chosenFormat?.showsBothDirections == true ||
                    directionOptions.none { it.key != null } -> allItem.checked = true
            }
            return
        }
        optionItems.forEach { item ->
            val opt = item.payload as DirectionOption
            val keys = previous[opt.stopId] ?: return@forEach
            val key = opt.key
            item.checked = keys.isEmpty() || (key != null && keys.any { normalizeLabel(it) == normalizeLabel(key) })
        }
        if (oneDirectionOnly) optionItems.filter { it.checked }.drop(1).forEach { it.checked = false }
    }

    private fun toggleDirection(item: RowItem) {
        if (!item.checkable) return
        item.checked = !item.checked
        if (item.payload === AllDirections) {
            if (item.checked) adapter.items.forEach { if (it !== item) it.checked = false }
        } else if (item.checked) {
            adapter.items.forEach { other ->
                if (other !== item && (oneDirectionOnly || other.payload === AllDirections)) other.checked = false
            }
        }
        adapter.notifyDataSetChanged()
        updateSaveEnabled()
    }

    private fun updateSaveEnabled() {
        save.isEnabled = adapter.items.any { it.checked }
    }

    private fun saveConfig() {
        if (step == Step.AUTO) {
            saveAutoConfig()
            return
        }
        val m = mode ?: return
        val l = line ?: return
        val s = stop ?: return
        val all = adapter.items.any { it.payload === AllDirections && it.checked }
        val selected = adapter.items.filter { it.checked }.mapNotNull { it.payload as? DirectionOption }
        if (!all && selected.isEmpty()) {
            Toast.makeText(this, R.string.direction_select_one, Toast.LENGTH_SHORT).show()
            return
        }
        val directions = if (all) Directions.allDirections(s) else Directions.toSelection(directionOptions, selected)
        val label = if (all) Directions.ALL_DIRECTIONS else Directions.label(directionOptions, selected, s)
        val theme = THEME_CHOICES[themeSpinner.selectedItemPosition].first
        prefs.saveConfig(WidgetConfig(widgetId, m, l, s, directions, label, theme))
        finishSaved()
    }

    private fun finishSaved() {
        WidgetUpdater.render(this, widgetId)
        WidgetUpdater.requestRefresh(this, widgetId)
        WidgetUpdater.scheduleTick(this)
        setResult(RESULT_OK, resultIntent())
        Toast.makeText(this, R.string.saved_toast, Toast.LENGTH_SHORT).show()
        finish()
    }
}
