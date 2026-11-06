package fr.trprim.siri.widget

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Icon
import fr.trprim.siri.R
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Dessine l'icône de la tuile : le prochain passage en blanc sur fond transparent. Android la teinte
 * comme ses propres icônes, selon l'état de la tuile. Une petite tuile n'affiche que cette icône.
 */
object TileIconRenderer {

    private const val SIZE_DP = 24f
    private const val MIN_SIZE_PX = 48

    /** Part de la largeur que le texte peut occuper. */
    private const val MAX_WIDTH = 0.96f

    fun render(context: Context, icon: TileIcon): Icon {
        val side = max(MIN_SIZE_PX, (SIZE_DP * context.resources.displayMetrics.density).roundToInt())
        val size = side.toFloat()
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val unit = icon.unit
        // Seule, une heure (« 17:05 ») est plus large que haute : elle est écrite plus petit.
        val value = paint(face(context, R.font.barlow_condensed_bold), size * if (unit != null) 0.74f else 0.5f)
        fitWidth(value, icon.value, size * MAX_WIDTH)
        val valueBounds = bounds(value, icon.value)
        if (unit == null) {
            canvas.drawText(icon.value, size / 2f, (size - valueBounds.height()) / 2f - valueBounds.top, value)
        } else {
            val unitPaint = paint(face(context, R.font.barlow_condensed_semibold), size * 0.34f)
            val unitBounds = bounds(unitPaint, unit)
            val gap = size * 0.04f
            val top = (size - valueBounds.height() - gap - unitBounds.height()) / 2f
            canvas.drawText(icon.value, size / 2f, top - valueBounds.top, value)
            canvas.drawText(unit, size / 2f, top + valueBounds.height() + gap - unitBounds.top, unitPaint)
        }
        return Icon.createWithBitmap(bitmap)
    }

    private fun paint(typeface: Typeface, textSize: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        this.typeface = typeface
        this.textSize = textSize
        textAlign = Paint.Align.CENTER
    }

    private fun fitWidth(paint: Paint, text: String, maxWidth: Float) {
        val width = paint.measureText(text)
        if (width > maxWidth) paint.textSize *= maxWidth / width
    }

    private fun bounds(paint: Paint, text: String) = Rect().also { paint.getTextBounds(text, 0, text.length, it) }

    private fun face(context: Context, id: Int): Typeface = try {
        context.resources.getFont(id)
    } catch (e: Resources.NotFoundException) {
        Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
}
