package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.sports.dayHeadingBand
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * A day's heading over a list of games or airings ("Today", "Saturday, 1 November 2026"): the one
 * look for day headings on the phone (Sports, a team or league page, Search's Teams, Search the
 * Guide). A band behind it only while [pinned] (a sticky header at the top of its list).
 */
@Composable
fun MobileDayHeading(
    text: String,
    pinned: Boolean,
    modifier: Modifier = Modifier,
) {
    CinemaText(
        text = text,
        // One step under the section headings (Title) and the game titles (Heading), so the
        // order of section, day and game reads; the same on both apps.
        role = TextRole.Body,
        tone = TextTone.Secondary,
        emphasis = true,
        modifier = modifier.dayHeadingBand(pinned).padding(vertical = Spacing.xs),
    )
}
