@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
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
import org.njarasoa.fijerena.ui.theme.TvFocusTokens

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

    /** A TV Guide cell (channel or programme): focus lifts it, lighter, with a glow. */
    @Composable
    fun guideCell(): TvCardLook {
        val shape = CinemaTheme.shapes.card
        return TvCardLook(
            colors =
                CardDefaults.colors(
                    containerColor = TvFocusTokens.restingContainer,
                    contentColor = CinemaTheme.colors.textPrimary,
                    focusedContainerColor = TvFocusTokens.focusedContainer,
                    focusedContentColor = CinemaTheme.colors.textPrimary,
                ),
            border =
                CardDefaults.border(focusedBorder = TvFocusTokens.cardFocusBorder(shape)),
            scale =
                CardDefaults.scale(
                    scale = TvFocusTokens.defaultScale,
                    focusedScale = TvFocusTokens.focusedScaleContent,
                    pressedScale = TvFocusTokens.pressedScaleSubtle,
                ),
            glow = CardDefaults.glow(focusedGlow = TvFocusTokens.cardFocusGlow),
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
