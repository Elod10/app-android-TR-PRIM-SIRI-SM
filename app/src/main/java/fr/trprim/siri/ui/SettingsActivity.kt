package fr.trprim.siri.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import fr.trprim.siri.R
import fr.trprim.siri.data.DataSources
import fr.trprim.siri.data.Prefs
import fr.trprim.siri.widget.WidgetUpdater

/** Réglages : source des données, clé API et fréquence d'actualisation. */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)

        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<View>(R.id.open_key).setOnClickListener { startActivity(Intent(this, KeyActivity::class.java)) }
        findViewById<View>(R.id.open_sources).setOnClickListener { startActivity(Intent(this, SourcesActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        // La source, le texte d'aide et les fréquences proposées dépendent de la clé.
        val hasKey = prefs.hasApiKey
        findViewById<TextView>(R.id.source_title).setText(if (hasKey) R.string.main_source_prim else R.string.main_source_traceo)
        findViewById<TextView>(R.id.source_detail).setText(
            if (hasKey) R.string.main_source_prim_detail else R.string.main_source_traceo_detail,
        )
        findViewById<TextView>(R.id.key_title).setText(if (hasKey) R.string.settings_key_edit else R.string.settings_key_add)
        findViewById<TextView>(R.id.key_subtitle).setText(if (hasKey) R.string.settings_key_edit_sub else R.string.settings_key_add_sub)
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
