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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import org.njarasoa.fijerena.core.ui.theme.CinemaProfileColors
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme

/** A profile's avatar: its colour as a circle, with the first letter of its name. */
@Composable
fun ProfileAvatar(
    name: String,
    colorIndex: Int,
    size: Dp,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
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
                    fontSize = fontSize,
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
