package fr.trprim.siri.ui

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import fr.trprim.siri.R
import fr.trprim.siri.data.PositionReader
import fr.trprim.siri.data.Prefs
import fr.trprim.siri.data.TimeWindow
import fr.trprim.siri.widget.PassageNotifications
import fr.trprim.siri.widget.QuickTile
import fr.trprim.siri.widget.WidgetFormat
import fr.trprim.siri.widget.WidgetProviders
import fr.trprim.siri.widget.WidgetUpdater
import java.time.DayOfWeek
import kotlin.math.roundToInt

/**
 * Réglages d'un widget pour l'écran de verrouillage : sa notification, le suivi de la position sans
 * toucher le widget, la plage horaire où les deux s'appliquent et la tuile des réglages rapides.
 * Chaque choix est enregistré aussitôt.
 */
class LockScreenActivity : Activity() {

    companion object {
        private const val REQUEST_PRECISE = 51
        private const val REQUEST_BACKGROUND = 52
        private const val DAY_SIZE_DP = 38

        fun intent(context: Context, widgetId: Int): Intent =
            Intent(context, LockScreenActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

        /** Vrai si le widget a sa notification ou suit la position : la cloche de sa carte est pleine. */
        fun isActive(context: Context, prefs: Prefs, widgetId: Int): Boolean =
            (prefs.notifies(widgetId) && PassageNotifications.canPost(context)) ||
                (prefs.followsPosition(widgetId) && PositionReader(context).hasBackgroundAccess)

        /** « Notification · Position suivie · Mardi · 16:00 – 16:30 », ou « Désactivé ». */
        fun summary(context: Context, prefs: Prefs, widgetId: Int): String {
            val parts = ArrayList<String>()
            if (prefs.notifies(widgetId) && PassageNotifications.canPost(context)) {
                parts += context.getString(R.string.lock_summary_notify)
            }
            if (prefs.followsPosition(widgetId) && PositionReader(context).hasBackgroundAccess) {
                parts += context.getString(R.string.lock_summary_follow)
            }
            if (parts.isEmpty()) return context.getString(R.string.lock_summary_off)
            prefs.timeWindow(widgetId)?.let { parts += it.label() }
            return parts.joinToString(" · ")
        }
    }

    private lateinit var prefs: Prefs
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var notifySwitch: Switch
    private lateinit var followSwitch: Switch
    private lateinit var followStatus: TextView
    private lateinit var followAllow: Button
    private lateinit var windowChoice: RadioGroup
    private lateinit var windowDetails: View
    private lateinit var days: LinearLayout
    private lateinit var start: Button
    private lateinit var end: Button

    /** La plage réglée, gardée même quand on repasse sur « Tout le temps ». */
    private var draft = TimeWindow.DEFAULT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContentView(R.layout.activity_lock_screen)
        title = getString(R.string.lock_title)
        prefs = Prefs(this)

        notifySwitch = findViewById(R.id.notify_switch)
        followSwitch = findViewById(R.id.follow_switch)
        followStatus = findViewById(R.id.follow_status)
        followAllow = findViewById(R.id.follow_allow)
        windowChoice = findViewById(R.id.window_choice)
        windowDetails = findViewById(R.id.window_details)
        days = findViewById(R.id.window_days)
        start = findViewById(R.id.window_start)
        end = findViewById(R.id.window_end)

        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.lock_widget).text = widgetName()

        val format = WidgetProviders.formatOf(AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider?.className)
        val follows = prefs.config(widgetId)?.auto == true || format == WidgetFormat.PROXIMITE
        findViewById<View>(R.id.follow_section).visibility = if (follows) View.VISIBLE else View.GONE

        notifySwitch.setOnCheckedChangeListener(onNotifyChanged)
        followSwitch.isChecked = prefs.followsPosition(widgetId)
        followSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.setFollowsPosition(widgetId, checked)
            if (checked) requestBackgroundAccess()
            renderFollow()
        }
        followAllow.setOnClickListener { requestBackgroundAccess() }

        val saved = prefs.timeWindow(widgetId)
        draft = saved ?: TimeWindow.DEFAULT
        windowChoice.check(if (saved != null) R.id.window_some else R.id.window_always)
        windowChoice.setOnCheckedChangeListener { _, _ -> saveWindow() }
        start.setOnClickListener { pickTime(draft.startMinute) { draft = draft.copy(startMinute = it) } }
        end.setOnClickListener { pickTime(draft.endMinute) { draft = draft.copy(endMinute = it) } }
        renderWindow()

        findViewById<View>(R.id.tile_choose).setOnClickListener {
            prefs.tileWidget = widgetId
            WidgetUpdater.render(this, widgetId)
            renderTile()
        }
        val add = findViewById<View>(R.id.tile_add)
        add.visibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) View.VISIBLE else View.GONE
        add.setOnClickListener {
            // Android affiche sa propre demande ; une tuile déjà ajoutée n'est pas proposée deux fois.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) QuickTile.requestAdd(this, mainExecutor) { }
        }
        renderTile()
    }

    // --- Tuile des réglages rapides ---------------------------------------------------------------

    private fun renderTile() {
        val shown = QuickTile.widgetId(this) == widgetId
        findViewById<TextView>(R.id.tile_status).setText(if (shown) R.string.lock_tile_this else R.string.lock_tile_other)
        findViewById<View>(R.id.tile_choose).visibility = if (shown) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        // Une autorisation donnée ou retirée dans les réglages d'Android change ce qui est possible.
        val notifies = prefs.notifies(widgetId) && PassageNotifications.canPost(this)
        if (notifySwitch.isChecked != notifies) {
            notifySwitch.setOnCheckedChangeListener(null)
            notifySwitch.isChecked = notifies
            notifySwitch.setOnCheckedChangeListener(onNotifyChanged)
        }
        renderFollow()
    }

    /** Activer la notification demande d'abord l'autorisation, si Android l'exige. */
    private val onNotifyChanged = CompoundButton.OnCheckedChangeListener { _, checked ->
        when {
            !checked -> setNotify(false)
            NotificationAccess.ensure(this) -> setNotify(true)
            else -> notifySwitch.isChecked = false
        }
    }

    private fun widgetName(): String {
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider?.className
        val name = WidgetProviders.entryOf(provider)?.let { getString(it.nameRes) } ?: getString(R.string.widget_name)
        val config = prefs.config(widgetId) ?: return name
        if (WidgetProviders.formatOf(provider) == WidgetFormat.PROXIMITE) return name
        return listOf(name, UiText.lineTitle(config.line), config.stop.name).joinToString(" · ")
    }

    // --- Notification ---------------------------------------------------------------------------

    private fun setNotify(enabled: Boolean) {
        PassageNotifications.setEnabled(this, widgetId, enabled)
        WidgetUpdater.render(this, widgetId)
    }

    // --- Position --------------------------------------------------------------------------------

    private fun requestBackgroundAccess() {
        val reader = PositionReader(this)
        when {
            reader.hasBackgroundAccess -> renderFollow()
            !reader.hasPreciseAccess -> requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_PRECISE,
            )
            // Depuis Android 11, cette demande mène à la page de la permission, où l'on choisit « Toujours autoriser ».
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> requestPermissions(
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                REQUEST_BACKGROUND,
            )
        }
    }

    private fun renderFollow() {
        val missing = followSwitch.isChecked && !PositionReader(this).hasBackgroundAccess
        val precise = PositionReader(this).hasPreciseAccess
        followStatus.setText(if (precise) R.string.lock_follow_missing else R.string.lock_follow_precise)
        followStatus.visibility = if (missing) View.VISIBLE else View.GONE
        followAllow.visibility = if (missing) View.VISIBLE else View.GONE
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            NotificationAccess.REQUEST_CODE -> notifySwitch.isChecked = NotificationAccess.onResult(this, permissions, grantResults)
            REQUEST_PRECISE -> if (PositionReader(this).hasPreciseAccess) requestBackgroundAccess() else renderFollow()
            REQUEST_BACKGROUND -> {
                val refusedForGood = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    !PositionReader(this).hasBackgroundAccess &&
                    !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                if (refusedForGood) openAppSettings()
                renderFollow()
            }
        }
    }

    private fun openAppSettings() {
        Toast.makeText(this, R.string.lock_follow_settings, Toast.LENGTH_LONG).show()
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } catch (e: ActivityNotFoundException) {
            // Le message suffit.
        }
    }

    // --- Plage horaire ---------------------------------------------------------------------------

    private val someHours: Boolean get() = windowChoice.checkedRadioButtonId == R.id.window_some

    private fun saveWindow() {
        prefs.setTimeWindow(widgetId, if (someHours) draft else null)
        renderWindow()
        // La notification apparaît ou disparaît tout de suite.
        WidgetUpdater.render(this, widgetId)
    }

    private fun renderWindow() {
        windowDetails.visibility = if (someHours) View.VISIBLE else View.GONE
        start.text = getString(R.string.lock_window_from, TimeWindow.clock(draft.startMinute))
        end.text = getString(R.string.lock_window_to, TimeWindow.clock(draft.endMinute))
        renderDays()
    }

    private fun renderDays() {
        days.removeAllViews()
        val size = (DAY_SIZE_DP * resources.displayMetrics.density).roundToInt()
        DayOfWeek.entries.forEach { day ->
            val chosen = day in draft.days
            val chip = TextView(this).apply {
                layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
                gravity = Gravity.CENTER
                text = TimeWindow.initial(day)
                textSize = 15f
                setTextColor(getColorStateList(R.color.day_text))
                try {
                    typeface = resources.getFont(R.font.barlow_semicondensed_semibold)
                } catch (e: Resources.NotFoundException) {
                    // Police du système.
                }
                background = getDrawable(R.drawable.bg_day)
                isSelected = chosen
                contentDescription = getString(if (chosen) R.string.lock_day_on else R.string.lock_day_off, TimeWindow.dayName(day))
                setOnClickListener { toggleDay(day) }
            }
            val cell = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, size, 1f)
                addView(chip)
            }
            days.addView(cell)
        }
    }

    private fun toggleDay(day: DayOfWeek) {
        val next = if (day in draft.days) draft.days - day else draft.days + day
        if (next.isEmpty()) {
            Toast.makeText(this, R.string.lock_window_one_day, Toast.LENGTH_SHORT).show()
            return
        }
        draft = draft.copy(days = next)
        saveWindow()
    }

    private fun pickTime(minute: Int, apply: (Int) -> Unit) {
        TimePickerDialog(
            this,
            { _, hour, min ->
                apply(hour * 60 + min)
                saveWindow()
            },
            minute / 60,
            minute % 60,
            true,
        ).show()
    }
}
