package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.SourceBadge
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton

/**
 * On a film's or show's error page: the other copies of the title (the same TMDB id, on this
 * source or another), each opening that copy — its catalogue name, and its source's badge.
 * Nothing when there are none.
 */
@Composable
fun OtherCopiesList(
    copies: List<MediaItem>,
    sourceNames: Map<Long, String>,
    onSelect: (MediaItem) -> Unit,
) {
    if (copies.isEmpty()) return
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
        CinemaText(stringResource(R.string.details_open_other_copy), role = TextRole.Heading)
        copies.forEach { copy ->
            CinemaButton(kind = ButtonKind.Other, onClick = { onSelect(copy) }) {
                sourceNames[copy.providerId]?.let { name ->
                    SourceBadge(name, providerId = copy.providerId)
                    Spacer(Modifier.width(CinemaSpacing.xs))
                }
                CinemaText(copy.name, role = TextRole.Label, keepCase = true, tone = TextTone.Inherit)
            }
        }
    }
}
