package fr.gotraceo.live.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import fr.gotraceo.live.R
import fr.gotraceo.live.data.Prefs
import fr.gotraceo.live.widget.WidgetEntry
import fr.gotraceo.live.widget.WidgetUpdater
import kotlin.math.min
import kotlin.math.roundToInt

/** Galerie des formats : le format touché est ajouté à l'écran d'accueil, puis configuré. */
class AddWidgetActivity : Activity() {

    /** Widgets présents au moment de la demande, pour reconnaître celui qui vient d'être ajouté. */
    private var idsBeforePin: Set<Int>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_widget)
        findViewById<View>(R.id.back).setOnClickListener { finish() }

        val container = findViewById<LinearLayout>(R.id.gallery)
        WidgetGallery.sections.forEach { section ->
            val title = layoutInflater.inflate(R.layout.item_gallery_title, container, false) as TextView
            title.setText(section.titleRes)
            container.addView(title)
            section.entries.forEach { container.addView(entryView(it, container)) }
        }
        container.addView(adaptiveView(container))
    }

    override fun onResume() {
        super.onResume()
        val before = idsBeforePin ?: return
        val added = WidgetUpdater.widgetIds(this).firstOrNull { it !in before } ?: return
        idsBeforePin = null
        // La configuration part normalement toute seule : ceci couvre un système qui l'aurait bloquée.
        if (Prefs(this).config(added) == null && !ConfigureActivity.isOpen(added)) {
            startActivity(WidgetPinner.configureIntent(this).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, added))
        }
        finish()
    }

    private fun entryView(entry: WidgetEntry, parent: LinearLayout): View {
        val format = requireNotNull(entry.format)
        val view = layoutInflater.inflate(R.layout.item_gallery, parent, false)
        val density = resources.displayMetrics.density
        val available = resources.displayMetrics.widthPixels - 2 * resources.getDimension(R.dimen.screen_padding)
        val width = min(format.baseWidth * density, available)
        val height = width * format.baseHeight / format.baseWidth
        val preview = view.findViewById<ImageView>(R.id.gallery_preview)
        preview.setImageResource(entry.previewRes)
        val sideBySide = width < available * SIDE_BY_SIDE_BELOW
        preview.layoutParams = LinearLayout.LayoutParams(width.roundToInt(), height.roundToInt()).apply {
            bottomMargin = if (sideBySide) 0 else (8 * density).roundToInt()
        }

        // Les petits formats gardent leur description à côté d'eux.
        if (sideBySide) {
            val root = view as LinearLayout
            root.orientation = LinearLayout.HORIZONTAL
            root.gravity = Gravity.CENTER_VERTICAL
            view.findViewById<View>(R.id.gallery_texts).layoutParams =
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (14 * density).roundToInt()
                }
        }
        bindTexts(view, entry)
        return view
    }

    private fun adaptiveView(parent: LinearLayout): View {
        val view = layoutInflater.inflate(R.layout.item_gallery, parent, false)
        view.findViewById<View>(R.id.gallery_preview).visibility = View.GONE
        view.setBackgroundResource(R.drawable.bg_card_touch)
        val padding = resources.getDimensionPixelSize(R.dimen.card_padding)
        view.setPadding(padding, padding, padding, padding)
        bindTexts(view, WidgetGallery.adaptive)
        return view
    }

    private fun bindTexts(view: View, entry: WidgetEntry) {
        view.findViewById<TextView>(R.id.gallery_title).text =
            getString(R.string.widget_choice_title, getString(entry.nameRes), entry.size)
        view.findViewById<TextView>(R.id.gallery_description).setText(entry.descriptionRes)
        view.setOnClickListener { pin(entry) }
    }

    private fun pin(entry: WidgetEntry) {
        idsBeforePin = WidgetUpdater.widgetIds(this).toSet()
        if (!WidgetPinner.request(this, entry.provider)) {
            idsBeforePin = null
            Toast.makeText(this, R.string.main_add_widget_unsupported, Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val SIDE_BY_SIDE_BELOW = 0.62f
    }
}
