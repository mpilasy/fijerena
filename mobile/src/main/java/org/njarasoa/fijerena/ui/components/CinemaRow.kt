package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * One tappable row of a phone list, the full width: tap does [onClick], a long press [onLongClick]
 * (with [onLongClickLabel] for TalkBack). Without [onClick] it is a plain row that is not tappable.
 * It is at least a touch target high, with the list row's padding ([contentPadding], the side and
 * the top and bottom gaps; a row in a panel that already pads its sides gives
 * [CinemaRowDefaults.inPanel]).
 *
 * Feature code makes a tappable row with this, [org.njarasoa.fijerena.feature.settings.components.SettingsListRow]
 * (a preference) or [CinemaChoiceRow] (one option of a list of choices), never with a raw
 * `clickable` (the design-system gate counts it).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CinemaRow(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    role: Role? = null,
    contentPadding: PaddingValues = CinemaRowDefaults.contentPadding(),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(Spacing.md),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = MobileDimensions.minTouchTarget)
                .then(
                    if (onClick != null) {
                        Modifier.combinedClickable(
                            enabled = enabled,
                            onClickLabel = onClickLabel,
                            role = role,
                            onLongClickLabel = onLongClickLabel,
                            onLongClick = onLongClick,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ).padding(contentPadding),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

/** The paddings a [CinemaRow] takes. */
object CinemaRowDefaults {
    /** A row of a list on the screen's ground: the screen's side margin, a small gap above and below. */
    @Composable
    fun contentPadding(): PaddingValues = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)

    /** A row inside a panel that pads its own sides: only the gap above and below. */
    @Composable
    fun inPanel(): PaddingValues = PaddingValues(vertical = Spacing.xs)
}
