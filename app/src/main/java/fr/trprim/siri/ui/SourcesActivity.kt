package fr.trprim.siri.ui

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import fr.trprim.siri.R
import fr.trprim.siri.data.Prefs

/** Sources et licences : les mentions dépendent de la source active. */
class SourcesActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sources)
        findViewById<View>(R.id.back).setOnClickListener { finish() }

        val about = findViewById<TextView>(R.id.about)
        about.setText(if (Prefs(this).hasApiKey) R.string.main_about_prim else R.string.main_about_traceo)
        about.movementMethod = LinkMovementMethod.getInstance()

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
