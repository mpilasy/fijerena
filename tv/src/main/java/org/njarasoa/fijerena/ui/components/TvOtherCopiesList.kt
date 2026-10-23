package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * On a film's or show's error page ([TvErrorState]'s extra): the other copies of the title (the
 * same TMDB id, on this source or another), each opening that copy — its catalogue name, then its
 * source's when there are several. Nothing when there are none.
 */
@Composable
fun TvOtherCopiesList(
    copies: List<MediaItem>,
    sourceNames: Map<Long, String>,
    onSelect: (MediaItem) -> Unit,
) {
    if (copies.isEmpty()) return
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CinemaText(stringResource(R.string.details_open_other_copy), role = TextRole.Heading)
        copies.forEach { copy ->
            val label = sourceNames[copy.providerId]?.let { "${copy.name} · $it" } ?: copy.name
            CinemaSecondaryButton(onClick = { onSelect(copy) }, text = label)
        }
    }
}
