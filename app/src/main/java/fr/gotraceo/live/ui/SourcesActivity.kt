package fr.gotraceo.live.ui

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import fr.gotraceo.live.R
import fr.gotraceo.live.data.Prefs
import fr.gotraceo.live.widget.AutoMode
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Sources et licences : les mentions dépendent de la source active. */
class SourcesActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sources)
        SystemBars.pad(this)
        findViewById<View>(R.id.back).setOnClickListener { finish() }

        val about = findViewById<TextView>(R.id.about)
        about.setText(if (Prefs(this).hasApiKey) R.string.main_about_prim else R.string.main_about_traceo)
        about.movementMethod = LinkMovementMethod.getInstance()
        findViewById<TextView>(R.id.code_licence).movementMethod = LinkMovementMethod.getInstance()

        // La Licence Ouverte demande la date des données réutilisées : celle de l'index, s'il existe.
        AutoMode.store(this).savedAt()?.let { savedAt ->
            val date = DateFormat.getDateInstance(DateFormat.LONG, Locale.FRENCH).format(Date(savedAt))
            findViewById<TextView>(R.id.index_date).apply {
                text = getString(R.string.sources_index_date, date)
                visibility = View.VISIBLE
            }
        }

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        val versionView = findViewById<TextView>(R.id.version)
        if (version == null) {
            versionView.visibility = View.GONE
        } else {
            versionView.text = getString(R.string.sources_version, version)
        }
    }
}
