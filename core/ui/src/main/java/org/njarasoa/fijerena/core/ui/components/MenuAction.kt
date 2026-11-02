package org.njarasoa.fijerena.core.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One action of an action menu (the phone's `CinemaActionMenu`, the TV's `TvActionMenu`): its icon,
 * its label and what it does. A [removal] (Remove from Favourites, Remove from Recent, Delete) goes
 * last, just above Cancel, in the danger colour, and never takes the first focus (CONTRIBUTING
 * rule 7). The menu closes after [onClick].
 */
@Immutable
data class MenuAction(
    val icon: ImageVector,
    val label: String,
    val removal: Boolean = false,
    val onClick: () -> Unit,
)

/** The actions in menu order: the safe actions as given, then the removals as given. */
fun List<MenuAction>.inMenuOrder(): List<MenuAction> = filterNot { it.removal } + filter { it.removal }
