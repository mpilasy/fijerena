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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.ImmutableMediaList
import org.njarasoa.fijerena.core.ui.components.ImmutableNowPlaying
import org.njarasoa.fijerena.core.ui.components.ImmutableStringSet
import org.njarasoa.fijerena.core.ui.components.ImmutableWatchProgress
import org.njarasoa.fijerena.core.ui.components.staggeredEntrance
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.bounceMarquee
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.theme.CinemaOrangeLight
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle

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
    onStreamSelected: (streamId: String, streamName: String, categoryId: String, providerData: Map<String, String>) -> Unit,
    onStreamLongPress: (MediaItem) -> Unit = {},
    onStreamFocused: (MediaItem) -> Unit = {},
    onRefreshStreams: (String) -> Unit,
    modifier: Modifier = Modifier,
    thumbnailScale: Float = 1f,
) {
    // Animate rotation when refreshing
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
    val listState = rememberTvLazyListState()
    // FocusRequester for auto-scroll target — cleared on each category switch to avoid unbounded growth
    val lastPlayedFocusRequester = remember { FocusRequester() }

    val scale = LocalUiScale.current

    // Auto-scroll and focus on last played item (on initial load and when returning from player).
    // Keyed to selectedCategoryId so switching list context (e.g. the Live TV preview panel's
    // Last Watched <-> Favorites toggle) resets it — otherwise, since lastPlayedItemId (the
    // current channel) stays the same across that toggle, the guard below would treat a
    // *second* visit to an already-visited list as already handled and skip re-focusing, leaving
    // focus wherever it landed after the previously-focused Card was disposed by the switch away.
    var lastFocusedItemId by remember(selectedCategoryId) { mutableStateOf<String?>(null) }

    // Entrance animation plays once per item: LazyColumn recycles item composition off the ends
    // of the scroll buffer, and D-pad scrolling churns that buffer constantly, so without this
    // guard the fade/slide replays on every focus move instead of just on first appearance.
    // Keyed to streams so it resets (bounded) on category switch instead of growing unbounded
    // across every stream id seen this session.
    val enteredStreamIds = remember(streams) { mutableSetOf<String>() }

    LaunchedEffect(streams, streamsLoading, lastPlayedItemId) {
        // Skip entirely while streamsLoading: that branch renders a spinner, not the list, so no
        // Card exists yet for the FocusRequester to attach to. Previously this ran anyway, always
        // failed, and — critically — still marked lastFocusedItemId as handled, so once the list
        // actually finished loading a moment later the guard below was already tripped and this
        // never got a second chance. Focus was left stuck on the header's refresh button (the
        // first focusable in the composed tree) for good.
        if (!streamsLoading && !streams.isNullOrEmpty() && lastPlayedItemId != null && lastPlayedItemId != lastFocusedItemId) {
            val lastPlayedIndex = streams.indexOfFirst { it.id == lastPlayedItemId }
            if (lastPlayedIndex != -1) {
                listState.animateScrollToItem(lastPlayedIndex)
                // Small delay so the target item is actually composed and its FocusRequester
                // attached before requesting focus — mirrors TvChannelListOverlay.kt's identical
                // race.
                kotlinx.coroutines.delay(100)
                try {
                    lastPlayedFocusRequester.requestFocus()
                    // Only mark handled on success, so a failed attempt (e.g. still racing
                    // composition) gets retried on the next recomposition instead of being
                    // silently given up on forever.
                    lastFocusedItemId = lastPlayedItemId
                } catch (_: IllegalStateException) {
                }
            }
        }
    }

    Column(modifier = modifier) {
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
                // Always show refresh button when a category is selected
                selectedCategoryId?.let { categoryId ->
                    CinemaIconButton(
                        onClick = { onRefreshStreams(categoryId) },
                        enabled = !streamsLoading,
                        size = 40.dp,
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
                        }
                    )
                }
            }
            // Show stream count
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

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        color = CinemaSurfaceVariant.copy(alpha = CinemaAlpha.tint),
                        shape = RoundedCornerShape(CornerRadius.small),
                    ),
        ) {
            when {
                streamsLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(TvDimensions.progressIndicator),
                            color = CinemaAccent,
                        )
                    }
                }
                streams.isNullOrEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
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
                    }
                }
                else -> {
                    TvLazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(Spacing.sm.scaled(scale)),
                        verticalArrangement = Arrangement.spacedBy(LocalUiStyle.current.grid.spacing.scaled(scale)),
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
                                onClick = { onStreamSelected(item.id, item.name, item.categoryId, item.providerData) },
                                onLongPress = { onStreamLongPress(item) },
                                onFocused = { onStreamFocused(item) },
                                // Only the last-played item gets a focus requester for auto-scroll
                                focusRequester = if (item.id == lastPlayedItemId) lastPlayedFocusRequester else null,
                                thumbnailScale = thumbnailScale,
                                modifier =
                                    if (enteredStreamIds.add(item.id)) {
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StreamItem(
    item: MediaItem,
    isFavorite: Boolean = false,
    watchProgress: Float = 0f,
    isWatched: Boolean = false,
    nowPlayingProgram: EpgProgram? = null,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    onFocused: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    thumbnailScale: Float = 1f,
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
    val typography = MaterialTheme.typography
    val scaledStyles =
        remember(scale, typography) {
            object {
                val titleMedium = typography.titleMedium.copy(fontSize = typography.titleMedium.fontSize.scaled(scale))
                val bodySmall = typography.bodySmall.copy(fontSize = typography.bodySmall.fontSize.scaled(scale))
            }
        }

    Card(
        onClick = onClick,
        modifier =
            modifier
                .padding(horizontal = Spacing.md.scaled(scale))
                .fillMaxWidth()
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .tvLongPress(onLongPress)
                .then(
                    if (focusRequester != null) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    },
                ),
        colors =
            CardDefaults.colors(
                containerColor = CinemaSurface,
                contentColor = CinemaTextPrimary,
                focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.tint),
                focusedContentColor = CinemaTextPrimary,
            ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CornerRadius.medium.scaled(scale))),
        scale =
            CardDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScaleContent,
                pressedScale = TvFocusTokens.pressedScaleSubtle,
            ),
        glow =
            CardDefaults.glow(
                focusedGlow =
                    androidx.tv.material3.Glow(
                        elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.cardElevationShadow),
                        elevation = TvFocusTokens.focusShadowElevation,
                    ),
            ),
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(Spacing.sm.scaled(scale)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
            ) {
                // Poster thumbnail
                CinemaThumbnail(
                    url = item.thumbnailUrl,
                    fallbackLetter = item.name.firstOrNull(),
                    contentType = ThumbnailContentType.DEFAULT,
                    modifier =
                        Modifier
                            .size(
                                width = (TvDimensions.posterWidth * thumbnailScale).scaled(scale),
                                height = (TvDimensions.posterHeight * thumbnailScale).scaled(scale),
                            ),
                )

                // Text info
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isFavorite) {
                            Text(
                                text = "\u2605",
                                style = scaledStyles.titleMedium,
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

                        Text(
                            text = item.name,
                            style = scaledStyles.titleMedium,
                            color = CinemaTextPrimary,
                            maxLines = 1,
                            modifier = if (isFocused) Modifier.bounceMarquee() else Modifier,
                        )
                    }
                    // Rating (e.g. "7.9 | PG-13")
                    item.metadata.rating?.let { rating ->
                        Text(
                            text = "★ $rating",
                            style = scaledStyles.bodySmall,
                            color = CinemaAccent.copy(alpha = CinemaAlpha.textMedium),
                            maxLines = 1,
                        )
                    }
                    // "What's On Now" for Live TV
                    nowPlayingProgram?.let { program ->
                        Text(
                            text = stringResource(R.string.epg_now_prefix, program.title),
                            style = scaledStyles.bodySmall,
                            color = CinemaOrangeLight,
                            maxLines = 1,
                            modifier = if (isFocused) Modifier.bounceMarquee() else Modifier,
                        )
                    }
                }
            }

            // Progress bar
            if (watchProgress > 0f) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { watchProgress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(TvDimensions.borderFocused.scaled(scale)),
                    color = CinemaAccent,
                    trackColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.focusedTint),
                )
            }
        }
    }
}
