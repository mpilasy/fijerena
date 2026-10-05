package org.njarasoa.fijerena.feature.category.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.ImmutableCategoryList
import org.njarasoa.fijerena.core.ui.components.ImmutableMediaList
import org.njarasoa.fijerena.core.ui.components.ImmutableNowPlaying
import org.njarasoa.fijerena.core.ui.components.ImmutableStringSet
import org.njarasoa.fijerena.core.ui.components.ImmutableWatchProgress
import org.njarasoa.fijerena.core.ui.navigation.SectionRoot
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.ui.components.SectionRootButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.rememberPaneFocus
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

private val CinemaTextSecondaryHigh = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)

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
    onSearchClick: () -> Unit,
    onEpgClick: (categoryId: String, categoryName: String) -> Unit,
    onBack: () -> Unit,
    /** The section-root button (P6), last in the header; null hides it. */
    sectionRoot: SectionRoot? = null,
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

    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    val scaledStyles =
        remember(scale, typography) {
            object {
                val displaySmall = typography.displaySmall.copy(fontSize = typography.displaySmall.fontSize.scaled(scale))
                val titleMedium = typography.titleMedium.copy(fontSize = typography.titleMedium.fontSize.scaled(scale))
                val labelSmall = typography.labelSmall.copy(fontSize = typography.labelSmall.fontSize.scaled(scale))
                val titleSmall = typography.titleSmall.copy(fontSize = typography.titleSmall.fontSize.scaled(scale))
                val bodySmall = typography.bodySmall.copy(fontSize = typography.bodySmall.fontSize.scaled(scale))
            }
        }

    val categoryMap =
        remember(categories) {
            categories.associateBy { it.id }
        }

    // Back from Search or the TV Guide lands on the header button that opened it. Runs once the
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

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.lg.scaled(scale)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CinemaIconButton(
                    onClick = {
                        returnFocus.leaveFrom(RETURN_SEARCH)
                        onSearchClick()
                    },
                    modifier = Modifier.navReturnFocusTarget(returnFocus, RETURN_SEARCH),
                    icon = {
                        Icon(
                            imageVector = CinemaIcons.Search,
                            contentDescription = stringResource(R.string.common_search),
                            tint = CinemaTextPrimary,
                        )
                    },
                )
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
                SectionRootButton(sectionRoot)
                Column {
                    Text(
                        text = providerName,
                        style = scaledStyles.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text =
                            when (contentType) {
                                ContentType.LIVE_TV -> stringResource(R.string.provider_live_tv_label)
                                ContentType.MOVIES -> stringResource(R.string.provider_movies_label)
                                ContentType.TV_SHOWS -> stringResource(R.string.provider_tv_shows_label)
                                else -> contentType.replace("_", " ")
                            },
                        style = scaledStyles.titleMedium,
                        color = CinemaAccent,
                    )
                    Text(
                        text = stringResource(R.string.category_count_format, categories.size),
                        style = scaledStyles.labelSmall,
                        color = CinemaTextSecondary,
                    )
                }
            }
            Text(
                text = providerName,
                style = scaledStyles.titleSmall,
                color = CinemaTextSecondaryHigh,
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
                    style = scaledStyles.bodySmall,
                    color =
                        if (epgIndexState is EpgIndexState.Indexing) {
                            CinemaTextSecondary
                        } else {
                            CinemaError
                        },
                    modifier = Modifier.padding(bottom = Spacing.xs.scaled(scale)),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg.scaled(scale)),
        ) {
            CategoryList(
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                categoriesRefreshing = categoriesRefreshing,
                contentType = contentType,
                categoryViewModel = categoryViewModel,
                favoriteCategoryIds = favoriteCategoryIds,
                onCategorySelected = onCategorySelected,
                onRefreshCategories = onRefreshCategories,
                paneFocus = categoriesPane,
                itemsPane = itemsPane,
                // Entry focus (F-C-1): the selected category only while the item pane has nothing
                // to land on; once the category's rows are there (or on Back, when they already
                // are) StreamList lands on its entry row.
                focusSelectedOnOpen = streams.isNullOrEmpty() || playingNotListed,
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
                modifier =
                    Modifier
                        .weight(0.7f)
                        .fillMaxHeight(),
            )
        }
    }
}

// Keys for the header buttons that navigate away — see rememberNavReturnFocus.
private const val RETURN_SEARCH = "search"
private const val RETURN_TV_GUIDE = "tvGuide"
