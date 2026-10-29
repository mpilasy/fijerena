package org.njarasoa.fijerena.feature.category.components

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.ui.components.TvSectionTabs
import org.njarasoa.fijerena.ui.components.input.PaneFocusState

/**
 * The browse lists as tabs at the top of the items pane (the tv browse tabs plan): the category
 * last picked in the categories pane, then Recent, Favourites and the other virtual lists, as
 * icons with their label on focus. Focus selects a tab, as in every TV tab row.
 *
 * Left on the first tab enters [categoriesPane], Down enters [itemsPane]; neither is left to
 * Compose's geometric search. Right on the last tab stays; Up reaches the screen header.
 */
@Composable
internal fun BrowseTabs(
    tabs: List<MediaCategory>,
    selectedKey: String?,
    onTabSelected: (MediaCategory) -> Unit,
    categoriesPane: PaneFocusState,
    itemsPane: PaneFocusState,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = tabs.indexOfFirst { it.key == selectedKey }
    val entry = remember { FocusRequester() }
    TvSectionTabs(
        tabs = tabs.map { it.name },
        selectedIndex = selectedIndex,
        // Entering the row lands on the selected tab, which is already open: nothing to load.
        onTabSelected = { index -> tabs.getOrNull(index)?.takeIf { it.key != selectedKey }?.let(onTabSelected) },
        icons = tabs.map { if (it.isVirtual) virtualCategoryIcon(it.id) else null },
        entryFocusRequester = entry,
        firstTabModifier = Modifier.onPreviewKeyEvent { it.isKeyDown(Key.DirectionLeft) && categoriesPane.enter() },
        modifier =
            modifier
                // An empty list has no row to enter: Compose then finds its button.
                .onPreviewKeyEvent { it.isKeyDown(Key.DirectionDown) && itemsPane.enter() }
                // Entering the row lands on the selected tab, not the one nearest by geometry, as in
                // the Live TV panel: focusProperties directly before focusGroup, as Modifier.tvPane does.
                .focusProperties { onEnter = { if (selectedIndex >= 0) entry.requestFocus() } }
                .focusGroup(),
    )
}

private fun KeyEvent.isKeyDown(key: Key): Boolean = type == KeyEventType.KeyDown && this.key == key

/** The icon that marks a virtual category as a mode of browsing (Recent, Favourites…). */
@Composable
private fun virtualCategoryIcon(categoryId: String): ImageVector =
    when (categoryId) {
        CategoryViewModel.RECENT_CATEGORY_ID -> CinemaIcons.Replay
        CategoryViewModel.FAVORITES_CATEGORY_ID -> CinemaIcons.Star
        CategoryViewModel.FAVORITE_CATEGORIES_ID -> CinemaIcons.StarBorder
        CategoryViewModel.WANTED_CATEGORY_ID -> CinemaIcons.Bookmark
        CategoryViewModel.CHARTS_CATEGORY_ID -> CinemaIcons.TrendingUp
        else -> CinemaIcons.Folder
    }
