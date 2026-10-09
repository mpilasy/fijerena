@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components.rail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

/**
 * The TV navigation rail (docs/plans/20261008_tv-nav-rail-plan.md). Phase 0 placeholder with the
 * final signature: a plain column of icon buttons. Phase 1 (lane R) builds the real one — faint
 * icons at rest inside the 56 dp margin, sliding out to icons + labels over a scrim on focus.
 *
 * @param items what the rail offers, top to bottom, sections already filtered to the source's.
 * @param profileInitial the avatar's letter for [RailItem.PROFILE].
 * @param onSelect called with the item picked; the nav host navigates.
 */
@Composable
fun TvNavRail(
    state: TvNavRailState,
    items: List<RailItem>,
    profileInitial: String,
    onSelect: (RailItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.visible) return
    Column(modifier = modifier.padding(start = 8.dp)) {
        items.forEach { item ->
            IconButton(
                onClick = { onSelect(item) },
                modifier = if (item == (state.current ?: items.first())) Modifier.focusRequester(state.entry) else Modifier,
            ) {
                Icon(
                    imageVector =
                        when (item) {
                            RailItem.PROFILE -> CinemaIcons.Home

                            // the avatar letter in Phase 1
                            RailItem.HOME -> CinemaIcons.Home

                            RailItem.LIVE_TV -> CinemaIcons.LiveTv

                            RailItem.MOVIES -> CinemaIcons.Movie

                            RailItem.TV_SHOWS -> CinemaIcons.Tv

                            RailItem.SEARCH -> CinemaIcons.Search

                            RailItem.SETTINGS -> CinemaIcons.Settings
                        },
                    contentDescription = null,
                )
            }
        }
    }
}
