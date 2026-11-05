package fr.trprim.siri.widget

import fr.trprim.siri.data.Line
import fr.trprim.siri.data.WidgetTheme
import kotlin.math.pow

/** Couleurs d'un widget (ARGB). */
data class Palette(
    /** Vrai quand le fond du widget est la couleur de la ligne. */
    val isLine: Boolean,
    val background: Int,
    val text: Int,
    val text2: Int,
    val text3: Int,
    val late: Int,
    val cancelled: Int,
    /** Chiffre grisé lorsque l'actualisation a échoué. */
    val muted: Int,
    val rail: Int,
    val node: Int,
    val badgeBg: Int,
    val badgeFg: Int,
    val hair: Int,
    val chip: Int,
    /** Bloc neutre (voie supprimée ou données anciennes). */
    val surface: Int,
    /** Bloc de la voie et pastille « À quai ». */
    val tileBg: Int,
    val tileFg: Int,
    /** Étiquette de retard sur fond de couleur de ligne. */
    val tagBg: Int,
    val tagFg: Int,
)

object Palettes {

    private const val MIN_CONTRAST = 4.5
    private val INK = 0xFF15171C.toInt()
    private val PAPER = 0xFFF7F6F1.toInt()

    /** Palette d'un widget selon le thème choisi et son format. */
    fun resolve(theme: WidgetTheme, format: WidgetFormat, line: Line?): Palette {
        val color = line?.let { parseColor(it.color) ?: defaultColor(it.mode) } ?: 0xFF546E7A.toInt()
        val on = onColor(color, line?.let { parseColor(it.textColor) })
        val wantsLine = when (theme) {
            WidgetTheme.AUTO -> format.lineColorByDefault
            WidgetTheme.LINE -> true
            WidgetTheme.DARK, WidgetTheme.LIGHT -> false
        }
        return when {
            // La liste porte des retards en couleur, illisibles sur une couleur de ligne.
            wantsLine && line != null && format != WidgetFormat.LISTE -> linePalette(color, on)
            theme == WidgetTheme.LIGHT -> lightPalette(color, on)
            else -> darkPalette(color, on)
        }
    }

    private fun darkPalette(line: Int, on: Int) = Palette(
        isLine = false,
        background = INK,
        text = 0xFFF5F3EC.toInt(),
        text2 = 0xFFB7BBC3.toInt(),
        text3 = 0xFF8F949E.toInt(),
        late = 0xFFFFB03B.toInt(),
        cancelled = 0xFFFF8F80.toInt(),
        muted = 0xFF8F949E.toInt(),
        rail = line,
        node = 0xFFF5F3EC.toInt(),
        badgeBg = line,
        badgeFg = on,
        hair = 0xFF2A2D35.toInt(),
        chip = 0xFF2A2D35.toInt(),
        surface = 0xFF23262D.toInt(),
        tileBg = line,
        tileFg = on,
        tagBg = 0xFFFFB03B.toInt(),
        tagFg = INK,
    )

    private fun lightPalette(line: Int, on: Int) = Palette(
        isLine = false,
        background = PAPER,
        text = INK,
        text2 = 0xFF4A4F58.toInt(),
        text3 = 0xFF60656F.toInt(),
        late = 0xFF945000.toInt(),
        cancelled = 0xFFBE2A1C.toInt(),
        muted = 0xFF60656F.toInt(),
        rail = line,
        node = INK,
        badgeBg = line,
        badgeFg = on,
        hair = 0xFFDEDDD5.toInt(),
        chip = 0xFFE9E8E1.toInt(),
        surface = 0xFFE9E8E1.toInt(),
        tileBg = line,
        tileFg = on,
        tagBg = 0xFF945000.toInt(),
        tagFg = PAPER,
    )

    private fun linePalette(line: Int, on: Int): Palette {
        val ground = ensureContrast(line, on)
        return Palette(
            isLine = true,
            background = ground,
            text = on,
            text2 = on,
            text3 = on,
            late = on,
            cancelled = on,
            muted = withAlpha(on, 0x80),
            rail = on,
            node = on,
            badgeBg = on,
            badgeFg = ground,
            hair = withAlpha(on, 0x40),
            chip = withAlpha(on, 0x20),
            surface = withAlpha(on, 0x20),
            tileBg = on,
            tileFg = ground,
            tagBg = on,
            tagFg = ground,
        )
    }

    /** Couleur de texte d'une ligne : celle du référentiel si elle est lisible, sinon noir ou blanc. */
    fun onColor(background: Int, preferred: Int?): Int {
        if (preferred != null && contrast(background, preferred) >= 3.0) return preferred
        return if (contrast(background, INK) >= contrast(background, 0xFFFFFFFF.toInt())) INK else 0xFFFFFFFF.toInt()
    }

    /** Assombrit ou éclaircit le fond jusqu'à ce que le petit texte reste lisible. */
    fun ensureContrast(background: Int, on: Int): Int {
        var ground = background
        val darken = luminance(on) > luminance(background)
        var steps = 0
        while (contrast(ground, on) < MIN_CONTRAST && steps < 30) {
            ground = if (darken) scale(ground, 0.95) else lighten(ground, 0.08)
            steps++
        }
        return ground
    }

    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((color shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun scale(color: Int, factor: Double): Int = mapChannels(color) { (it * factor).toInt() }

    private fun lighten(color: Int, amount: Double): Int = mapChannels(color) { (it + (255 - it) * amount).toInt() }

    private inline fun mapChannels(color: Int, transform: (Int) -> Int): Int {
        val r = transform((color shr 16) and 0xFF).coerceIn(0, 255)
        val g = transform((color shr 8) and 0xFF).coerceIn(0, 255)
        val b = transform(color and 0xFF).coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    /** Lit une couleur « RRGGBB » ou « #RRGGBB » du référentiel. */
    fun parseColor(hex: String?): Int? {
        val digits = hex?.trim()?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
        val value = digits.toLongOrNull(16) ?: return null
        return (0xFF000000L or value).toInt()
    }

    private fun defaultColor(mode: String): Int = when (mode) {
        "metro" -> 0xFF003CA6.toInt()
        "rail" -> 0xFF7A2E8C.toInt()
        "tram" -> 0xFF0B7A75.toInt()
        "bus" -> 0xFF2E7D32.toInt()
        else -> 0xFF546E7A.toInt()
    }
}
