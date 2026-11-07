package fr.gotraceo.live.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import fr.gotraceo.live.R
import fr.gotraceo.live.data.DataSources
import fr.gotraceo.live.data.Prefs
import fr.gotraceo.live.widget.QuotaAlerts
import fr.gotraceo.live.widget.WidgetUpdater

/** Réglages : source des données, clé API, fréquence d'actualisation, sources et confidentialité. */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        SystemBars.pad(this)
        prefs = Prefs(this)

        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<View>(R.id.open_key).setOnClickListener { startActivity(Intent(this, KeyActivity::class.java)) }
        findViewById<View>(R.id.open_sources).setOnClickListener { startActivity(Intent(this, SourcesActivity::class.java)) }
        findViewById<View>(R.id.open_privacy).setOnClickListener { PrivacyPolicy.open(this) }
        findViewById<View>(R.id.open_support).setOnClickListener { Support.open(this) }
    }

    override fun onResume() {
        super.onResume()
        // La source, le texte d'aide et les fréquences proposées dépendent de la clé.
        val hasKey = prefs.hasApiKey
        val paused = prefs.primPaused
        findViewById<TextView>(R.id.source_title).setText(
            when {
                paused -> R.string.main_source_prim_paused
                hasKey -> R.string.main_source_prim
                else -> R.string.main_source_traceo
            },
        )
        findViewById<TextView>(R.id.source_detail).setText(
            when {
                paused -> R.string.main_source_prim_paused_detail
                hasKey -> R.string.main_source_prim_detail
                else -> R.string.main_source_traceo_detail
            },
        )
        findViewById<TextView>(R.id.key_title).setText(if (hasKey) R.string.settings_key_edit else R.string.settings_key_add)
        val usage = prefs.primUsage()
        findViewById<TextView>(R.id.key_subtitle).text = if (hasKey) {
            getString(R.string.settings_key_usage, QuotaAlerts.number(usage.used), QuotaAlerts.number(usage.quota))
        } else {
            getString(R.string.settings_key_add_sub)
        }
        findViewById<TextView>(R.id.refresh_help).setText(if (hasKey) R.string.main_refresh_help_key else R.string.main_refresh_help)
        renderRefreshChoices(hasKey)
    }

    private fun renderRefreshChoices(hasKey: Boolean) {
        val group = findViewById<RadioGroup>(R.id.refresh_choices)
        group.setOnCheckedChangeListener(null)
        group.removeAllViews()
        val choices = DataSources.refreshChoices(Prefs.REFRESH_CHOICES, hasKey)
        val current = DataSources.refreshMinutes(prefs.refreshMinutes, hasKey).takeIf { it in choices }
            ?: Prefs.DEFAULT_REFRESH_MINUTES
        choices.forEach { minutes ->
            val button = layoutInflater.inflate(R.layout.item_refresh_choice, group, false) as RadioButton
            button.id = View.generateViewId()
            button.tag = minutes
            button.text = when (minutes) {
                0 -> getString(R.string.refresh_manual)
                1 -> getString(R.string.refresh_every_minute)
                else -> getString(R.string.refresh_every_n, minutes)
            }
            group.addView(button)
            if (minutes == current) group.check(button.id)
        }
        group.setOnCheckedChangeListener { radio, checkedId ->
            val minutes = radio.findViewById<View>(checkedId)?.tag as? Int ?: return@setOnCheckedChangeListener
            // Comparer à l'intervalle appliqué conserve « Chaque minute » pour le retour d'une clé.
            if (minutes != DataSources.refreshMinutes(prefs.refreshMinutes, hasKey)) {
                prefs.refreshMinutes = minutes
                WidgetUpdater.scheduleTick(this)
            }
        }
    }
}
