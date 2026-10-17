@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.Border
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.CardScale
import androidx.tv.material3.CardShape
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.cornerShape
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.TvFocusTokens

/**
 * The focus look of a content list row — a `Card` that is one focus stop and stands for a channel,
 * a title, a category or a search result (TV UI audit, X2: "light lift + white outline").
 *
 * - **At rest:** [CinemaSurface], white text.
 * - **Focused:** a lighter surface ([TvFocusTokens.focusedContainer]), a white outline
 *   ([TvFocusTokens.focusedRowOutline]) and the style's slight content scale; the text stays white.
 *   The shadow follows the look and feel ([TvFocusTokens.focusedGlow]).
 * - **Current** (the channel playing, the category being browsed): the accent bar from
 *   `Modifier.currentIndicator`, the only accent on the row. Its title is [TvFocusTokens.currentText]
 *   at rest and white while focused — see [titleColor].
 *
 * ```
 * Card(
 *     onClick = onClick,
 *     colors = TvListRowDefaults.colors(),
 *     border = TvListRowDefaults.border(),
 *     scale = TvListRowDefaults.scale(),
 *     glow = TvListRowDefaults.glow(),
 *     shape = TvListRowDefaults.shape(),
 *     modifier = Modifier.onFocusChanged { isFocused = it.isFocused },
 * ) {
 *     Row(Modifier.currentIndicator(isCurrent)) {
 *         Text(title, color = TvListRowDefaults.titleColor(isCurrent, isFocused))
 *     }
 * }
 * ```
 *
 * The values are `@Composable` (they read the active theme and look and feel), so a list hoists
 * them out of its item body — once per list, not once per row (see `TvCardLooks` in cards/TvCard.kt).
 */
object TvListRowDefaults {
    @ReadOnlyComposable
    @Composable
    fun colors(): CardColors =
        CardDefaults.colors(
            containerColor = CinemaTheme.colors.surface,
            contentColor = CinemaTheme.colors.textPrimary,
            focusedContainerColor = TvFocusTokens.focusedContainer,
            focusedContentColor = CinemaTheme.colors.textPrimary,
            pressedContainerColor = TvFocusTokens.focusedContainer,
            pressedContentColor = CinemaTheme.colors.textPrimary,
        )

    @ReadOnlyComposable
    @Composable
    fun border(): CardBorder {
        val focused =
            Border(
                border = BorderStroke(TvFocusTokens.focusBorderWidth, TvFocusTokens.focusedRowOutline),
                shape = cornerShape(CornerRadius.medium),
            )
        return CardDefaults.border(border = Border.None, focusedBorder = focused, pressedBorder = focused)
    }

    @Composable
    fun scale(): CardScale =
        CardDefaults.scale(
            scale = TvFocusTokens.defaultScale,
            focusedScale = TvFocusTokens.focusedScaleContent,
            pressedScale = TvFocusTokens.pressedScaleSubtle,
        )

    @Composable
    fun glow(): CardGlow = CardDefaults.glow(focusedGlow = TvFocusTokens.focusedGlow)

    @Composable
    fun shape(): CardShape = CardDefaults.shape(shape = cornerShape(CornerRadius.medium))

    /** A row's title: [TvFocusTokens.currentText] on a current row at rest, white otherwise — never the accent on focus. */
    @ReadOnlyComposable
    @Composable
    fun titleColor(
        isCurrent: Boolean,
        isFocused: Boolean,
    ): Color = if (isCurrent && !isFocused) TvFocusTokens.currentText else CinemaTheme.colors.textPrimary

    /** [titleColor] as a text tone, for [CinemaText] titles. */
    fun titleTone(
        isCurrent: Boolean,
        isFocused: Boolean,
    ): TextTone = if (isCurrent && !isFocused) TextTone.AccentSoft else TextTone.Primary
}
