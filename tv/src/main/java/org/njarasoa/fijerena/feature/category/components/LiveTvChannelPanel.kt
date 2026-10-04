package org.njarasoa.fijerena.feature.category.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Icon
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.ImmutableMediaList
import org.njarasoa.fijerena.core.ui.components.ImmutableNowPlaying
import org.njarasoa.fijerena.core.ui.components.ImmutableStringSet
import org.njarasoa.fijerena.core.ui.components.ImmutableWatchProgress
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.ui.components.TvSectionTabs
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.input.rememberPaneFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * The Live TV channel panel (LT2, LT3): a tab row — the picked category when there is one ·
 * Recent · Favourites, with Refresh at its end — over the rows of the selected [ChannelContext].
 * One component in two places: docked beside the preview, and over the video in full screen
 * ([overlay], hosted by `PlayerScreen`, which owns opening, Back and keeping focus inside).
 *
 * Keys: Up from the first row lands on the selected tab, whichever node above it Compose's
 * geometric search picked; Left/Right on the tabs switch the list (focus follows selection, as
 * [TvSectionTabs] does everywhere), and Left on the first tab of the docked panel is Back
 * ([onLeftFromFirstTab]); Left/Right on a row switch to the previous / next tab too, focus staying
 * in the rows (on the current channel when the new list has it) — Recent ↔ Favourites in one press
 * from anywhere in the list, without climbing to the tabs; Down from the tabs enters the rows on the current channel when the list
 * has it, else the first row; OK on a row promotes it to full screen, or tunes it in full
 * screen. The rows take focus on the current channel when they appear, so the overlay opens on
 * it. An empty list is a line of text, not a Refresh button, so focus stays on the tabs (L-10).
 * Refresh is at the end of the tab row, no longer a stop between the tabs and the first row
 * (L-13).
 */
@Composable
internal fun LiveTvChannelPanel(
    tabs: List<ChannelContext>,
    context: ChannelContext,
    onContextSelected: (ChannelContext) -> Unit,
    streams: ImmutableMediaList?,
    streamsLoading: Boolean,
    lastPlayedItemId: String?,
    nowPlaying: ImmutableNowPlaying,
    contentType: String,
    categoryViewModel: CategoryViewModel,
    isDevMode: Boolean,
    favoriteIds: ImmutableStringSet,
    watchProgress: ImmutableWatchProgress,
    watchedIds: ImmutableStringSet,
    onCategorySelected: (String) -> Unit,
    onStreamSelected: (streamId: String, streamName: String, categoryId: String, target: BrowseTarget) -> Unit,
    onStreamPromote: (MediaItem) -> Unit,
    onStreamFocused: (MediaItem) -> Unit,
    onRefresh: () -> Unit,
    /** Full screen: the panel has just opened over the video and must hold focus at once. */
    overlay: Boolean = false,
    /**
     * Docked in the preview: Left on the first tab leaves the preview as Back does (UX overhaul
     * plan Part II Live TV target item 8). Null (full screen, where Back closes the panel) keeps
     * focus on the tab.
     */
    onLeftFromFirstTab: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scale = LocalUiScale.current
    val tabsEntry = remember { FocusRequester() }
    val refreshFocus = remember { FocusRequester() }
    val rowsPane = rememberPaneFocus()
    val selectedIndex = tabs.indexOfFirst { it.id == context.id }.coerceAtLeast(0)
    val labels = tabs.map { it.label() }

    // An empty tab has nothing focusable below the tabs, so focus goes (or stays) there — also
    // when the last favourite is removed from its row and the row disappears under focus. Over
    // the video the tabs also hold focus while the list loads, since the player gave it up when
    // the panel opened; the rows take it on the current channel once they are there.
    val isEmpty = !streamsLoading && streams != null && streams.isEmpty()
    val focusTabs = isEmpty || (overlay && streamsLoading)
    LaunchedEffect(context, focusTabs) {
        if (focusTabs) tabsEntry.requestFocusWithRetry()
    }

    // Left/Right on a row: the previous / next tab. Its rows take focus once the new list is
    // there — not the list still on screen when the tab changed, whose rows are about to go and
    // would leave focus on the list's container.
    var rowsFocusPendingFrom by remember { mutableStateOf<ImmutableMediaList?>(null) }
    var rowsFocusPending by remember { mutableStateOf(false) }
    LaunchedEffect(streams, streamsLoading, rowsFocusPending) {
        if (!rowsFocusPending || streamsLoading || streams == null || streams === rowsFocusPendingFrom) return@LaunchedEffect
        rowsFocusPending = false
        rowsFocusPendingFrom = null
        if (streams.isNotEmpty()) rowsPane.focusEntryInNewList()
    }
    val switchTabFromRow: (Key) -> Boolean = { key ->
        val target = if (key == Key.DirectionRight) selectedIndex + 1 else selectedIndex - 1
        val next = tabs.getOrNull(target)
        when {
            next != null -> {
                rowsFocusPendingFrom = streams
                rowsFocusPending = true
                onContextSelected(next)
            }

            key == Key.DirectionLeft -> {
                onLeftFromFirstTab?.invoke()
            }
        }
        true
    }

    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.sm.scaled(scale))
                    // Entering this row from the list lands on the selected tab, not on
                    // whichever tab or the Refresh icon geometry preferred. focusProperties
                    // directly before focusGroup, as Modifier.tvPane does.
                    .focusProperties { onEnter = { tabsEntry.requestFocus() } }
                    .focusGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvSectionTabs(
                tabs = labels,
                selectedIndex = selectedIndex,
                onTabSelected = { index -> tabs.getOrNull(index)?.let(onContextSelected) },
                entryFocusRequester = tabsEntry,
                endFocusRequester = refreshFocus,
                // On the tabs only, not Refresh: focus follows selection, so a focused tab is the
                // selected one.
                modifier =
                    Modifier.weight(1f).onPreviewKeyEvent { event ->
                        val leave =
                            onLeftFromFirstTab != null &&
                                selectedIndex == 0 &&
                                event.type == KeyEventType.KeyDown &&
                                event.key == Key.DirectionLeft
                        if (leave) onLeftFromFirstTab?.invoke()
                        leave
                    },
            )
            CinemaIconButton(
                onClick = onRefresh,
                enabled = !streamsLoading,
                size = TvDimensions.iconLarge,
                modifier = Modifier.focusRequester(refreshFocus),
                icon = {
                    Icon(
                        imageVector = CinemaIcons.Refresh,
                        contentDescription = stringResource(R.string.category_refresh_streams_description),
                        tint = CinemaTextPrimary,
                        modifier = Modifier.size(TvDimensions.iconMedium.scaled(scale)),
                    )
                },
            )
        }

        StreamList(
            streams = streams,
            streamsLoading = streamsLoading,
            selectedCategoryId = context.id,
            selectedCategoryName = null,
            lastPlayedItemId = lastPlayedItemId,
            nowPlaying = nowPlaying,
            contentType = contentType,
            categoryViewModel = categoryViewModel,
            isDevMode = isDevMode,
            favoriteIds = favoriteIds,
            watchProgress = watchProgress,
            watchedIds = watchedIds,
            onStreamSelected = { streamId, streamName, categoryId, target ->
                if (target is BrowseTarget.CategoryRef) {
                    onCategorySelected(target.categoryId)
                } else {
                    val item = streams?.firstOrNull { it.id == streamId }
                    if (item != null) {
                        onStreamPromote(item)
                    } else {
                        // Not resolvable from the current list (shouldn't normally happen) — fall
                        // back to the caller's own handling.
                        onStreamSelected(streamId, streamName, categoryId, target)
                    }
                }
            },
            onStreamFocused = onStreamFocused,
            onRefreshStreams = { onRefresh() },
            modifier =
                Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft, Key.DirectionRight -> switchTabFromRow(event.key)
                        else -> false
                    }
                },
            thumbnailScale = 0.5f,
            paneFocus = rowsPane,
            showHeader = false,
            emptyMessage =
                if (context is ChannelContext.Favorites) {
                    stringResource(R.string.live_panel_no_favorites)
                } else {
                    stringResource(R.string.category_no_channels)
                },
        )
    }
}

/** The tab title of a [ChannelContext]: the category's name, or the virtual list's label. */
@Composable
internal fun ChannelContext.label(): String =
    when (this) {
        is ChannelContext.Category -> name
        ChannelContext.Recent -> stringResource(R.string.category_recent_label)
        ChannelContext.Favorites -> stringResource(R.string.settings_import_favorites_label)
    }
