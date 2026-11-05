package org.njarasoa.fijerena.core.ui.components

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaProfileColors
import org.njarasoa.fijerena.core.ui.theme.CinemaShapeRoles
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A profile's avatar: its colour, with the letters of its name ([avatarLetters]). One letter has the
 * size of [initialRole], times [initialScale] for an avatar that is much larger than the text.
 * Several letters are one block in the Look's title font, in its emphasis weight: 2 or 3 in one line, 4 as 2 above
 * 2, centred on the letters' own outlines and as large as the avatar allows, so they touch its edge.
 * The avatar is as round as the Look's buttons ([CinemaShapeRoles.avatar]).
 */
@Composable
fun ProfileAvatar(
    name: String,
    colorIndex: Int,
    size: Dp,
    initialRole: TextRole,
    modifier: Modifier = Modifier,
    initialScale: Float = 1f,
) {
    val letters = avatarLetters(name)
    // One letter has its role's size, times the scale for a large avatar.
    val letterSize = textStyleOf(initialRole).fontSize.let { if (it.isSpecified) it * initialScale else it }
    val shapes = CinemaTheme.shapes
    Box(
        modifier =
            modifier
                .size(size)
                .clip(shapes.avatar)
                .background(CinemaProfileColors.forIndex(colorIndex))
                // The letters read as one word ("admi"), not one text per letter.
                .semantics { text = AnnotatedString(letters.joinToString("")) },
        contentAlignment = Alignment.Center,
    ) {
        if (letters.size <= 1) {
            AvatarLetter(letters.firstOrNull().orEmpty(), letterSize)
        } else {
            // 4 letters as 2 above 2; 2 or 3 in one line.
            LetterBlock(
                if (letters.size == 4) letters.chunked(2) else listOf(listOf(letters.joinToString(""))),
                cornerPercent = shapes.avatarCornerPercent,
            )
        }
    }
}

/**
 * [rows] of letters drawn by their outlines, as one block centred in the avatar: one row is one
 * word; two rows are a grid, each letter centred in its column. The block is as large as the
 * avatar's shape (corner radius [cornerPercent] of its size) holds, so its outermost points touch it.
 */
@Composable
private fun LetterBlock(
    rows: List<List<String>>,
    cornerPercent: Int,
) {
    val resolver = LocalFontFamilyResolver.current
    val family = CinemaTheme.type.heading.fontFamily
    // The Look's title font, in its emphasis weight.
    val weight = emphasisWeight
    val typeface = remember(resolver, family, weight) { resolver.resolve(family, weight).value as Typeface }
    val colour = CinemaProfileColors.onAvatar.toArgb()
    Canvas(modifier = Modifier.fillMaxSize()) {
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = MEASURE_TEXT_SIZE
                color = colour
            }
        // Every cell's outline at the measuring size, relative to its baseline.
        val bounds = rows.map { row -> row.map { cell -> Rect().also { paint.getTextBounds(cell, 0, cell.length, it) } } }
        val columns = rows.maxOf { it.size }
        val columnWidths = (0 until columns).map { c -> bounds.maxOf { row -> row.getOrNull(c)?.width() ?: 0 }.toFloat() }
        val rowTops = bounds.map { row -> row.minOf { it.top }.toFloat() }
        val rowHeights = bounds.mapIndexed { r, row -> row.maxOf { it.bottom } - rowTops[r] }
        val columnGap = rowHeights.max() * COLUMN_GAP_SHARE
        val rowGap = rowHeights.max() * ROW_GAP_SHARE
        val blockWidth = columnWidths.sum() + columnGap * (columns - 1)
        // Where each letter goes at the measuring size: in a grid each leans toward the centre (the
        // left column's to the right of its cell, the right column's to the left), each row on one
        // baseline. One row is one word, centred.
        // The letters' exact outlines (glyph paths) at the measuring size: each centred in its
        // column, each row on one baseline (rows sit close, so they lean toward the centre).
        val ink = Path()
        var rowTop = 0f
        rows.forEachIndexed { r, row ->
            val baseline = rowTop - rowTops[r]
            var columnLeft = -blockWidth / 2
            row.forEachIndexed { c, cell ->
                val b = bounds[r][c]
                // Each letter centred in its column.
                val x = columnLeft + (columnWidths[c] - b.width()) / 2 - b.left
                ink.addPath(Path().also { paint.getTextPath(cell, 0, cell.length, x, baseline, it) })
                columnLeft += columnWidths[c] + columnGap
            }
            rowTop += rowHeights[r] + rowGap
        }
        // Centred on the ink itself, then as large as the shape allows: every point of every outline
        // inside the avatar's shape (its corners included), so the outermost ones touch its edge.
        val inked = RectF().also { ink.computeBounds(it, true) }
        val midX = inked.centerX()
        val midY = inked.centerY()
        val half = size.minDimension / 2
        val cornerRadius = size.minDimension * cornerPercent / 100f
        val points = ink.approximate(0.5f)
        var scale = Float.MAX_VALUE
        for (k in 0 until points.size / 3) {
            val dx = abs(points[k * 3 + 1] - midX)
            val dy = abs(points[k * 3 + 2] - midY)
            val distance = sqrt(dx * dx + dy * dy)
            if (distance > 0f) scale = minOf(scale, edgeDistance(dx / distance, dy / distance, half, cornerRadius) / distance)
        }
        ink.transform(
            Matrix().apply {
                setTranslate(-midX, -midY)
                postScale(scale, scale)
                postTranslate(center.x, center.y)
            },
        )
        drawIntoCanvas { canvas -> canvas.nativeCanvas.drawPath(ink, paint) }
    }
}

@Composable
private fun AvatarLetter(
    letter: String,
    size: TextUnit,
) {
    // look-ok: the initial is sized to the avatar, in the Look's title font (a name's font)
    Text(
        text = letter,
        // The avatar reads its letters as one word (above).
        modifier = Modifier.clearAndSetSemantics {},
        style =
            CinemaTheme.type.heading.copy(
                // look-ok: the initial's size from its role, above
                fontSize = size,
                fontWeight = emphasisWeight,
                lineHeight = TextUnit.Unspecified,
            ),
        color = CinemaProfileColors.onAvatar,
    )
}

/** The text size the lines are measured at, before they are scaled to the avatar. */
private const val MEASURE_TEXT_SIZE = 100f

/** The gap between the two columns of a grid, as a share of a row's height. */
private const val COLUMN_GAP_SHARE = 0.06f

/** The gap between the two rows of a grid, as a share of a row's height. */
private const val ROW_GAP_SHARE = 0.3f

private const val MAX_AVATAR_LETTERS = 4

/**
 * The letters an avatar shows for [name], in lower case: the first letter of each word, at most 4;
 * for a name of one word, its first 4 letters. Words are separated by spaces and punctuation
 * ("Grown-up" is two words). A letter is a whole character as shown (BreakIterator): an emoji
 * made of several code points stays whole.
 */
fun avatarLetters(name: String): List<String> {
    val words = name.split(WORD_SEPARATOR).filter { it.isNotEmpty() }
    return when (words.size) {
        0 -> emptyList()
        1 -> characters(words.single()).take(MAX_AVATAR_LETTERS)
        else -> words.take(MAX_AVATAR_LETTERS).map { characters(it).first() }
    }.map { it.lowercase(Locale.ROOT) }
}

/** Spaces and punctuation, and the maths symbols (+, =, <, >): not part of a word. */
private val WORD_SEPARATOR = Regex("[\\s\\p{P}\\p{Sm}]+")

/** The characters of [word] as shown, one string each. */
private fun characters(word: String): List<String> {
    val breaks = BreakIterator.getCharacterInstance(Locale.ROOT)
    breaks.setText(word)
    val result = mutableListOf<String>()
    var start = breaks.first()
    var end = breaks.next()
    while (end != BreakIterator.DONE) {
        result += word.substring(start, end)
        start = end
        end = breaks.next()
    }
    return result
}

/** The names of the [CinemaProfileColors.palette] colours, in the same order. */
private val PROFILE_COLOUR_NAMES =
    listOf(
        R.string.profile_color_blue,
        R.string.profile_color_red,
        R.string.profile_color_green,
        R.string.profile_color_orange,
        R.string.profile_color_purple,
        R.string.profile_color_cyan,
        R.string.profile_color_pink,
        R.string.profile_color_brown,
    )

/** The name of the profile colour at [index], for the colour picker ("Blue", "Red", ...). */
@Composable
fun profileColourName(index: Int): String = stringResource(PROFILE_COLOUR_NAMES[Math.floorMod(index, PROFILE_COLOUR_NAMES.size)])

/**
 * How far from the centre a ray in direction ([ux], [uy]) (both 0 or more, a unit vector) leaves a
 * square of half side [half] whose corners are rounded with [cornerRadius].
 */
internal fun edgeDistance(
    ux: Float,
    uy: Float,
    half: Float,
    cornerRadius: Float,
): Float {
    val toSide = half / maxOf(ux, uy)
    val inner = half - cornerRadius
    // Leaving through a straight side, before the corner's arc begins.
    if (ux * toSide <= inner || uy * toSide <= inner) return toSide
    // Leaving through the corner's arc: |t·u - (inner, inner)| = cornerRadius, the far root.
    val b = ux * inner + uy * inner
    val c = 2 * inner * inner - cornerRadius * cornerRadius
    return b + sqrt(b * b - c)
}
