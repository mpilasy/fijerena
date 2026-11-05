@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.CardScale
import androidx.tv.material3.CardShape
import androidx.tv.material3.Glow
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.textStyleOf
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.cornerShape
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * A TV card's whole look: colours, outline, focus scale, glow and shape. `CardDefaults` calls are
 * `@Composable`, so a list builds its look once ([TvCardLooks]) and hands it to every [TvCard].
 */
@Immutable
data class TvCardLook(
    val colors: CardColors,
    val border: CardBorder,
    val scale: CardScale,
    val glow: CardGlow,
    val shape: CardShape,
)

/** The TV card looks, from [CinemaTheme] and [TvFocusTokens]. */
object TvCardLooks {
    /** A content list row ([TvListRowDefaults]: light lift, white outline, text stays white). */
    @Composable
    fun listRow(): TvCardLook =
        TvCardLook(
            colors = TvListRowDefaults.colors(),
            border = TvListRowDefaults.border(),
            scale = TvListRowDefaults.scale(),
            glow = TvListRowDefaults.glow(),
            shape = TvListRowDefaults.shape(),
        )

    /**
     * A section title that folds its group (Search): no fill at rest, a content row's focus look
     * (light lift, white outline) when focused.
     */
    @Composable
    fun foldingHeader(): TvCardLook =
        TvCardLook(
            colors =
                CardDefaults.colors(
                    containerColor = CinemaTheme.colors.clear,
                    contentColor = CinemaTheme.colors.textPrimary,
                    focusedContainerColor = TvFocusTokens.focusedContainer,
                    focusedContentColor = CinemaTheme.colors.textPrimary,
                ),
            border = TvListRowDefaults.border(),
            scale = CardDefaults.scale(),
            glow = CardDefaults.glow(),
            shape = TvListRowDefaults.shape(),
        )

    /** A card on one of Home's shelves (Jump back in, Favourites, Live): raised at rest; focus lifts it with a glow. */
    @Composable
    fun shelf(): TvCardLook {
        val shape = CinemaTheme.shapes.card
        return TvCardLook(
            colors =
                CardDefaults.colors(
                    containerColor = CinemaTheme.colors.surface,
                    contentColor = CinemaTheme.colors.textPrimary,
                    focusedContainerColor = CinemaTheme.colors.surface,
                    focusedContentColor = CinemaTheme.colors.textPrimary,
                ),
            border =
                CardDefaults.border(
                    border = Border(BorderStroke(TvFocusTokens.borderThin, CinemaTheme.colors.glassBorder)),
                    focusedBorder = TvFocusTokens.cardFocusBorder(shape),
                ),
            scale =
                CardDefaults.scale(
                    scale = TvFocusTokens.defaultScale,
                    focusedScale = TvFocusTokens.focusedScale,
                    pressedScale = TvFocusTokens.pressedScaleSubtle,
                ),
            glow = CardDefaults.glow(glow = TvFocusTokens.restingGlow, focusedGlow = TvFocusTokens.cardFocusGlow),
            shape = CardDefaults.shape(shape = shape),
        )
    }

    /**
     * A small tile in a row of choices (Sports' followed teams): raised at rest; focus lifts it with
     * the white ring of every focused row, as the guide cells, not the shelves' accent glow.
     */
    @Composable
    fun tile(): TvCardLook {
        val shape = CinemaTheme.shapes.card
        val ring = Border(BorderStroke(TvFocusTokens.focusBorderWidth, TvFocusTokens.focusedRowOutline), shape = shape)
        return TvCardLook(
            colors =
                CardDefaults.colors(
                    containerColor = CinemaTheme.colors.surface,
                    contentColor = CinemaTheme.colors.textPrimary,
                    focusedContainerColor = TvFocusTokens.focusedContainer,
                    focusedContentColor = CinemaTheme.colors.textPrimary,
                ),
            border =
                CardDefaults.border(
                    border = Border(BorderStroke(TvFocusTokens.borderThin, CinemaTheme.colors.glassBorder)),
                    focusedBorder = ring,
                    pressedBorder = ring,
                ),
            scale =
                CardDefaults.scale(
                    scale = TvFocusTokens.defaultScale,
                    focusedScale = TvFocusTokens.focusedScale,
                    pressedScale = TvFocusTokens.pressedScaleSubtle,
                ),
            glow = CardDefaults.glow(),
            shape = CardDefaults.shape(shape = shape),
        )
    }

    /** A recent-search chip: raised at rest, a faint accent fill on focus. */
    @Composable
    fun chip(): TvCardLook =
        TvCardLook(
            colors =
                CardDefaults.colors(
                    containerColor = CinemaTheme.colors.surface,
                    focusedContainerColor = CinemaTheme.colors.accent.copy(alpha = CinemaAlpha.glassBorder),
                ),
            border = CardDefaults.border(),
            scale =
                CardDefaults.scale(
                    scale = TvFocusTokens.defaultScale,
                    focusedScale = TvFocusTokens.focusedScaleContent,
                ),
            glow = CardDefaults.glow(),
            shape = CardDefaults.shape(shape = CinemaTheme.shapes.card),
        )

    /**
     * A TV Guide cell (channel or programme): focus lifts it, lighter, with the white ring of every
     * focused row (one TV focus look), not an accent glow, which read as faint on a narrow cell.
     */
    @Composable
    fun guideCell(): TvCardLook {
        val shape = CinemaTheme.shapes.card
        val ring = Border(BorderStroke(TvFocusTokens.focusBorderWidth, TvFocusTokens.focusedRowOutline), shape = shape)
        return TvCardLook(
            colors =
                CardDefaults.colors(
                    containerColor = TvFocusTokens.restingContainer,
                    contentColor = CinemaTheme.colors.textPrimary,
                    focusedContainerColor = TvFocusTokens.focusedContainer,
                    focusedContentColor = CinemaTheme.colors.textPrimary,
                ),
            border = CardDefaults.border(focusedBorder = ring, pressedBorder = ring),
            scale =
                CardDefaults.scale(
                    scale = TvFocusTokens.defaultScale,
                    focusedScale = TvFocusTokens.focusedScaleContent,
                    pressedScale = TvFocusTokens.pressedScaleSubtle,
                ),
            glow = CardDefaults.glow(),
            shape = CardDefaults.shape(shape = shape),
        )
    }

    /** An episode row: raised at rest, an accent tint and shadow on focus. */
    @Composable
    fun episode(): TvCardLook =
        // A content row's focus look, as every list row: a lighter lift and an outline, never an accent
        // fill — the accent fill hid the current episode's accent title, on a light theme entirely.
        TvCardLook(
            colors =
                CardDefaults.colors(
                    containerColor = CinemaTheme.colors.surface,
                    focusedContainerColor = TvFocusTokens.focusedContainer,
                ),
            border = TvListRowDefaults.border(),
            scale =
                CardDefaults.scale(
                    scale = TvFocusTokens.defaultScale,
                    focusedScale = TvFocusTokens.focusedScaleContent,
                    pressedScale = TvFocusTokens.pressedScaleSubtle,
                ),
            glow = CardDefaults.glow(focusedGlow = accentShadow()),
            shape = CardDefaults.shape(shape = CinemaTheme.shapes.card),
        )

    /**
     * A block of text that takes focus only so the list scrolls to it (Device Info's sections,
     * Diagnostics' entries, Licences): the resting container, a lighter one and a white outline on
     * focus, no scale, the large corner.
     */
    @Composable
    fun infoBlock(): TvCardLook {
        val focused = Border(BorderStroke(TvFocusTokens.focusBorderWidth, TvFocusTokens.focusedRowOutline))
        return TvCardLook(
            colors =
                CardDefaults.colors(
                    containerColor = TvFocusTokens.restingContainer,
                    contentColor = CinemaTheme.colors.textPrimary,
                    focusedContainerColor = TvFocusTokens.focusedContainer,
                    focusedContentColor = CinemaTheme.colors.textPrimary,
                    pressedContainerColor = TvFocusTokens.focusedContainer,
                    pressedContentColor = CinemaTheme.colors.textPrimary,
                ),
            border = CardDefaults.border(border = Border.None, focusedBorder = focused, pressedBorder = focused),
            scale = CardDefaults.scale(scale = TvFocusTokens.defaultScale, focusedScale = TvFocusTokens.defaultScale),
            glow = CardDefaults.glow(),
            shape = CardDefaults.shape(shape = cornerShape(CornerRadius.large)),
        )
    }

    /**
     * A profile's avatar on the picker: the avatar shape (as round as the style's buttons), the style's focus scale, and a
     * white ring hugging its edge on focus (F-PP-1, TV UI audit #1): drawn just outside the edge, with
     * no gap, so it reads as the avatar's own outline; white, the rows' focus colour, never blends
     * into a coloured avatar the way the accent did.
     */
    @Composable
    fun avatar(): TvCardLook {
        val shape = CinemaTheme.shapes.avatar
        val ring =
            Border(
                border = BorderStroke(TvFocusTokens.avatarFocusBorderWidth, TvFocusTokens.focusedRowOutline),
                inset = TvFocusTokens.avatarFocusBorderWidth / 2,
                shape = shape,
            )
        return TvCardLook(
            colors =
                CardDefaults.colors(
                    containerColor = TvFocusTokens.restingContainer,
                    focusedContainerColor = TvFocusTokens.focusedContainer,
                    pressedContainerColor = TvFocusTokens.focusedContainer,
                ),
            border = CardDefaults.border(border = Border.None, focusedBorder = ring, pressedBorder = ring),
            scale =
                CardDefaults.scale(
                    scale = TvFocusTokens.defaultScale,
                    focusedScale = TvFocusTokens.focusedScale,
                    pressedScale = TvFocusTokens.pressedScale,
                ),
            glow = CardDefaults.glow(),
            shape = CardDefaults.shape(shape = shape),
        )
    }

    /**
     * A colour swatch (a profile's colour): round, filled with [color] in every state, the style's
     * focus scale; the text colour outlines the chosen one, the soft accent the focused one.
     * [uiScale] scales the outline as the swatch.
     */
    @Composable
    fun swatch(
        color: Color,
        selected: Boolean,
        uiScale: Float,
    ): TvCardLook {
        val shape = CinemaTheme.shapes.identity
        val width = TvFocusTokens.focusBorderWidth.scaled(uiScale)
        val focused = Border(BorderStroke(width, CinemaTheme.colors.accentSoft))
        return TvCardLook(
            colors = CardDefaults.colors(containerColor = color, focusedContainerColor = color, pressedContainerColor = color),
            border =
                CardDefaults.border(
                    border = if (selected) Border(BorderStroke(width, CinemaTheme.colors.textPrimary)) else Border.None,
                    focusedBorder = focused,
                    pressedBorder = focused,
                ),
            scale =
                CardDefaults.scale(
                    scale = TvFocusTokens.defaultScale,
                    focusedScale = TvFocusTokens.focusedScale,
                    pressedScale = TvFocusTokens.pressedScale,
                ),
            glow = CardDefaults.glow(),
            shape = CardDefaults.shape(shape = shape),
        )
    }

    @Composable
    private fun accentShadow(): Glow =
        Glow(
            elevationColor = CinemaTheme.colors.accent.copy(alpha = CinemaAlpha.cardElevationShadow),
            elevation = TvFocusTokens.focusShadowElevation,
        )
}

/** A focusable TV card in [look]; [onLongClick] is long-press OK. */
@Composable
fun TvCard(
    onClick: () -> Unit,
    look: TvCardLook,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // look-ok: the component that wraps Material's Card
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        colors = look.colors,
        border = look.border,
        scale = look.scale,
        glow = look.glow,
        shape = look.shape,
        content = content,
    )
}

/**
 * A content row's title: [TvListRowDefaults.titleColor] (the current colour on a current row at
 * rest, the text colour otherwise), in [role].
 */
@Composable
fun TvRowTitle(
    text: String,
    isCurrent: Boolean,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    role: TextRole = TextRole.Heading,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    androidx.tv.material3.Text(
        text = text,
        modifier = modifier,
        style = textStyleOf(role),
        color = TvListRowDefaults.titleColor(isCurrent = isCurrent, isFocused = isFocused),
        maxLines = maxLines,
        overflow = overflow,
    )
}
