package fr.trprim.siri.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import fr.trprim.siri.R
import fr.trprim.siri.data.Line
import fr.trprim.siri.data.normalizeLabel
import fr.trprim.siri.widget.BadgeRenderer

/** Élément d'une liste de l'assistant de configuration. */
class RowItem(
    val title: String,
    val subtitle: String? = null,
    /** Ligne dont la pastille est affichée en icône (dessinée à la demande). */
    val badge: Line? = null,
    val checkable: Boolean = false,
    var checked: Boolean = false,
    /** Texte supplémentaire pris en compte par la recherche. */
    val searchExtra: String = "",
    val payload: Any? = null,
) {
    val searchText: String = normalizeLabel("$title ${subtitle.orEmpty()} $searchExtra")
}

/** Adaptateur simple avec filtrage par texte. */
class RowAdapter(private val context: Context) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)
    private val badgeCache = LruCache<String, Bitmap>(80)
    private var all: List<RowItem> = emptyList()
    private var visible: List<RowItem> = emptyList()
    private var query: String = ""

    fun setItems(items: List<RowItem>) {
        all = items
        applyFilter()
    }

    fun filter(text: String) {
        query = normalizeLabel(text)
        applyFilter()
    }

    val items: List<RowItem> get() = all

    private fun applyFilter() {
        val words = query.split(' ').filter { it.isNotEmpty() }
        visible = if (words.isEmpty()) all else all.filter { item -> words.all { item.searchText.contains(it) } }
        notifyDataSetChanged()
    }

    override fun getCount(): Int = visible.size
    override fun getItem(position: Int): RowItem = visible[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_row, parent, false)
        val item = getItem(position)
        val icon = view.findViewById<ImageView>(R.id.item_icon)
        val title = view.findViewById<TextView>(R.id.item_title)
        val subtitle = view.findViewById<TextView>(R.id.item_subtitle)
        val check = view.findViewById<CheckBox>(R.id.item_check)

        val badge = item.badge
        if (badge != null) {
            icon.setImageBitmap(badgeFor(badge))
            icon.visibility = View.VISIBLE
        } else {
            icon.setImageDrawable(null)
            icon.visibility = View.GONE
        }
        title.text = item.title
        subtitle.text = item.subtitle.orEmpty()
        subtitle.visibility = if (item.subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
        check.visibility = if (item.checkable) View.VISIBLE else View.GONE
        check.isChecked = item.checked
        return view
    }

    private fun badgeFor(line: Line): Bitmap {
        val key = "${line.mode}|${line.shortName}|${line.color}|${line.textColor}"
        return badgeCache.get(key) ?: BadgeRenderer.render(context, line).also { badgeCache.put(key, it) }
    }
}
