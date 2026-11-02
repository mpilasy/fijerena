package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.ui.components.CatchupBadge
import org.njarasoa.fijerena.core.ui.components.CinemaIcon
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.contentOn
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * One row of an action menu or of a list over video (the player's channel list), in the chip
 * corner: an optional [icon], the [label] (Body; Heading on one line when [labelIsName], a channel)
 * and an optional [supportingText] under it. At rest it has the raised fill; [selected], the strong
 * accent with text that reads on it (Material's own pick was invisible on light themes). A
 * [removal]'s icon and label are in the danger colour. The row gives its own padding and text
 * roles: callers give data, never content.
 *
 * A single choice in a dialog (a picker, a track, a source) is a [CinemaRadioRow], not this row.
 *
 * [overMedia]: a list over video: both fills see-through, the selected one in the accent, the text
 * in the text colour. [catchupDays]: a channel that keeps an archive, its [CatchupBadge] before the
 * label.
 */
@Composable
fun CinemaChoiceRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supportingText: String? = null,
    selected: Boolean = false,
    removal: Boolean = false,
    labelIsName: Boolean = false,
    overMedia: Boolean = false,
    catchupDays: Int? = null,
) {
    val colors = CinemaTheme.colors
    val fill =
        when {
            overMedia && selected -> colors.accent.copy(alpha = CinemaAlpha.glass)
            overMedia -> colors.surfaceRaised.copy(alpha = CinemaAlpha.glass)
            selected -> colors.accentStrong
            else -> colors.surfaceRaised
        }
    val tone = if (removal) TextTone.Danger else TextTone.Inherit
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = fill,
        contentColor = if (selected && !overMedia) contentOn(colors.accentStrong) else colors.textPrimary,
        shape = CinemaTheme.shapes.chip,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) CinemaIcon(icon, contentDescription = null, tone = tone)
            catchupDays?.let { CatchupBadge(it, iconSize = MobileDimensions.iconSmall) }
            Column(modifier = Modifier.weight(1f)) {
                if (labelIsName) {
                    CinemaText(label, role = TextRole.Heading, tone = tone, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    CinemaText(label, tone = tone)
                }
                if (supportingText != null) {
                    CinemaText(supportingText, role = TextRole.BodySmall, tone = TextTone.Secondary)
                }
            }
        }
    }
}
