package org.njarasoa.fijerena.feature.category.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.tv.material3.Card
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardGlow
import androidx.tv.material3.CardScale
import androidx.tv.material3.CardShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.browseTarget
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.formatRating
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.ImmutableMediaList
import org.njarasoa.fijerena.core.ui.components.ImmutableNowPlaying
import org.njarasoa.fijerena.core.ui.components.ImmutableStringSet
import org.njarasoa.fijerena.core.ui.components.ImmutableWatchProgress
import org.njarasoa.fijerena.core.ui.components.LanguageBadge
import org.njarasoa.fijerena.core.ui.components.RatingBadge
import org.njarasoa.fijerena.core.ui.components.SkeletonList
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.bounceMarquee
import org.njarasoa.fijerena.core.ui.components.staggeredEntrance
import org.njarasoa.fijerena.core.ui.model.FavoriteMenuTarget
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.cards.TvListRowDefaults
import org.njarasoa.fijerena.ui.components.input.PaneFocusState
import org.njarasoa.fijerena.ui.components.input.currentIndicator
import org.njarasoa.fijerena.ui.components.input.paneItem
import org.njarasoa.fijerena.ui.components.input.rememberPaneFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.input.tvPane
import org.njarasoa.fijerena.ui.theme.CinemaOrangeLight
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * Row card styling, built once per list composition instead of once per row.
 * `TvListRowDefaults.colors` is `@Composable`, so it cannot be wrapped in `remember` — hoisting the
 * calls out of the item body is what stops a `CardColors`/`CardBorder`/`CardScale`/`CardGlow`/`CardShape` set
 * being allocated per visible row per recomposition. A data class so a fresh instance (e.g. from
 * the refresh-spinner rotation) still compares equal and lets rows skip.
 */
@Immutable
private data class StreamCardStyle(
    val colors: CardColors,
    val border: CardBorder,
    val cardScale: CardScale,
    val glow: CardGlow,
    val shape: CardShape,
    val titleMedium: TextStyle,
    val bodySmall: TextStyle,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun streamCardStyle(
    scale: Float,
    typography: androidx.tv.material3.Typography = MaterialTheme.typography,
): StreamCardStyle {
    val scaledTitleMedium =
        remember(scale, typography) {
            typography.titleMedium.copy(fontSize = typography.titleMedium.fontSize.scaled(scale))
        }
    val scaledBodySmall =
        remember(scale, typography) {
            typography.bodySmall.copy(fontSize = typography.bodySmall.fontSize.scaled(scale))
        }
    return StreamCardStyle(
        colors = TvListRowDefaults.colors(),
        border = TvListRowDefaults.border(),
        cardScale = TvListRowDefaults.scale(),
        glow = TvListRowDefaults.glow(),
        shape = TvListRowDefaults.shape(),
        titleMedium = scaledTitleMedium,
        bodySmall = scaledBodySmall,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun StreamList(
    streams: ImmutableMediaList?,
    streamsLoading: Boolean,
    selectedCategoryId: String?,
    selectedCategoryName: String?,
    lastPlayedItemId: String?,
    nowPlaying: ImmutableNowPlaying,
    contentType: String,
    categoryViewModel: CategoryViewModel,
    isDevMode: Boolean,
    favoriteIds: ImmutableStringSet = ImmutableStringSet(),
    watchProgress: ImmutableWatchProgress = ImmutableWatchProgress(),
    watchedIds: ImmutableStringSet = ImmutableStringSet(),
    onStreamSelected: (streamId: String, streamName: String, categoryId: String, target: BrowseTarget) -> Unit,
    onRefreshStreams: (String) -> Unit,
    modifier: Modifier = Modifier,
    thumbnailScale: Float = 1f,
    /**
     * This column's pane in a two-pane screen (see `Modifier.tvPane`): Left leaves it for
     * [categoriesPane], and the list takes entry focus once loaded (F-C-1). Null keeps the list
     * a plain column that only follows [lastPlayedItemId].
     */
    paneFocus: PaneFocusState? = null,
    categoriesPane: PaneFocusState? = null,
    /**
     * False when focus belongs to the other pane on this open: the list takes neither entry focus
     * nor the target row's, now or later (Back from the Live TV preview when this list lacks the
     * channel that was playing, LT6). D-pad entry still lands on the remembered row.
     */
    takeEntryFocus: Boolean = true,
    /** The title / Refresh / count header above the rows. The Live TV preview panel draws its tab row instead (LT2). */
    showHeader: Boolean = true,
    /**
     * An empty list shows this text alone — no Refresh button, nothing focusable — so focus
     * stays where it was (the preview panel's tab row, L-10). Null keeps the focusable Refresh.
     */
    emptyMessage: String? = null,
) {
    var targetRotation by remember { mutableStateOf(0f) }

    LaunchedEffect(streamsLoading) {
        if (streamsLoading) {
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
    val listState = rememberLazyListState()

    val scale = LocalUiScale.current
    val cardStyle = streamCardStyle(scale)
    val isRecentList = selectedCategoryId == CategoryViewModel.RECENT_CATEGORY_ID
    val isWatchable = contentType == ContentType.MOVIES || contentType == ContentType.TV_SHOWS
    val canRemoveFromRecent = isRecentList && categoryViewModel.supportsRemoveFromRecent

    // Row actions (UX overhaul plan Part II P3): long-press OK or the Menu key on a row opens this
    // menu for it, in place of the hidden trailing ★/✓/🗑 buttons that used to be extra Right
    // stops. The target is rebuilt from the live sets so the menu shows the row's current state.
    var actionsItem by remember { mutableStateOf<MediaItem?>(null) }
    actionsItem?.let { item ->
        FavoriteContextMenuDialog(
            target =
                FavoriteMenuTarget.Stream(
                    itemId = item.id,
                    itemName = item.name,
                    categoryId = item.categoryId,
                    contentType = contentType,
                    isFavorite = item.id in favoriteIds,
                    isWatched = if (isWatchable) item.id in watchedIds else null,
                    isInRecent = canRemoveFromRecent,
                    seriesId = item.seriesId,
                ),
            onConfirm = { categoryViewModel.toggleFavoriteStream(item.id, item.name, item.categoryId, contentType) },
            onDismiss = { actionsItem = null },
            onToggleWatched = if (isWatchable) ({ categoryViewModel.toggleWatchedStream(item.id, contentType) }) else null,
            onRemoveFromRecent =
                if (canRemoveFromRecent) ({ categoryViewModel.removeFromRecent(item.id, contentType, item.seriesId) }) else null,
        )
    }

    // Auto-scroll and focus on the opened or last played item (on initial load and when returning
    // from details/player).
    // Without a pane, keyed to selectedCategoryId so a list switch resets it — otherwise, when
    // lastPlayedItemId stays the same across the switch, the guard below would treat a *second*
    // visit to an already-visited list as already handled and skip re-focusing, leaving focus
    // wherever it landed after the previously-focused Card was disposed by the switch away. In a
    // pane, not keyed: OK on a category keeps focus on the category, and Right enters the list on
    // its remembered row (F-C-5); the Live TV preview panel's tabs switch its list without
    // taking focus off the tab row (LT2).
    var lastFocusedItemId by remember(if (paneFocus == null) selectedCategoryId else null) { mutableStateOf<String?>(null) }

    // Movie/series row last opened from this list. Opening details doesn't play anything, so
    // lastPlayedItemId never points at it, and on Back the whole destination recomposes from
    // scratch — CategoryList's own "focus the selected category" effect then won and focus landed
    // in the left pane. Saveable so it survives the nav round-trip (NavHost keeps each back-stack
    // entry's saved state); keyed to the category so another list doesn't inherit it. Movies and
    // series only: Live TV keeps following lastPlayedItemId, which tracks channel zapping in the
    // player.
    var openedItemId by rememberSaveable(selectedCategoryId) { mutableStateOf<String?>(null) }
    val focusTargetId = openedItemId ?: lastPlayedItemId

    // Entrance animation plays once per item: LazyColumn recycles item composition off the ends
    // of the scroll buffer, and D-pad scrolling churns that buffer constantly, so without this
    // guard the fade/slide replays on every focus move instead of just on first appearance.
    // Keyed to streams so it resets (bounded) on category switch instead of growing unbounded
    // across every stream id seen this session.
    val enteredStreamIds = remember(streams) { mutableSetOf<String>() }

    // Keyed on the target's position, not on `streams` itself: with a guide source the list is
    // re-emitted again and again as programme info arrives, and each emission relaunched this
    // effect. When the target can't take focus (a hidden list behind the full-screen player, a row
    // never composed) every relaunch failed again — a log flood that never stopped while watching.
    val focusTargetIndex = remember(streams, focusTargetId) { streams?.indexOfFirst { it.id == focusTargetId } ?: -1 }
    val pane = paneFocus ?: rememberPaneFocus()
    pane.bind(
        selectedKey = focusTargetId,
        firstKey = streams?.firstOrNull()?.id,
        listState = listState,
        indexOf = { key -> streams?.indexOfFirst { it.id == key } ?: -1 },
    )
    // Entry focus (F-C-1): a pane's list takes focus once per composition when it first has rows,
    // even with no target — on the remembered row (a Back return), else the first.
    var entryPending by remember { mutableStateOf(paneFocus != null) }
    LaunchedEffect(selectedCategoryId, streamsLoading, focusTargetId, focusTargetIndex, streams.isNullOrEmpty(), takeEntryFocus) {
        // Skip entirely while streamsLoading: that branch renders a spinner, not the list, so no
        // Card exists yet for the FocusRequester to attach to. Previously this ran anyway, always
        // failed, and — critically — still marked lastFocusedItemId as handled, so once the list
        // actually finished loading a moment later the guard below was already tripped and this
        // never got a second chance. Focus was left stuck on the header's refresh button (the
        // first focusable in the composed tree) for good.
        if (streamsLoading || streams.isNullOrEmpty()) return@LaunchedEffect
        if (!takeEntryFocus) {
            // The other pane has focus on this open (LT6): count it as handled, so a later list
            // that has the target doesn't pull focus off the category the user is on.
            lastFocusedItemId = focusTargetId
            entryPending = false
            return@LaunchedEffect
        }
        if (focusTargetId != null && focusTargetId != lastFocusedItemId && focusTargetIndex != -1) {
            // Only mark handled on success, so a failed attempt (e.g. still racing
            // composition) gets another go when the effect's keys change instead of being
            // silently given up on forever.
            if (pane.focusKey(focusTargetId)) {
                lastFocusedItemId = focusTargetId
                entryPending = false
            }
        } else if (entryPending) {
            if (pane.focusEntry()) entryPending = false
        }
    }

    Column(modifier = modifier) {
        if (showHeader) {
            StreamListHeader(
                streams = streams,
                streamsLoading = streamsLoading,
                selectedCategoryId = selectedCategoryId,
                selectedCategoryName = selectedCategoryName,
                categoryViewModel = categoryViewModel,
                isDevMode = isDevMode,
                rotation = rotation,
                onRefreshStreams = onRefreshStreams,
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        color = CinemaSurfaceVariant.copy(alpha = CinemaAlpha.tint),
                        shape = RoundedCornerShape(CornerRadius.small),
                    ).then(
                        // The header above (title, Refresh) stays outside the pane: Up from the
                        // first row reaches it. Right goes nowhere (R1).
                        if (paneFocus != null) Modifier.tvPane(paneFocus, exitLeft = categoriesPane) else Modifier,
                    ),
        ) {
            when {
                streamsLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(Spacing.sm.scaled(scale)),
                    ) {
                        SkeletonList(
                            rowCount = 6,
                            rowHeight = (TvDimensions.cardHeight).scaled(scale),
                            thumbnailWidth = (TvDimensions.posterWidth * thumbnailScale).scaled(scale),
                            thumbnailHeight = (TvDimensions.posterHeight * thumbnailScale).scaled(scale),
                            verticalSpacing =
                                LocalUiStyle.current.grid.spacing
                                    .scaled(scale),
                        )
                    }
                }

                streams.isNullOrEmpty() && emptyMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(Spacing.md.scaled(scale)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = emptyMessage,
                            style = MaterialTheme.typography.bodyLarge,
                            color = CinemaTextSecondary,
                        )
                    }
                }

                streams.isNullOrEmpty() -> {
                    // Nothing else in this branch is focusable. Removing the last item from an already-loaded category (e.g.
                    // unfavoriting the only favorite) left D-pad focus with nowhere to land: the
                    // focused Card was gone, nothing here claimed it, so it fell to the window
                    // root and stopped responding to D-pad input. A refresh action here both
                    // fixes that and gives an actionable retry for a genuinely empty category.
                    val emptyStateFocusRequester = remember { FocusRequester() }
                    if (selectedCategoryId != null) {
                        // Not keyed on `streams`: an empty list re-emitted would relaunch it.
                        LaunchedEffect(selectedCategoryId) {
                            emptyStateFocusRequester.requestFocusWithRetry()
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text =
                                    if (streams == null) {
                                        stringResource(R.string.category_select_to_view_channels)
                                    } else {
                                        stringResource(R.string.category_no_channels)
                                    },
                                style = MaterialTheme.typography.bodyLarge,
                                color = CinemaTextSecondary,
                            )
                            if (selectedCategoryId != null) {
                                Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                                CinemaSecondaryButton(
                                    onClick = { onRefreshStreams(selectedCategoryId) },
                                    text = stringResource(R.string.common_refresh),
                                    modifier = Modifier.focusRequester(emptyStateFocusRequester),
                                )
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(Spacing.sm.scaled(scale)),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                LocalUiStyle.current.grid.spacing
                                    .scaled(scale),
                            ),
                    ) {
                        itemsIndexed(
                            items = streams,
                            key = { _, item -> item.id },
                            contentType = { _, _ -> "stream" },
                        ) { index, item ->
                            StreamItem(
                                item = item,
                                isFavorite = item.id in favoriteIds,
                                watchProgress = watchProgress[item.id] ?: 0f,
                                isWatched = item.id in watchedIds,
                                nowPlayingProgram = nowPlaying[item.id],
                                isCurrent = item.id == lastPlayedItemId,
                                onClick = {
                                    if (isWatchable) {
                                        // Already focused: mark handled too, so the effect above
                                        // doesn't scroll this row to the top as the screen leaves.
                                        lastFocusedItemId = item.id
                                        openedItemId = item.id
                                    }
                                    onStreamSelected(item.id, item.name, item.categoryId, item.browseTarget(contentType))
                                },
                                onOpenActions = { actionsItem = item },
                                cardModifier = Modifier.paneItem(pane, item.id),
                                thumbnailScale = thumbnailScale,
                                cardStyle = cardStyle,
                                modifier =
                                    // remember-scoped so a recomposition of an already-visible item
                                    // reuses the same answer. Called bare, add() returned false on
                                    // the first recomposition and dropped staggeredEntrance from the
                                    // chain mid-animation, detaching the node.
                                    if (remember(item.id) { enteredStreamIds.add(item.id) }) {
                                        Modifier.staggeredEntrance(index)
                                    } else {
                                        Modifier
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The title, Refresh icon and row count above a [StreamList]'s rows. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StreamListHeader(
    streams: ImmutableMediaList?,
    streamsLoading: Boolean,
    selectedCategoryId: String?,
    selectedCategoryName: String?,
    categoryViewModel: CategoryViewModel,
    isDevMode: Boolean,
    rotation: Float,
    onRefreshStreams: (String) -> Unit,
) {
    val scale = LocalUiScale.current
    Column(modifier = Modifier.padding(bottom = Spacing.md.scaled(scale))) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale)),
        ) {
            Text(
                text = selectedCategoryName ?: stringResource(R.string.category_select_category),
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontSize =
                            MaterialTheme.typography.titleLarge.fontSize
                                .scaled(scale),
                    ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            selectedCategoryId?.let { categoryId ->
                CinemaIconButton(
                    onClick = { onRefreshStreams(categoryId) },
                    enabled = !streamsLoading,
                    size = TvDimensions.iconLarge,
                    icon = {
                        Icon(
                            imageVector = CinemaIcons.Refresh,
                            contentDescription = stringResource(R.string.category_refresh_streams_description),
                            tint = CinemaTextPrimary,
                            modifier =
                                Modifier
                                    .size(TvDimensions.iconMedium.scaled(scale))
                                    .rotate(rotation),
                        )
                    },
                )
            }
        }
        if (streams != null) {
            val streamsLabel = stringResource(R.string.stream_count_format, streams.size)
            val streamCountText =
                buildString {
                    append(streamsLabel)
                    if (isDevMode && selectedCategoryId != null) {
                        categoryViewModel.getPayloadSize(selectedCategoryId)?.let {
                            append(" | $it")
                        }
                        categoryViewModel.getFetchTime(selectedCategoryId)?.let {
                            append(" in $it")
                        }
                    }
                }
            Text(
                text = streamCountText,
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontSize =
                            MaterialTheme.typography.labelSmall.fontSize
                                .scaled(scale),
                    ),
                color = CinemaTextSecondary,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StreamItem(
    item: MediaItem,
    isFavorite: Boolean = false,
    watchProgress: Float = 0f,
    isWatched: Boolean = false,
    nowPlayingProgram: EpgProgram? = null,
    /** The channel playing / the last played item: the P5 "current" bar and title colour. */
    isCurrent: Boolean = false,
    onClick: () -> Unit,
    /** Long-press OK or the Menu key: open the row's action menu (P3). */
    onOpenActions: () -> Unit,
    /** Applied to the card itself (the focusable), not the row. */
    cardModifier: Modifier = Modifier,
    thumbnailScale: Float = 1f,
    cardStyle: StreamCardStyle,
    modifier: Modifier = Modifier,
) {
    val scale = LocalUiScale.current
    // Marquee only while focused. BounceMarqueeNode runs a withFrameNanos loop that invalidates
    // draw every frame for as long as its text overflows, and IPTV channel names overflow
    // constantly — with it applied unconditionally, every visible row kept two such loops running
    // (title + "Now:"), so a dozen on-screen rows meant ~24 concurrent per-frame animations
    // competing with the scroll itself. At rest the two look identical: fraction is 0, so the
    // node draws the same clipped text a plain Text does.
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        onLongClick = onOpenActions,
        modifier =
            modifier
                .padding(horizontal = Spacing.md.scaled(scale))
                .fillMaxWidth()
                .then(cardModifier)
                .onFocusChanged { isFocused = it.isFocused }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || event.key != Key.Menu) return@onKeyEvent false
                    onOpenActions()
                    true
                },
        colors = cardStyle.colors,
        border = cardStyle.border,
        shape = cardStyle.shape,
        scale = cardStyle.cardScale,
        glow = cardStyle.glow,
    ) {
        Column(modifier = Modifier.currentIndicator(isCurrent)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(Spacing.sm.scaled(scale)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
            ) {
                CinemaThumbnail(
                    url = item.thumbnailUrl,
                    fallbackLetter = item.name.firstOrNull(),
                    contentType = ThumbnailContentType.DEFAULT,
                    overlayGradient = true,
                    modifier =
                        Modifier
                            .size(
                                width = (TvDimensions.posterWidth * thumbnailScale).scaled(scale),
                                height = (TvDimensions.posterHeight * thumbnailScale).scaled(scale),
                            ),
                )

                val parsedTitle = remember(item.name) { parseDisplayTitle(item.name) }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isFavorite) {
                            Text(
                                text = "\u2605",
                                style = cardStyle.titleMedium,
                                color = CinemaAccent,
                            )
                        }

                        if (isWatched) {
                            Icon(
                                imageVector = CinemaIcons.CheckCircle,
                                contentDescription = stringResource(R.string.content_watched_badge),
                                tint = CinemaSuccess,
                                modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                            )
                        }

                        parsedTitle.badge?.let { LanguageBadge(it) }

                        Text(
                            // See mobile's StreamCard — provider data occasionally sends a blank
                            // name (e.g. "EN -  (US)" with nothing between the dashes).
                            text = parsedTitle.title.ifBlank { stringResource(R.string.content_untitled) },
                            style = cardStyle.titleMedium,
                            color = TvListRowDefaults.titleColor(isCurrent = isCurrent, isFocused = isFocused),
                            maxLines = 1,
                            modifier = if (isFocused) Modifier.bounceMarquee() else Modifier,
                        )
                    }
                    item.metadata.rating?.let { rating ->
                        RatingBadge(
                            rating = rating,
                            textColor = CinemaAccent.copy(alpha = CinemaAlpha.textMedium),
                            style = cardStyle.bodySmall,
                        )
                    }
                    nowPlayingProgram?.let { program ->
                        Text(
                            text = stringResource(R.string.epg_now_prefix, program.title),
                            style = cardStyle.bodySmall,
                            color = CinemaOrangeLight,
                            maxLines = 1,
                            modifier = if (isFocused) Modifier.bounceMarquee() else Modifier,
                        )
                    }
                }

                // Discoverability hint for the action menu: a glyph, not a focus stop, on the
                // focused row only (plan Decisions 1).
                if (isFocused) RowActionsHint()
            }

            if (watchProgress > 0f) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { watchProgress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = TvDimensions.borderFocused.scaled(scale))
                            .height(TvDimensions.resumeBarHeight.scaled(scale)),
                    color = CinemaAccent,
                    trackColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.focusedTint),
                )
            }
        }
    }
}

/** The "⋮" at the end of a focused content row (StreamList and CategoryList): long-press OK or Menu opens its actions. */
@Composable
internal fun RowActionsHint() {
    Icon(
        imageVector = CinemaIcons.MoreVert,
        contentDescription = stringResource(R.string.row_actions_hint),
        tint = CinemaTextSecondary,
        modifier = Modifier.size(TvDimensions.iconSmall.scaled(LocalUiScale.current)),
    )
}
