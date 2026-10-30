package org.njarasoa.fijerena.feature.person

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.network.tmdb.ShowAppearance
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.person.appearanceLabel
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing

/**
 * A person's appearances in one show, as a sheet over the person page
 * (docs/plans/20261030_episode-cast-plan.md, EC-D2): one row per episode ("S9E17 · Authority") or
 * per season ("Season 3"), a row the source lacks marked "Not in your source". A tap on a row calls
 * [onSelect]; the sheet stays, for Back from the show. Dismissing it calls [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowAppearancesSheet(
    title: String,
    subtitle: String?,
    lines: List<ShowAppearance>,
    onSelect: (ShowAppearance) -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CinemaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
        ) {
            CinemaText(text = title, role = TextRole.Title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let { CinemaText(text = it, role = TextRole.BodySmall, tone = TextTone.Secondary) }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = CinemaSpacing.sm, bottom = CinemaSpacing.md)) {
            items(lines, key = { "${it.season}:${it.episode ?: ""}" }) { line ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(line) }
                            .padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CinemaText(
                        text = appearanceLabel(resources, line),
                        role = TextRole.Body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!line.inSource) {
                        CinemaText(
                            text = stringResource(R.string.person_missing_mark),
                            role = TextRole.BodySmall,
                            tone = TextTone.Secondary,
                        )
                    }
                }
            }
        }
    }
}
