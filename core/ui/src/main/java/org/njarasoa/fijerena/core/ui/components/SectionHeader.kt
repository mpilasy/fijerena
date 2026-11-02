package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing

/**
 * The heading over a group of items in a list (Search's sections: Live TV, Movies, Not in Your
 * Sources), the same on TV and on the phone: [title] in [TextRole.Heading] with emphasis, an
 * optional [count] ("12 results") in the secondary tone at the end, and an optional [trailing]
 * part after it (the fold arrow of a group that folds). The caller places it ([modifier]); a
 * header that folds its group puts it in its own tappable or focusable container.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CinemaText(
            text = title,
            role = TextRole.Heading,
            tone = TextTone.Primary,
            emphasis = true,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count != null) {
            CinemaText(
                text = count,
                role = TextRole.Body,
                tone = TextTone.Secondary,
                modifier = Modifier.padding(start = CinemaSpacing.sm),
            )
        }
        if (trailing != null) {
            Row(modifier = Modifier.padding(start = CinemaSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                trailing()
            }
        }
    }
}

/**
 * The name of a small group of rows inside a panel, a dialog or a filter (the filter's Source and
 * Type groups, the player statistics' groups), the same on TV and on the phone: [TextRole.Label]
 * with emphasis, in the primary tone. Not tappable or focusable: it only names the rows under it.
 */
@Composable
fun GroupHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    CinemaText(text = text, role = TextRole.Label, tone = TextTone.Primary, emphasis = true, modifier = modifier)
}
