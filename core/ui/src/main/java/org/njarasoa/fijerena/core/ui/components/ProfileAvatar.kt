package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaProfileColors
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme

/**
 * A profile's avatar: its colour as a circle, with the first letter of its name. The letter has the
 * size of [initialRole], times [initialScale] for an avatar that is much larger than the text.
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
    // The initial has its role's size, times the scale for a large avatar.
    val initialSize = textStyleOf(initialRole).fontSize.let { if (it.isSpecified) it * initialScale else it }
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CinemaTheme.shapes.identity)
                .background(CinemaProfileColors.forIndex(colorIndex)),
        contentAlignment = Alignment.Center,
    ) {
        // look-ok: the initial is sized to the avatar, in the Look's title font (a name's font)
        Text(
            text = initialOf(name),
            style =
                CinemaTheme.type.heading.copy(
                    // look-ok: the initial's size from its role, above
                    fontSize = initialSize,
                    fontWeight = emphasisWeight,
                    lineHeight = TextUnit.Unspecified,
                ),
            color = CinemaProfileColors.onAvatar,
        )
    }
}

/**
 * First character of [name], uppercased. By code point, not `Char`: an emoji is two UTF-16 units,
 * and taking only one of them renders a broken glyph.
 */
fun initialOf(name: String): String {
    val trimmed = name.trim()
    return if (trimmed.isEmpty()) "" else String(Character.toChars(trimmed.codePointAt(0))).uppercase()
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
