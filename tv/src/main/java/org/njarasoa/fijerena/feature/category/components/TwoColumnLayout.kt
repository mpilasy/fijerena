package org.njarasoa.fijerena.feature.category.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogActionButton
import org.njarasoa.fijerena.core.ui.components.ImmutableCategoryList
import org.njarasoa.fijerena.core.ui.components.ImmutableMediaList
import org.njarasoa.fijerena.core.ui.components.ImmutableNowPlaying
import org.njarasoa.fijerena.core.ui.components.ImmutableStringSet
import org.njarasoa.fijerena.core.ui.components.ImmutableWatchProgress
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.components.TvUndoBar
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.rememberPaneFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.rememberUndoBarState
import org.njarasoa.fijerena.ui.components.undoOnMenuKey
import org.njarasoa.fijerena.ui.theme.Spacing

@Composable
internal fun TwoColumnLayout(
    categoryViewModel: CategoryViewModel,
    categories: ImmutableCategoryList,
    selectedCategoryId: String?,
    streams: ImmutableMediaList?,
    streamsLoading: Boolean,
    categoriesRefreshing: Boolean,
    lastPlayedItemId: String?,
    nowPlaying: ImmutableNowPlaying,
    contentType: String,
    /** Live TV, Back from the preview: the channel it was playing (LT6). */
    returnedPlayingId: String? = null,
    favoriteIds: ImmutableStringSet = ImmutableStringSet(),
    favoriteCategoryIds: ImmutableStringSet = ImmutableStringSet(),
    watchProgress: ImmutableWatchProgress = ImmutableWatchProgress(),
    watchedIds: ImmutableStringSet = ImmutableStringSet(),
    supportsNativeEpg: Boolean,
    epgIndexState: EpgIndexState,
    onCategorySelected: (String) -> Unit,
    onStreamSelected: (streamId: String, streamName: String, categoryId: String, target: BrowseTarget) -> Unit,
    onRefreshCategories: () -> Unit,
    onRefreshStreams: (String) -> Unit,
    onEpgClick: (categoryId: String, categoryName: String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    var providerName by remember { mutableStateOf(appSettings.providerName) }
    // Read once — avoids SharedPreferences disk I/O on every recomposition
    val isDevMode = remember { appSettings.isDevMode }

    // Load actual provider name from database (AppSettings default is "My Provider")
    LaunchedEffect(Unit) {
        val repo =
            org.njarasoa.fijerena.core.network.provider
                .ProviderRepository(context.applicationContext)
        repo.getActiveProvider()?.let { providerName = it.name }
    }

    val categoryMap =
        remember(categories) {
            categories.associateBy { it.id }
        }
    // The source's own categories: Recent, Favourites and the like are modes, not categories.
    val categoryCount = remember(categories) { categories.count { !it.isVirtual } }

    // Back from the TV Guide lands on the header button that opened it. Runs once the
    // screen is RESUMED, after CategoryList's and StreamList's own focus effects, so it has the
    // last word. Rows opened from the stream list are StreamList's own business (openedItemId).
    val returnFocus = rememberNavReturnFocus()
    NavReturnFocusEffect(returnFocus)

    // The two columns are panes (Modifier.tvPane): Left/Right move between them and land on the
    // selected category / the remembered item; Up/Down stay in their column. Both remember their
    // row across the Back round trip.
    val categoriesPane = rememberPaneFocus()
    val itemsPane = rememberPaneFocus()

    // Back from the Live TV preview (LT6): the channel it was playing is the current channel here
    // — lastPlayedItemId only moves once a channel has been watched past the watch delay, so it
    // still names the one that opened the preview. When the list on screen has it, the items pane
    // lands on that row and remembers it; when it does not (the channel was played from another
    // list, or Recent has not recorded it yet), focus stays on the selected category.
    val currentItemId = returnedPlayingId ?: lastPlayedItemId
    val playingNotListed =
        remember(returnedPlayingId, streams, streamsLoading) {
            returnedPlayingId != null && !streamsLoading && streams != null && streams.none { it.id == returnedPlayingId }
        }

    // One Refresh for the screen (TV UI audit #5), in the header: the categories and the list on
    // screen, which the two panes' own Refresh icons used to do one at a time.
    val refreshing = categoriesRefreshing || streamsLoading
    var targetRotation by remember { mutableStateOf(0f) }
    // Keyed on the flag: the loop never returns, so a false value has to cancel it.
    LaunchedEffect(refreshing) {
        if (refreshing) {
            while (true) {
                targetRotation = (targetRotation + 360f) % 3600f
                kotlinx.coroutines.delay(CinemaAnimation.loadingDebounceMs)
            }
        }
    }
    val rotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = CinemaAnimation.fadeInDurationMs, easing = LinearEasing),
        label = "refresh_rotation",
    )

    // A removal's Undo (docs/plans/archive/20261009_tv-recents-favorites-plan.md → A): the bar at the
    // screen's foot, Menu undoes while it shows. Gone when another list is picked.
    val undoBar = rememberUndoBarState()
    LaunchedEffect(selectedCategoryId) { undoBar.dismiss() }

    // Edit mode of the Recent / Favourites list (plan → A): the pencil in the header turns it on
    // and off; Back or another list ends it. Clear Recent, in Recent's, asks once.
    val canEdit =
        selectedCategoryId == CategoryViewModel.FAVORITES_CATEGORY_ID ||
            (selectedCategoryId == CategoryViewModel.RECENT_CATEGORY_ID && categoryViewModel.supportsRemoveFromRecent)
    var editMode by remember(selectedCategoryId) { mutableStateOf(false) }
    var confirmClearRecent by remember { mutableStateOf(false) }
    // Clear Recent exists only in edit mode: ending the mode while it has focus (Back, from the
    // header) removed the focused button and left nothing focused. Focus goes to the pencil then.
    // Read at the Back press: the button's own focus callback has already said "not focused" by
    // the time an effect could look.
    var clearRecentFocused by remember { mutableStateOf(false) }
    var refocusEditAction by remember { mutableStateOf(false) }
    val editActionFocus = remember { FocusRequester() }
    LaunchedEffect(editMode, refocusEditAction) {
        if (!editMode && refocusEditAction) {
            refocusEditAction = false
            editActionFocus.requestFocusWithRetry()
        }
    }
    if (confirmClearRecent) {
        ClearRecentDialog(
            onConfirm = {
                confirmClearRecent = false
                categoryViewModel.clearRecent()
            },
            onDismiss = { confirmClearRecent = false },
        )
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .undoOnMenuKey(undoBar)
                .onPreviewKeyEvent { event ->
                    // Back ends edit mode before anything else takes it — in onPreviewKeyEvent, as a
                    // BackHandler misses the first press while a row holds focus (NAVIGATION_GUIDE →
                    // "TV Back on Detail Screens"). Both edges are consumed; the release acts.
                    val isBack = editMode && event.key == Key.Back
                    if (isBack && event.type == KeyEventType.KeyUp) {
                        refocusEditAction = clearRecentFocused
                        editMode = false
                    }
                    isBack
                },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TV UI audit X6: the section is the title, the source and its category count the
            // subtitle (the source no longer repeated at the far right), the icon actions at the end.
            TvScreenHeader(
                title = sectionTitle(contentType),
                subtitle = "$providerName · ${stringResource(R.string.category_count_format, categoryCount)}",
            ) {
                val hasEpgData =
                    supportsNativeEpg ||
                        epgIndexState is EpgIndexState.Indexed
                if (contentType == ContentType.LIVE_TV && selectedCategoryId != null && hasEpgData) {
                    val selectedCategoryName = categoryMap[selectedCategoryId]?.name
                    if (selectedCategoryName != null) {
                        TvIconAction(
                            onClick = {
                                returnFocus.leaveFrom(RETURN_TV_GUIDE)
                                onEpgClick(selectedCategoryId, selectedCategoryName)
                            },
                            icon = CinemaIcons.DateRange,
                            label = stringResource(R.string.common_tv_guide),
                            modifier = Modifier.navReturnFocusTarget(returnFocus, RETURN_TV_GUIDE),
                        )
                    }
                }
                if (editMode && selectedCategoryId == CategoryViewModel.RECENT_CATEGORY_ID) {
                    TvIconAction(
                        onClick = { confirmClearRecent = true },
                        icon = CinemaIcons.DeleteForever,
                        label = stringResource(R.string.recent_clear),
                        danger = true,
                        modifier = Modifier.onFocusChanged { clearRecentFocused = it.isFocused },
                    )
                }
                if (canEdit) {
                    TvIconAction(
                        onClick = { editMode = !editMode },
                        icon = CinemaIcons.Edit,
                        label = stringResource(if (editMode) R.string.edit_list_done else R.string.edit_list_action),
                        modifier = Modifier.focusRequester(editActionFocus),
                    )
                }
                TvIconAction(
                    onClick = {
                        // Never disabled while it runs (a focused button that disables drops focus):
                        // a press while refreshing is ignored instead.
                        if (!refreshing) {
                            onRefreshCategories()
                            selectedCategoryId?.let(onRefreshStreams)
                        }
                    },
                    icon = CinemaIcons.Refresh,
                    label = stringResource(R.string.common_refresh),
                    iconModifier = Modifier.rotate(rotation),
                )
            }

            if (contentType == ContentType.LIVE_TV) {
                val epgErrorMessage =
                    when (epgIndexState) {
                        is EpgIndexState.Failed -> stringResource(R.string.epg_indexing_failed)
                        is EpgIndexState.Indexing -> stringResource(R.string.epg_indexing_progress, epgIndexState.progressPercent)
                        else -> null
                    }
                if (epgErrorMessage != null) {
                    Text(
                        text = epgErrorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (epgIndexState is EpgIndexState.Indexing) {
                                CinemaTextSecondary
                            } else {
                                CinemaError
                            },
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )
                }
            }

            Row(
                // Leaves room for the Undo bar under it while the bar shows, instead of the bar
                // covering the list's bottom row.
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                CategoryList(
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    contentType = contentType,
                    categoryViewModel = categoryViewModel,
                    favoriteCategoryIds = favoriteCategoryIds,
                    onCategorySelected = onCategorySelected,
                    paneFocus = categoriesPane,
                    itemsPane = itemsPane,
                    // Entry focus (F-C-1): the selected category only while the item pane has nothing
                    // to land on; once the category's rows are there (or on Back, when they already
                    // are) StreamList lands on its entry row.
                    focusSelectedOnOpen = streams.isNullOrEmpty() || playingNotListed,
                    undoBar = undoBar,
                    modifier =
                        Modifier
                            .weight(0.3f)
                            .fillMaxHeight(),
                )

                StreamList(
                    streams = streams,
                    streamsLoading = streamsLoading,
                    selectedCategoryId = selectedCategoryId,
                    selectedCategoryName = selectedCategoryId?.let { categoryMap[it]?.name },
                    lastPlayedItemId = currentItemId,
                    nowPlaying = nowPlaying,
                    contentType = contentType,
                    categoryViewModel = categoryViewModel,
                    isDevMode = isDevMode,
                    favoriteIds = favoriteIds,
                    watchProgress = watchProgress,
                    watchedIds = watchedIds,
                    onStreamSelected = { streamId, streamName, categoryId, target ->
                        // A row from "Recent Categories"/"Favorite Categories" browses, it doesn't play.
                        if (target is BrowseTarget.CategoryRef) {
                            onCategorySelected(target.categoryId)
                        } else {
                            onStreamSelected(streamId, streamName, categoryId, target)
                        }
                    },
                    onRefreshStreams = onRefreshStreams,
                    paneFocus = itemsPane,
                    categoriesPane = categoriesPane,
                    takeEntryFocus = !playingNotListed,
                    undoBar = undoBar,
                    editMode = editMode,
                    modifier =
                        Modifier
                            .weight(0.7f)
                            .fillMaxHeight(),
                )
            }
            TvUndoBar(undoBar, Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.sm))
        }
    }
}

/**
 * Clear Recent's one confirmation (the only confirmed removal, plan → A): focus opens on Cancel,
 * never on the destructive action (focus contract rule 7).
 */
@Composable
private fun ClearRecentDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.recent_clear_confirm_title),
                style = MaterialTheme.typography.titleMedium,
                color = CinemaTextPrimary,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.recent_clear_confirm_message),
                style = MaterialTheme.typography.bodyMedium,
                color = CinemaTextSecondary,
            )
        },
        confirmButton = {
            CinemaDialogActionButton(
                onClick = onConfirm,
                colors =
                    androidx.compose.material3.ButtonDefaults
                        .buttonColors(containerColor = CinemaError),
            ) {
                Text(text = stringResource(R.string.recent_clear))
            }
        },
        dismissButton = {
            CinemaDialogActionButton(
                onClick = onDismiss,
                colors =
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = CinemaSurfaceVariant,
                        contentColor = CinemaTextPrimary,
                    ),
            ) {
                Text(text = stringResource(R.string.common_cancel))
            }
        },
        containerColor = CinemaSurface,
        titleContentColor = CinemaTextPrimary,
        textContentColor = CinemaTextSecondary,
    )
}

// Keys for the header buttons that navigate away — see rememberNavReturnFocus.
private const val RETURN_TV_GUIDE = "tvGuide"
