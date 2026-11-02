package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.sports.dayHeadingBand
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * A day's heading over a list of games or airings ("Today", "Saturday, 1 November 2026"): the one
 * look for day headings on TV (Sports, a team or league page, Search's Teams, Search the Guide).
 * It takes no focus. A band behind it only while [pinned] (a sticky header at the top of its list).
 */
@Composable
fun TvDayHeading(
    text: String,
    modifier: Modifier = Modifier,
    pinned: Boolean = false,
) {
    CinemaText(
        text = text,
        role = TextRole.Title,
        modifier = modifier.dayHeadingBand(pinned).padding(horizontal = Spacing.md, vertical = Spacing.xs),
    )
}
