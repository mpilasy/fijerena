package org.njarasoa.fijerena.core.ui.sports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/**
 * Keeps [viewModel]'s games up to date while this screen is resumed ([SportsGamesViewModel.keepFresh]):
 * read again when they are old or one is on, the games that are over hidden. Used by the team and
 * league pages and the Sports section, on TV and phone.
 */
@Composable
fun KeepGamesFresh(viewModel: SportsGamesViewModel) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, owner) {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.keepFresh() }
    }
}
