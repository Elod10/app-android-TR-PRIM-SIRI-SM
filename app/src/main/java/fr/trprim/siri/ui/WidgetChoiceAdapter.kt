package fr.trprim.siri.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import fr.trprim.siri.R
import fr.trprim.siri.widget.WidgetEntry

/** Liste des widgets à ajouter : aperçu, nom, taille de départ et description. */
class WidgetChoiceAdapter(private val entries: List<WidgetEntry>) : BaseAdapter() {

    override fun getCount(): Int = entries.size
    override fun getItem(position: Int): WidgetEntry = entries[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val context = parent.context
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_widget_choice, parent, false)
        val entry = getItem(position)
        view.findViewById<ImageView>(R.id.choice_preview).setImageResource(entry.previewRes)
        view.findViewById<TextView>(R.id.choice_title).text =
            context.getString(R.string.widget_choice_title, context.getString(entry.nameRes), entry.size)
        view.findViewById<TextView>(R.id.choice_description).setText(entry.descriptionRes)
        return view
    }
}
