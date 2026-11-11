@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.episode

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.CardScale
import androidx.tv.material3.CardShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.WatchedItem
import org.njarasoa.fijerena.core.network.friendlyErrorMessage
import org.njarasoa.fijerena.core.network.resumeProgress
import org.njarasoa.fijerena.core.network.tmdb.TitleCredits
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.RelatedTitles
import org.njarasoa.fijerena.core.player.domain.SeasonInfo
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.player.domain.cleanTitle
import org.njarasoa.fijerena.core.player.domain.episodeIdMatchingName
import org.njarasoa.fijerena.core.player.domain.episodeOwnTitle
import org.njarasoa.fijerena.core.player.domain.firstSeasonWithUnwatchedEpisode
import org.njarasoa.fijerena.core.player.domain.flattenedEpisodes
import org.njarasoa.fijerena.core.player.domain.isWatchedToEnd
import org.njarasoa.fijerena.core.player.domain.resumeAnchorEpisodeId
import org.njarasoa.fijerena.core.player.domain.seasonNumberContaining
import org.njarasoa.fijerena.core.player.domain.seriesYearRange
import org.njarasoa.fijerena.core.player.domain.sortedSeasons
import org.njarasoa.fijerena.core.player.model.computeEndsAt
import org.njarasoa.fijerena.core.player.model.extractYear
import org.njarasoa.fijerena.core.player.model.formatDuration
import org.njarasoa.fijerena.core.player.model.formatRating
import org.njarasoa.fijerena.core.player.model.formatTime
import org.njarasoa.fijerena.core.player.model.hasMeaningfulDuration
import org.njarasoa.fijerena.core.player.model.parseDurationToSeconds
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaBadge
import org.njarasoa.fijerena.core.ui.components.CinemaProgressBar
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.MenuAction
import org.njarasoa.fijerena.core.ui.components.PanelFill
import org.njarasoa.fijerena.core.ui.components.RatingBadge
import org.njarasoa.fijerena.core.ui.components.SourceBadge
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.WatchedBadge
import org.njarasoa.fijerena.core.ui.components.panel
import org.njarasoa.fijerena.core.ui.components.rememberChildProfile
import org.njarasoa.fijerena.core.ui.history.LocalNerd
import org.njarasoa.fijerena.core.ui.newepisodes.LocalNewEpisodes
import org.njarasoa.fijerena.core.ui.newepisodes.NewEpisodeMark
import org.njarasoa.fijerena.core.ui.person.rememberEpisodeCredits
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.ProvideUiScaledDensity
import org.njarasoa.fijerena.core.ui.trailer.rememberPlayTrailer
import org.njarasoa.fijerena.core.ui.utils.displayDate
import org.njarasoa.fijerena.core.ui.utils.seasonsAndEpisodesText
import org.njarasoa.fijerena.core.ui.utils.titleFactRows
import org.njarasoa.fijerena.core.ui.viewmodels.PlayedSeries
import org.njarasoa.fijerena.core.ui.viewmodels.SeriesDetailsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SeriesDetailsViewModelFactory
import org.njarasoa.fijerena.core.ui.wanted.rememberWishlist
import org.njarasoa.fijerena.feature.category.components.RowActionsHint
import org.njarasoa.fijerena.feature.category.components.tvLongPress
import org.njarasoa.fijerena.feature.contentselection.components.openActionsOnMenuKey
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.ui.components.IgnorePinnedHeader
import org.njarasoa.fijerena.ui.components.KeepHeroInView
import org.njarasoa.fijerena.ui.components.RelatedTitlesRow
import org.njarasoa.fijerena.ui.components.TvActionMenu
import org.njarasoa.fijerena.ui.components.TvBackdropText
import org.njarasoa.fijerena.ui.components.TvCreditsTabContent
import org.njarasoa.fijerena.ui.components.TvDetailHero
import org.njarasoa.fijerena.ui.components.TvDetailRow
import org.njarasoa.fijerena.ui.components.TvErrorState
import org.njarasoa.fijerena.ui.components.TvIcon
import org.njarasoa.fijerena.ui.components.TvOtherCopiesList
import org.njarasoa.fijerena.ui.components.TvPickerDialog
import org.njarasoa.fijerena.ui.components.TvSectionTabs
import org.njarasoa.fijerena.ui.components.WishlistRemovePrompt
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.cards.TvCard
import org.njarasoa.fijerena.ui.components.cards.TvCardLook
import org.njarasoa.fijerena.ui.components.cards.TvCardLooks
import org.njarasoa.fijerena.ui.components.cards.TvRowTitle
import org.njarasoa.fijerena.ui.components.input.CurrentIndicatorEdge
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.TvInputDefaults
import org.njarasoa.fijerena.ui.components.input.TvInputListItem
import org.njarasoa.fijerena.ui.components.input.currentIndicator
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.modifiers.pickerFocus
import org.njarasoa.fijerena.ui.components.modifiers.tabChipFill
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.components.ratingOutOfTen
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled
import kotlin.math.roundToInt
import org.njarasoa.fijerena.core.player.domain.EpisodeItem as DomainEpisodeItem

@Composable
fun EpisodeSelectionScreen(
    seriesId: String,
    seriesName: String,
    categoryId: String,
    initialEpisodeId: String? = null,
    onEpisodeSelected: (
        episodeId: String,
        episodeTitle: String,
        extension: String,
        startFromBeginning: Boolean,
        series: PlayedSeries,
    ) -> Unit,
    onCategorySelected: (categoryId: String) -> Unit,
    onBack: () -> Unit,
    onRelatedTitleSelected: (MediaItem) -> Unit = {},
    // The route's source.
    providerId: Long = 0L,
    // A cast card or a crew name of the Cast tab: opens the person page.
    onPersonSelected: (personId: String, name: String, crew: Boolean) -> Unit = { _, _, _ -> },
    // The season to open on when no episode is named (an actor's season, or an episode the source lacks).
    initialSeason: Int? = null,
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val viewModel: SeriesDetailsViewModel =
        viewModel(
            factory =
                remember(seriesId, categoryId, providerId) {
                    SeriesDetailsViewModelFactory(context.applicationContext, seriesId, categoryId, seriesName, providerId)
                },
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Child profile (the kids' Home plan, Phase 2): no Category button and no favourite undo.
    // More Like This stays, filtered.
    val child = remember { appSettings.isChildProfile(appSettings.activeProfileId) }
    val relatedTitles by viewModel.relatedTitles.collectAsStateWithLifecycle()
    val tmdbTitle by viewModel.tmdbTitle.collectAsStateWithLifecycle()
    val credits by viewModel.credits.collectAsStateWithLifecycle()
    val logoUrl by viewModel.logoUrl.collectAsStateWithLifecycle()
    val backdropUrl by viewModel.backdropUrl.collectAsStateWithLifecycle()
    val alternateStreams by viewModel.alternateStreams.collectAsStateWithLifecycle()
    val sourceNames by viewModel.sourceNames.collectAsStateWithLifecycle()

    // Kept across a background refresh, so the list stays on screen with a spinning refresh icon
    // and keeps its scroll position and season.
    var lastSuccess by remember { mutableStateOf<SeriesDetailsViewModel.UiState.Success?>(null) }
    (uiState as? SeriesDetailsViewModel.UiState.Success)?.let { lastSuccess = it }
    val isRefreshing = uiState is SeriesDetailsViewModel.UiState.Loading && lastSuccess != null

    val state = uiState
    val shown = lastSuccess
    when {
        state is SeriesDetailsViewModel.UiState.Error -> {
            TvErrorState(
                message = state.message,
                onRetry = { viewModel.loadSeriesInfo() },
                title = stringResource(R.string.series_error_loading),
                onBack = onBack,
                backLabel = stringResource(R.string.series_back_to_list),
                extra = {
                    TvOtherCopiesList(alternateStreams, sourceNames) {
                        if (viewModel.otherSourceOf(it) != null) onRelatedTitleSelected(it) else viewModel.switchToAlternateStream(it)
                    }
                },
            )
        }

        shown != null -> {
            EpisodeListContent(
                seriesDetail = shown.seriesDetail,
                // A child's are filtered like its Search (relatedInSources → forChild).
                relatedTitles = relatedTitles,
                credits = credits,
                tmdbTitle = tmdbTitle,
                logoUrl = logoUrl,
                backdropUrl = backdropUrl,
                alternateStreams = alternateStreams,
                sourceNames = sourceNames,
                seriesName = shown.streamName,
                categoryId = shown.categoryId,
                mediaRepository = viewModel.mediaRepository!!,
                initialEpisodeId = initialEpisodeId,
                initialSeason = initialSeason,
                isFavorite = shown.isFavorite,
                categoryName = shown.categoryName.takeUnless { child },
                isRefreshing = isRefreshing,
                onToggleFavorite = if (child) null else ({ viewModel.toggleFavorite(shown.streamName) }),
                onEpisodeSelected = {
                    id,
                    title,
                    extension,
                    fromStart,
                    ->
                    onEpisodeSelected(id, title, extension, fromStart, viewModel.playedSeries())
                },
                onCategorySelected = { onCategorySelected(shown.categoryId) },
                onRefresh = { viewModel.refreshSeriesInfo() },
                onBack = onBack,
                onRelatedTitleSelected = onRelatedTitleSelected,
                // A copy on another source opens on that source, as a related title does.
                onAlternateStreamSelected = {
                    if (viewModel.otherSourceOf(it) != null) onRelatedTitleSelected(it) else viewModel.switchToAlternateStream(it)
                },
                onPersonSelected = onPersonSelected,
            )
        }

        else -> {
            LoadingScreen()
        }
    }
}

// internal, not private: EpisodeSelectionScreenTest calls it without the ViewModel and DI
// (the episode selection fragility plan).
@Composable
internal fun EpisodeListContent(
    seriesDetail: SeriesDetail,
    relatedTitles: RelatedTitles,
    tmdbTitle: String?,
    logoUrl: String?,
    backdropUrl: String?,
    alternateStreams: List<MediaItem>,
    seriesName: String,
    categoryId: String,
    mediaRepository: MediaRepository,
    initialEpisodeId: String? = null,
    isFavorite: Boolean,
    categoryName: String?,
    isRefreshing: Boolean,
    onToggleFavorite: (() -> Unit)?,
    onEpisodeSelected: (episodeId: String, episodeTitle: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onCategorySelected: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onRelatedTitleSelected: (MediaItem) -> Unit,
    onAlternateStreamSelected: (MediaItem) -> Unit,
    sourceNames: Map<Long, String> = emptyMap(),
    credits: TitleCredits? = null,
    onPersonSelected: (personId: String, name: String, crew: Boolean) -> Unit = { _, _, _ -> },
    initialSeason: Int? = null,
) {
    val context = LocalContext.current
    val playTrailer = rememberPlayTrailer()
    val resources = LocalResources.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    var providerName by remember { mutableStateOf(appSettings.providerName) }
    // AppSettings.providerName is the legacy single-provider key: it stays "My Provider" for every
    // Room provider. Read the name of the show's source from the DB instead, as TwoColumnLayout.kt does.
    LaunchedEffect(mediaRepository) {
        val repo =
            org.njarasoa.fijerena.core.network.provider
                .ProviderRepository(context.applicationContext)
        repo.getProviderById(mediaRepository.getProviderId())?.let { providerName = it.name }
    }
    val listState = rememberLazyListState()
    // This show's new episodes, for the New mark on their cards (the new episode alerts plan).
    val newEpisodeIds = LocalNewEpisodes.current.episodeIds(mediaRepository.getProviderId(), seriesDetail.id)
    val scale = LocalUiScale.current
    // Built once per list, not once per card (CardDefaults calls are @Composable).
    val episodeCardStyle = TvCardLooks.episode()
    val watchedToggleScope = rememberCoroutineScope()

    // The episode whose panel opened a person page from its cast: Back from that page opens the
    // panel again (the navigation round trip drops every plain remember).
    var personReturnEpisodeId by rememberSaveable { mutableStateOf<String?>(null) }

    // Selected episode for the detail panel. Only an explicit tap sets it, except on the way back
    // from a person page opened from the panel.
    var selectedEpisode by remember {
        mutableStateOf(
            personReturnEpisodeId?.let { id ->
                seriesDetail.episodes.values.firstNotNullOfOrNull { list -> list.firstOrNull { it.id == id } }
            },
        )
    }
    // Used once: a later return (from the player) opens on the list again.
    LaunchedEffect(Unit) { personReturnEpisodeId = null }

    // One FocusRequester on the selected tab (the tv detail hero ui plan, Phase 5), as on
    // MovieDetailsScreen: D-pad Down from the action row and Back from inside a section both go to
    // it. Declared before the BackHandlers below that read it.
    val tabRowFocusRequester = remember { FocusRequester() }
    // D-pad Up from a section's topmost focusable goes to the tab row. Default geometry search picks
    // the tab pill nearest on the X axis, not the open tab. Applied only to each section's topmost
    // focusable, so Up between rows inside a section is unchanged.
    val upToTabRow =
        Modifier.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                tabRowFocusRequester.requestFocus()
                true
            } else {
                false
            }
        }
    // True while focus is inside the selected tab's section; the LazyColumn's onPreviewKeyEvent reads
    // it for Back. Only focus-in sets it and only onTabSelected clears it (see MovieDetailsScreen's
    // identical flag for why focus loss is not trusted).
    var focusInSection by remember { mutableStateOf(false) }

    // Opened on an episode or a season (an actor's line, Continue Watching), focus starts on that
    // episode. Until another key is pressed, Back leaves the screen at once, to the control that
    // opened it (focus contract, rule 6), not first to the tab row.
    var backLeavesAtOnce by rememberSaveable { mutableStateOf(initialEpisodeId != null || initialSeason != null) }

    // Back from inside a section goes to the tab row. Deep in the episode list the row is not
    // composed, so focusing it does nothing and Back is lost. Scroll it in first, then focus it.
    val backScope = rememberCoroutineScope()
    val focusTabRow: () -> Unit = {
        backScope.launch {
            listState.scrollToItem(TABS_ITEM_INDEX)
            withFrameNanos { }
            if (!tabRowFocusRequester.requestFocusWithRetry()) onBack()
        }
    }

    // Closing the panel remembers the open episode, so the effect below can focus its row again: the
    // panel replaces the whole LazyColumn, so nothing has focus otherwise.
    var reopenedEpisodeId by remember { mutableStateOf<String?>(null) }
    val reopenedEpisodeFocusRequester = remember { FocusRequester() }
    val closeEpisodePanel: () -> Unit = {
        reopenedEpisodeId = selectedEpisode?.id
        selectedEpisode = null
        personReturnEpisodeId = null
    }

    // Back closes the detail panel first, then leaves. These BackHandlers are only fallbacks: on a
    // Shield a focused TV Button eats the first Back before BackHandler sees it, so the real handling
    // is in the LazyColumn's onPreviewKeyEvent below.
    BackHandler(enabled = selectedEpisode != null) {
        closeEpisodePanel()
    }
    // Fallback while the LazyColumn's onPreviewKeyEvent is not in the tree yet. Same branch as that
    // handler: an unconditional onBack() here would override it.
    BackHandler(enabled = selectedEpisode == null) {
        if (focusInSection && !backLeavesAtOnce) {
            focusTabRow()
        } else {
            onBack()
        }
    }

    // Back from what the Category button or a Similar card opened lands on that control, with its tab
    // section scrolled in first. Back from the player lands on the resume card (effect below). During
    // such a return the resume card's scroll/focus effects stand down, or the later anchor would take
    // focus off the control.
    val returnFocus = rememberNavReturnFocus()
    NavReturnFocusEffect(returnFocus, fallback = tabRowFocusRequester) {
        listState.scrollToItem(TABS_ITEM_INDEX + 1)
    }
    val relatedReturnId = returnFocus.key?.takeIf { it.startsWith(RETURN_RELATED_PREFIX) }?.removePrefix(RETURN_RELATED_PREFIX)

    var targetRotation by remember { mutableStateOf(0f) }

    val rotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = CinemaAnimation.fadeInDurationMs, easing = LinearEasing),
        label = "refresh_rotation",
    )

    // A `while (isRefreshing)` guard would never observe the parameter changing. The effect key
    // does that job: a false value cancels the loop.
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            while (true) {
                targetRotation = (targetRotation + 360f) % 3600f
                kotlinx.coroutines.delay(CinemaAnimation.loadingDebounceMs)
            }
        }
    }

    val sortedSeasons =
        remember(seriesDetail) {
            seriesDetail.sortedSeasons { num -> resources.getString(R.string.series_season_name_format, num) }
        }

    // Sort episodes per season once, not in the LazyColumn on every recomposition.
    val sortedEpisodesBySeason =
        remember(seriesDetail) {
            seriesDetail.episodes.mapValues { (_, eps) -> eps.sortedBy { it.episodeNumber } }
        }
    val totalEpisodes =
        remember(seriesDetail) {
            seriesDetail.episodes.values.sumOf { it.size }
        }
    val hasMultipleSeasons = sortedSeasons.size > 1

    // Tabs are built from what this series has (the tv detail hero ui plan, Phase 5), as on
    // MovieDetailsScreen. No Seasons tab: the season pills on the Episodes tab do the same job.
    // TMDB's credits when it has them (cast tab plan), else the source's cast names (CT-D7).
    val tmdbCredits = credits?.takeUnless { it.isEmpty }
    val hasCast = tmdbCredits != null || !seriesDetail.metadata.cast.isNullOrBlank()
    val hasMoreLikeThis = relatedTitles.moreLikeThis.isNotEmpty()
    val tabs =
        remember(hasCast, hasMoreLikeThis) {
            buildList {
                add(SeriesDetailTab.EPISODES)
                add(SeriesDetailTab.DETAILS)
                if (hasCast) add(SeriesDetailTab.CAST)
                // Last: it loads late, so it shifts no other tab when it appears.
                if (hasMoreLikeThis) add(SeriesDetailTab.MORE_LIKE_THIS)
            }
        }
    // Episodes until a tab is picked: the episode list is this screen's primary content. The tab
    // picked is kept by name: a tab that comes late (Cast, when the credits arrive) moves the
    // others, and an index would then show another tab.
    var selectedTabName by rememberSaveable { mutableStateOf<String?>(null) }
    val safeTabIndex =
        tabs.indexOfFirst { it.name == selectedTabName }.takeIf { it >= 0 }
            ?: tabs.indexOf(SeriesDetailTab.EPISODES).coerceAtLeast(0)
    val tabLabels = tabs.map { seriesDetailTabLabel(it) }

    // The anchored episode/season, and whether the season is the user's pick (see
    // EpisodeResumeState.kt). One season shows at a time, switched by the season tabs; the resume
    // season wins on first load. The route's season counts as a resume season (the episode cast plan).
    val initialResumeSeason =
        initialEpisodeId?.let { seriesDetail.seasonNumberContaining(it) }
            ?: initialSeason?.takeIf { season -> sortedSeasons.any { it.seasonNumber == season } }
    val resumeState =
        rememberEpisodeResumeState(
            seriesId = seriesDetail.id,
            initialEpisodeId = initialEpisodeId,
            initialSeason = initialResumeSeason ?: sortedSeasons.firstOrNull()?.seasonNumber,
            // Only a real resume season counts as "manual" — not the plain "first season"
            // fallback used when there's no resume episode at all.
            initialManualSeason = initialResumeSeason != null,
        )

    val playButtonFocusRequester = remember { FocusRequester() }

    // "More" menu (Refresh info), as on the movie hero. A Dialog window: closing it hands focus
    // back to the More button.
    var showMoreMenu by remember { mutableStateOf(false) }
    var moreMenuOpened by remember { mutableStateOf(false) }
    val moreButtonFocusRequester = remember { FocusRequester() }
    LaunchedEffect(showMoreMenu) {
        if (showMoreMenu) {
            moreMenuOpened = true
        } else if (moreMenuOpened) {
            moreMenuOpened = false
            moreButtonFocusRequester.requestFocusWithRetry(fallback = playButtonFocusRequester)
        }
    }
    if (showMoreMenu) {
        TvActionMenu(
            title = tmdbTitle ?: cleanTitle(seriesDetail.name.ifEmpty { seriesName }),
            actions =
                listOf(
                    MenuAction(icon = CinemaIcons.Refresh, label = stringResource(R.string.series_refresh_info), onClick = onRefresh),
                ),
            onDismiss = { showMoreMenu = false },
        )
    }

    // Focus requester for the stream name row, so switching to an alternate stream can keep
    // focus there instead of it falling back to the window root (see streamSwitchSignal below).
    val streamNameFocusRequester = remember { FocusRequester() }
    // True once the row actually reports itself focused — lets the reassertion loop below stop
    // as soon as it has actually won, instead of guessing how many frames that takes.
    var streamRowFocused by remember { mutableStateOf(false) }
    // Bumped in onSelect: selecting a dropdown item destroys the focused node, so focus falls to the
    // window root and the D-pad does nothing until this claims it back for the row. The resumeEpisodeId
    // effect below also reads it as a sticky "a switch happened" flag: switchToAlternateStream reloads
    // from cache and then from the network, so the anchor can land more than once for one switch.
    // rememberSaveable, because the screen is disposed while the player is open (the episode
    // selection fragility plan).
    var streamSwitchSignal by rememberSaveable { mutableStateOf(0) }

    // Focus target for the resume episode card, so OK plays it at once.
    val resumeCardFocusRequester = remember { FocusRequester() }
    // True until the screen has placed its first focus — see the focus effect below.
    var awaitingFirstFocus by remember { mutableStateOf(true) }

    // Focus target for the season-pill row, the last focusable above the first episode card. Default
    // directional search picks the much taller episode card over the small row, and focusGroup() does
    // not change that. SeasonTabs attaches this to the selected tab.
    val seasonTabsFocusRequester = remember { FocusRequester() }

    // The sticky episodes header (UX overhaul Part II Phase 6, F-E-1/F-E-2). Once the hero has
    // scrolled away it also shows a compact line (show title · season · Play next). Up from the first
    // episode lands here: the selected season tab, else Play next, else the section tab row. Left from
    // an episode opens the TV navigation rail.
    // The line stays while its Play has focus: focusing it can scroll the hero back, and hiding the
    // line would remove the focused button. It shows only once the header is pinned, so its height
    // moves nothing; shown earlier, it moved the focused card and the list shook without end.
    var compactPlayFocused by remember { mutableStateOf(false) }
    val compactHeaderShown by remember {
        derivedStateOf { listState.firstVisibleItemIndex > EPISODES_HEADER_ITEM_INDEX || compactPlayFocused }
    }
    val compactPlayFocusRequester = remember { FocusRequester() }

    // The compact line takes no height in the list: it hangs over the cards under the pinned header.
    // A header that changed height moved the focused card, and the list shook without end.
    var compactLineHeight by remember { mutableIntStateOf(0) }
    val scrollToTabRow: () -> Unit = {
        backScope.launch {
            listState.scrollToItem(TABS_ITEM_INDEX)
            tabRowFocusRequester.requestFocusWithRetry()
        }
    }
    val focusEpisodesHeader: () -> Unit = {
        when {
            hasMultipleSeasons -> seasonTabsFocusRequester.requestFocus()
            compactHeaderShown -> compactPlayFocusRequester.requestFocus()
            else -> scrollToTabRow()
        }
    }

    // Scroll to the resume episode only when it is the reason this season is selected. A manual tab
    // focus or click never moves the list.
    // Items above the episodes: hero, section tabs, pinned episodes header, and the gap under it.
    val headerItemCount = 4

    // The anchor a season or show mark just moved Play to (markEpisodesWatched below): neither
    // this scroll nor the first-focus effect follows it, so focus stays on the action pressed.
    var anchorMovedByMark by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(resumeState.resumeEpisodeId) {
        if (returnFocus.isReturn) return@LaunchedEffect
        val targetId = resumeState.resumeEpisodeId ?: return@LaunchedEffect
        if (targetId == anchorMovedByMark) return@LaunchedEffect
        if (seriesDetail.seasonNumberContaining(targetId) != resumeState.selectedSeason) return@LaunchedEffect
        val seasonEpisodes = sortedEpisodesBySeason[resumeState.selectedSeason?.toString()] ?: return@LaunchedEffect
        val episodeIndex = seasonEpisodes.indexOfFirst { it.id == targetId }
        if (episodeIndex < 0) return@LaunchedEffect
        // A jump, not an animation: during a slow scroll nothing held focus, and the system put it on a
        // season tab, which pulled the list back up.
        listState.scrollToItem(headerItemCount + episodeIndex)
    }

    // Resume fraction per episode, for the card progress bars. Read again on every entry, also on
    // return from the player.
    var episodeProgress by remember(seriesDetail) { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var episodePlaybackPositions by remember(seriesDetail) { mutableStateOf<Map<String, Long>>(emptyMap()) }

    // The route's episode is the resume episode only once it has been played: an actor's episode
    // opens the screen on it, but it is not where the viewer stopped, so it gets no "Continue
    // watching" (the episode cast plan). Read again on each entry, a return from the player too.
    var routeEpisodePlayed by remember { mutableStateOf(false) }
    LaunchedEffect(initialEpisodeId) {
        routeEpisodePlayed = initialEpisodeId != null && wasPlayed(mediaRepository, initialEpisodeId)
    }
    var watchedEpisodeIds by remember(seriesDetail) { mutableStateOf<Set<String>>(emptySet()) }
    // The Wishlist icon and its ask-once prompt (wishlist UX plan, Phase 1): asked when the show
    // is watched to the end, its last episode watched (consistency plan, C-D4). None for a child.
    val wishlistTitle = tmdbTitle ?: cleanTitle(seriesDetail.name.ifEmpty { seriesName })
    val wishlist =
        rememberWishlist(
            contentType = ContentType.TV_SHOWS,
            tmdbId = seriesDetail.metadata.tmdbId,
            title = wishlistTitle,
            year = extractYear(seriesDetail.metadata.year, seriesDetail.metadata.releaseDate, seriesDetail.name),
            watched = seriesDetail.isWatchedToEnd(sortedSeasons, watchedEpisodeIds),
        )
    if (wishlist?.askRemove == true) WishlistRemovePrompt(wishlistTitle, wishlist.answer)

    // Reads progress/watched (with TMDB siblings) again for every episode of this series. Also called
    // after a manual mark: a patch of one episode would miss a sibling the mark completed. Does not
    // change resumeEpisodeId/selectedSeasonNumber. Returns this series' own rows, for
    // markEpisodesWatched's re-anchor.
    suspend fun refreshEpisodeWatchState(): Map<String, WatchedItem> {
        val allEpisodeIds = mutableListOf<String>()
        for (episodes in seriesDetail.episodes.values) {
            for (ep in episodes) {
                allEpisodeIds.add(ep.id)
            }
        }
        val allWatched = mediaRepository.getPlaybackPositionsSuspend(allEpisodeIds, ContentType.TV_SHOWS)
        episodeProgress =
            buildMap {
                for ((id, watched) in allWatched) {
                    watched.resumeProgress()?.let { put(id, it) }
                }
            }
        episodePlaybackPositions =
            buildMap {
                for ((id, watched) in allWatched) {
                    watched.resumeProgress()?.let { put(id, watched.playbackPosition) }
                }
            }
        watchedEpisodeIds =
            buildSet {
                for ((id, watched) in allWatched) {
                    if (watched.isCompleted) add(id)
                }
                addAll(mediaRepository.getSiblingCompletedEpisodeIds(seriesDetail.id))
            }
        return allWatched
    }

    // Mark a whole season (hold OK / Menu on its tab) or the whole show (the hero's action) watched or
    // unwatched (the season show watched plan). Hidden for a source that keeps watched state on its
    // server (Jellyfin). "Unwatched" only when every episode in scope is already watched.
    val canMarkManyWatched = remember(mediaRepository) { mediaRepository.supportsMarkWatched }
    var showAllWatched by remember(seriesDetail) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(seriesDetail, watchedEpisodeIds, canMarkManyWatched) {
        if (canMarkManyWatched) showAllWatched = mediaRepository.episodesWatchState(seriesDetail.id, season = null)
    }
    var markManyError by remember { mutableStateOf<String?>(null) }
    val markEpisodesWatched: (Int?, Boolean) -> Unit = { season, watched ->
        watchedToggleScope.launch {
            mediaRepository
                .setEpisodesWatched(seriesDetail.id, season, watched)
                .onFailure { markManyError = friendlyErrorMessage(it, context, appSettings.isNerdMode) }
            val allWatched = refreshEpisodeWatchState()
            // Play and the "Continue watching" badge move on to the first unwatched episode at once (the
            // watched followups plan, Phase 1); the selected season stays. A single-episode toggle does not
            // come here.
            seriesDetail
                .resumeAnchorEpisodeId(
                    sortedSeasons = sortedSeasons,
                    lastPlayedEpisodeId = resumeState.resumeEpisodeId,
                    isCompleted = { allWatched[it]?.isCompleted == true },
                )?.takeIf { it != resumeState.resumeEpisodeId }
                ?.let {
                    anchorMovedByMark = it
                    resumeState.applyAnchor(episodeId = it, season = null)
                }
        }
    }
    // The season whose menu is open, with whether all its episodes are watched. A Dialog window:
    // closing it hands focus back to the season tab it was opened on.
    var seasonMenu by remember { mutableStateOf<Pair<Int, Boolean?>?>(null) }
    // The episode whose action menu is open (hold OK or Menu on its row).
    var episodeMenu by remember { mutableStateOf<DomainEpisodeItem?>(null) }
    var seasonMenuOpened by remember { mutableStateOf(false) }
    LaunchedEffect(seasonMenu) {
        if (seasonMenu != null) {
            seasonMenuOpened = true
        } else if (seasonMenuOpened) {
            seasonMenuOpened = false
            seasonTabsFocusRequester.requestFocusWithRetry()
        }
    }
    val openSeasonMenu: (Int) -> Unit = { season ->
        watchedToggleScope.launch {
            seasonMenu = season to mediaRepository.episodesWatchState(seriesDetail.id, season)
        }
    }
    seasonMenu?.let { (season, allWatched) ->
        // Hold OK or Menu on a season tab: mark the season watched or unwatched.
        TvActionMenu(
            title = stringResource(R.string.series_season_label, season),
            actions =
                listOf(
                    MenuAction(
                        icon = if (allWatched == true) CinemaIcons.MarkUnwatched else CinemaIcons.MarkWatched,
                        label = stringResource(if (allWatched == true) R.string.watched_unmark_season else R.string.watched_mark_season),
                        onClick = { markEpisodesWatched(season, allWatched != true) },
                    ),
                ),
            onDismiss = { seasonMenu = null },
        )
    }
    episodeMenu?.let { episode ->
        val nowWatched = episode.id in watchedEpisodeIds
        TvActionMenu(
            title =
                episodeOwnTitle(episode.title).ifBlank {
                    stringResource(R.string.series_episode_number_short, episode.episodeNumber)
                },
            actions =
                listOf(
                    MenuAction(
                        icon = if (nowWatched) CinemaIcons.MarkUnwatched else CinemaIcons.MarkWatched,
                        label = stringResource(if (nowWatched) R.string.watched_unmark else R.string.watched_mark),
                        onClick = {
                            // Manual mark (the watch state durable storage plan, Phase 6). Optimistic: flips this badge at
                            // once; the full read after the write catches a TMDB sibling this mark completed (Phase 5) and
                            // restores the resume bar on an unmark.
                            watchedEpisodeIds =
                                if (nowWatched) watchedEpisodeIds - episode.id else watchedEpisodeIds + episode.id
                            watchedToggleScope.launch {
                                mediaRepository.setWatched(episode.id, ContentType.TV_SHOWS, !nowWatched)
                                refreshEpisodeWatchState()
                            }
                        },
                    ),
                ),
            onDismiss = { episodeMenu = null },
        )
    }
    markManyError?.let { message ->
        MarkWatchedErrorDialog(message = message, onDismiss = { markManyError = null })
    }

    // Playback positions for every episode: the progress bars and watched checks above, plus
    // auto-expanding the season holding the next unwatched/in-progress episode.
    LaunchedEffect(seriesDetail) {
        val allEpisodeIds = mutableListOf<String>()
        for (episodes in seriesDetail.episodes.values) {
            for (ep in episodes) {
                allEpisodeIds.add(ep.id)
            }
        }

        val allWatched = mediaRepository.getPlaybackPositionsSuspend(allEpisodeIds, ContentType.TV_SHOWS)

        episodeProgress =
            buildMap {
                for ((id, watched) in allWatched) {
                    watched.resumeProgress()?.let { put(id, it) }
                }
            }
        episodePlaybackPositions =
            buildMap {
                for ((id, watched) in allWatched) {
                    watched.resumeProgress()?.let { put(id, watched.playbackPosition) }
                }
            }
        watchedEpisodeIds =
            buildSet {
                for ((id, watched) in allWatched) {
                    if (watched.isCompleted) add(id)
                }
                // TMDB dedup (Phase 5): completing one language/quality variant of an episode completes them all.
                // Checks only: no resume bar, no anchor move.
                addAll(mediaRepository.getSiblingCompletedEpisodeIds(seriesDetail.id))
            }

        // Anchor on the episode worth watching next. The route (or a play from this screen) names the
        // episode last played; from the series list, use the newest playback timestamp. The anchor moves
        // on when that episode is finished, on every entry, so Back from a completed episode lands on the
        // next one. A row only marked watched has timestamp 0: a mark is not a play.
        val lastPlayed =
            resumeState.resumeEpisodeId
                ?: allWatched.entries
                    .filter { it.value.timestamp > 0L }
                    .maxByOrNull { it.value.timestamp }
                    ?.key
        // An episode from another copy of this show (an alternate stream) is not in this list: find the
        // same season and episode here, not S1E1.
        val lastPlayedHere =
            if (lastPlayed != null && seriesDetail.seasonNumberContaining(lastPlayed) == null) {
                mediaRepository
                    .getPlaybackPositionSuspend(lastPlayed, ContentType.TV_SHOWS)
                    ?.itemName
                    ?.let { seriesDetail.episodeIdMatchingName(it) }
                    ?: lastPlayed
            } else {
                lastPlayed
            }
        seriesDetail
            .resumeAnchorEpisodeId(
                sortedSeasons = sortedSeasons,
                lastPlayedEpisodeId = lastPlayedHere,
                isCompleted = { allWatched[it]?.isCompleted == true },
            )?.let { resumeState.applyAnchor(episodeId = it, season = null) }

        if (!hasMultipleSeasons) {
            // One season: still seed selectedSeason, or the episode list stays keyed on null.
            sortedSeasons.firstOrNull()?.let { resumeState.applyAnchor(episodeId = null, season = it.seasonNumber) }
            return@LaunchedEffect
        }

        // The derived anchor's season beats the "first season with anything unwatched" guess:
        // a viewer mid-season 13 doesn't want season 1 opened because they skipped an episode.
        val targetSeason =
            resumeState.resumeEpisodeId?.let { seriesDetail.seasonNumberContaining(it) }
                ?: firstSeasonWithUnwatchedEpisode(
                    sortedSeasons = sortedSeasons,
                    episodesBySeason = sortedEpisodesBySeason,
                    isCompleted = { allWatched[it]?.isCompleted == true },
                )
        if (targetSeason != null) {
            resumeState.applyAnchor(episodeId = null, season = targetSeason)
        }
    }

    // Flat ordered list of all episodes across seasons, for previous/next.
    val flatEpisodes =
        remember(seriesDetail, sortedSeasons) {
            seriesDetail.flattenedEpisodes(sortedSeasons)
        }

    val anchorEpisode =
        remember(flatEpisodes, resumeState.resumeEpisodeId) {
            flatEpisodes.firstOrNull { it.id == resumeState.resumeEpisodeId } ?: flatEpisodes.firstOrNull()
        }
    val anchorResumePosMs = anchorEpisode?.id?.let { episodePlaybackPositions[it] } ?: 0L
    val hasResume = anchorResumePosMs > 0L

    // The hero's Play and the header's Play next share one label and one action: the episode code and
    // title ("▶ Play: S01E04 · Getting Into Cirque du Soleil", F-E-5). No code when the provider numbers
    // everything 0; the numbers alone when there is no title.
    val anchorOwnTitle = anchorEpisode?.let { episodeOwnTitle(it.title) }?.takeIf { it.isNotBlank() }?.let(::shortEpisodeTitle)
    val anchorTitle =
        if (anchorOwnTitle != null && anchorEpisode.episodeNumber > 0) {
            stringResource(
                R.string.series_episode_code_title_format,
                anchorEpisode.seasonNumber ?: 1,
                anchorEpisode.episodeNumber,
                anchorOwnTitle,
            )
        } else {
            anchorOwnTitle
        }
    val playNextLabel =
        when {
            anchorEpisode == null -> {
                stringResource(R.string.series_play_episode_action)
            }

            hasResume && anchorTitle != null -> {
                stringResource(R.string.series_resume_next_format, anchorTitle, formatTime(anchorResumePosMs))
            }

            hasResume -> {
                stringResource(
                    R.string.series_resume_episode_time_format,
                    anchorEpisode.seasonNumber ?: 1,
                    anchorEpisode.episodeNumber,
                    formatTime(anchorResumePosMs),
                )
            }

            anchorTitle != null -> {
                stringResource(R.string.series_play_next_format, anchorTitle)
            }

            else -> {
                stringResource(R.string.series_play_episode_format, anchorEpisode.seasonNumber ?: 1, anchorEpisode.episodeNumber)
            }
        }
    val playAnchor: (startFromBeginning: Boolean) -> Unit = { fromStart ->
        anchorEpisode?.let { ep ->
            resumeState.setResumeEpisode(ep.id, season = null)
            onEpisodeSelected(ep.id, ep.title, ep.extension ?: "mp4", fromStart)
        }
    }

    // Focus Play when the screen loads or the anchor arrives, unless the user switched to an alternate
    // stream on this screen: then focus stays on the stream name row.
    // With a resume episode, focus the resume card instead of Play. The scroll effect above moves the
    // list to it, and on return from the player the saved scroll offset applies before this effect
    // runs, so the hero (and Play) is not composed or is disposed at once. Compose then gives focus to
    // the first season tab, which selects season 1 (SeasonTab focus-follow-select) and loses the
    // resume season.
    // Opened on an episode further down, the card is not composed until the scroll brings it in, so
    // wait for it to be laid out; if it never is (RESUME_CARD_WAIT_MS), take Play. While waiting, the
    // system puts focus on the first season tab: [awaitingFirstFocus] stops that from selecting its
    // season.
    LaunchedEffect(resumeState.resumeEpisodeId) {
        val movedByMark = anchorMovedByMark != null && resumeState.resumeEpisodeId == anchorMovedByMark
        if (streamSwitchSignal != 0 || returnFocus.isReturn || movedByMark) {
            awaitingFirstFocus = false
        } else {
            val target = resumeState.resumeEpisodeId
            val cardShown =
                target != null &&
                    withTimeoutOrNull(RESUME_CARD_WAIT_MS) {
                        snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key == target } }.first { it }
                    } != null
            if (cardShown) {
                resumeCardFocusRequester.requestFocusWithRetry(fallback = playButtonFocusRequester)
            } else {
                playButtonFocusRequester.requestFocusWithRetry()
            }
            awaitingFirstFocus = false
        }
    }

    LaunchedEffect(streamSwitchSignal) {
        if (streamSwitchSignal == 0) return@LaunchedEffect
        // The dropdown's dismissal focuses the window root asynchronously, later than a fixed frame count
        // on real hardware. Poll, and stop as soon as the row reports focus.
        var attempts = 0
        while (!streamRowFocused && attempts < 90) {
            streamNameFocusRequester.requestFocus()
            withFrameNanos { }
            attempts++
        }
        // Never leave focus on the window root, where the D-pad moves nothing.
        if (!streamRowFocused) playButtonFocusRequester.requestFocusWithRetry()
    }

    // After the episode panel closes, focus the row that was open. Wait a frame for the list to come
    // back, scroll the row in if needed, then request. If the row is not in this season's list
    // (Next/Previous stepped to another season), fall back to the tab row.
    LaunchedEffect(selectedEpisode) {
        val targetId = reopenedEpisodeId
        if (selectedEpisode == null && targetId != null) {
            withFrameNanos { }
            val index = sortedEpisodesBySeason[resumeState.selectedSeason?.toString()]?.indexOfFirst { it.id == targetId } ?: -1
            if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.key == targetId }) {
                listState.scrollToItem(headerItemCount + index)
                withFrameNanos { }
            }
            if (index >= 0) {
                reopenedEpisodeFocusRequester.requestFocusWithRetry(fallback = tabRowFocusRequester)
            } else {
                tabRowFocusRequester.requestFocusWithRetry()
            }
            reopenedEpisodeId = null
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .panel(PanelFill.Ground)
                // Back interception for the detail panel, as for the list below: its focused Buttons eat the first
                // Back before BackHandler sees it, so close here, top-down.
                .then(
                    if (selectedEpisode != null) {
                        Modifier.onPreviewKeyEvent { event ->
                            if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                                closeEpisodePanel()
                                true
                            } else {
                                false
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
    ) {
        if (selectedEpisode != null) {
            val current = selectedEpisode!!
            val currentIdx =
                remember(flatEpisodes, current.id) {
                    flatEpisodes.indexOfFirst { it.id == current.id }
                }
            val previousEpisode = flatEpisodes.getOrNull(currentIdx - 1)
            val nextEpisode = flatEpisodes.getOrNull(currentIdx + 1)
            EpisodeDetailPanel(
                episode = current,
                seriesDetail = seriesDetail,
                seriesName = seriesName,
                categoryId = categoryId,
                providerName = providerName,
                backdropUrl = backdropUrl,
                mediaRepository = mediaRepository,
                previousEpisode = previousEpisode,
                nextEpisode = nextEpisode,
                showCredits = tmdbCredits,
                onPersonSelected = { personId, name, crew ->
                    personReturnEpisodeId = current.id
                    onPersonSelected(personId, name, crew)
                },
                onNavigate = { next -> selectedEpisode = next },
                onPlay = { episodeId, episodeTitle, extension, startFromBeginning ->
                    resumeState.setResumeEpisode(episodeId, season = null)
                    onEpisodeSelected(episodeId, episodeTitle, extension, startFromBeginning)
                },
                onBack = closeEpisodePanel,
            )
        } else {
            KeepHeroInView(scrollToTop = { listState.animateScrollToItem(0) }) { heroModifier ->
                // The episodes header pins only once the list reaches it: without this, focus on
                // episode 2 or 3 shook the list without end.
                IgnorePinnedHeader { listModifier ->
                    LazyColumn(
                        state = listState,
                        // No horizontal margin here: the hero backdrop below must run edge to edge. Every
                        // other item applies Spacing.tvSafeMarginHorizontal to itself instead.
                        contentPadding = PaddingValues(bottom = Spacing.tvSafeMarginVertical.scaled(scale)),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
                        // On a Shield the first Back press while a TV Button has focus never reaches the
                        // BackHandler(selectedEpisode == null) above. onPreviewKeyEvent runs top-down, before the focused
                        // Button, so it always wins. Same pattern as TvDpadEscape.kt.
                        modifier =
                            listModifier.fillMaxSize().testTag("episode_list").onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key != Key.Back) backLeavesAtOnce = false
                                if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                                    // Back from an open tab section goes to the tab row, not out of the screen.
                                    if (focusInSection && !backLeavesAtOnce) {
                                        focusTabRow()
                                    } else {
                                        onBack()
                                    }
                                    true
                                } else {
                                    false
                                }
                            },
                    ) {
                        item(key = "series_hero", contentType = "hero") {
                            val seriesTitleText = tmdbTitle ?: cleanTitle(seriesDetail.name.ifEmpty { seriesName })
                            val presentLabel = stringResource(R.string.series_present)
                            val yearRange = seriesDetail.seriesYearRange(presentLabel)
                            val countText =
                                if (sortedSeasons.size > 1) {
                                    seasonsAndEpisodesText(sortedSeasons.size, totalEpisodes)
                                } else {
                                    pluralStringResource(R.plurals.series_total_episodes_format, totalEpisodes, totalEpisodes)
                                }
                            val metaLine =
                                listOfNotNull(
                                    yearRange,
                                    seriesDetail.metadata.rating?.let { ratingOutOfTen(it) },
                                    seriesDetail.metadata.contentRating,
                                    countText,
                                    seriesDetail.metadata.genre,
                                )

                            TvDetailHero(
                                title = seriesTitleText,
                                backdropUrl = backdropUrl,
                                logoUrl = logoUrl,
                                titleFallback = {
                                    CinemaText(
                                        text = seriesTitleText,
                                        role = TextRole.Display,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                metaLine = metaLine,
                                plot = seriesDetail.metadata.plot,
                                note = if (seriesDetail.isSavedCopy) stringResource(R.string.details_saved_copy_tv) else null,
                                modifier = heroModifier,
                                sideSlot =
                                    anchorEpisode?.let { ep ->
                                        { NextUpCard(episode = ep, progress = episodeProgress[ep.id] ?: 0f) }
                                    },
                            ) {
                                // D-pad Down from any action button goes to the tab row, not where geometry search prefers. Same
                                // fix as MovieDetailsScreen's downToTabRow: focusProperties { down = ... } did not work here.
                                val downToTabRow =
                                    Modifier.onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                            tabRowFocusRequester.requestFocus()
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                // D-pad Up from the hero action row (the topmost focusables) scrolls the list to the top.
                                // bringIntoView keeps only the focused button visible, so after a tall plot or a tab switch the
                                // top of the hero could stay clipped with no way back.
                                val upScrollToTop =
                                    Modifier.onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                            watchedToggleScope.launch { listState.animateScrollToItem(0) }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                CinemaPrimaryButton(
                                    onClick = { playAnchor(false) },
                                    text = playNextLabel,
                                    icon = CinemaIcons.PlayArrow,
                                    modifier =
                                        Modifier
                                            .testTag(
                                                "hero_play_button",
                                            ).focusRequester(playButtonFocusRequester)
                                            .then(downToTabRow)
                                            .then(upScrollToTop)
                                            .leftToRail(),
                                )
                                if (hasResume) {
                                    TvIconAction(
                                        onClick = { playAnchor(true) },
                                        icon = CinemaIcons.Replay,
                                        label = stringResource(R.string.player_osd_start_over),
                                        modifier = downToTabRow.then(upScrollToTop),
                                    )
                                }
                                if (onToggleFavorite != null) {
                                    TvIconAction(
                                        onClick = onToggleFavorite,
                                        icon = if (isFavorite) CinemaIcons.Star else CinemaIcons.StarBorder,
                                        iconTone = if (isFavorite) TextTone.Accent else TextTone.Primary,
                                        label = stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add),
                                        modifier = downToTabRow.then(upScrollToTop),
                                    )
                                }
                                // Same order as the film hero: Play, Start over, Favourite, Watched, Wishlist, Trailer, More.
                                if (canMarkManyWatched) {
                                    val allWatched = showAllWatched == true
                                    TvIconAction(
                                        onClick = { markEpisodesWatched(null, !allWatched) },
                                        icon = if (allWatched) CinemaIcons.CheckCircleFilled else CinemaIcons.CheckCircleOutline,
                                        iconTone = if (allWatched) TextTone.Accent else TextTone.Primary,
                                        label =
                                            stringResource(
                                                if (allWatched) R.string.watched_unmark_show else R.string.watched_mark_show,
                                            ),
                                        modifier = downToTabRow.then(upScrollToTop),
                                    )
                                }
                                if (wishlist != null) {
                                    TvIconAction(
                                        onClick = wishlist.toggle,
                                        icon = if (wishlist.isOn) CinemaIcons.Bookmark else CinemaIcons.BookmarkBorder,
                                        iconTone = if (wishlist.isOn) TextTone.Accent else TextTone.Primary,
                                        label = stringResource(if (wishlist.isOn) R.string.wanted_remove else R.string.wanted_add),
                                        modifier = downToTabRow.then(upScrollToTop),
                                    )
                                }
                                seriesDetail.metadata.trailerUrl?.let { trailer ->
                                    TvIconAction(
                                        onClick = { playTrailer(trailer, seriesDetail.metadata.tmdbTrailerUrl) },
                                        icon = CinemaIcons.Movie,
                                        label = stringResource(R.string.details_watch_trailer_description),
                                        modifier = downToTabRow.then(upScrollToTop),
                                    )
                                }
                                // Refresh info is maintenance: it is behind "More" at the end of the row, as on the movie hero
                                // (Phase 6). During a refresh the button shows a spinning Refresh glyph.
                                TvIconAction(
                                    onClick = { showMoreMenu = true },
                                    icon = if (isRefreshing) CinemaIcons.Refresh else CinemaIcons.MoreVert,
                                    label = stringResource(R.string.details_action_more),
                                    modifier = downToTabRow.then(upScrollToTop).focusRequester(moreButtonFocusRequester),
                                    iconModifier = if (isRefreshing) Modifier.rotate(rotation) else Modifier,
                                )
                            }
                        }

                        item(key = "tabs") {
                            TvSectionTabs(
                                tabs = tabLabels,
                                selectedIndex = safeTabIndex,
                                onTabSelected = {
                                    selectedTabName = tabs.getOrNull(it)?.name
                                    // A tab that gets focus (Left/Right, Down from the action row, or our own Back request) means
                                    // focus is on the tab row, not in a section.
                                    focusInSection = false
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Spacing.tvSafeMarginHorizontal.scaled(scale))
                                        .padding(top = Spacing.xl.scaled(scale))
                                        // D-pad Down from the Episodes tab goes straight to the selected season pill, not through the
                                        // Row's default focus-group entry, which is unreliable here. Each SeasonTab focus-follow-selects,
                                        // so landing on Season 1 for one frame would overwrite the resume anchor's season.
                                        .onPreviewKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyDown &&
                                                event.key == Key.DirectionDown &&
                                                tabs.getOrNull(safeTabIndex) == SeriesDetailTab.EPISODES &&
                                                hasMultipleSeasons
                                            ) {
                                                seasonTabsFocusRequester.requestFocus()
                                                true
                                            } else {
                                                false
                                            }
                                        },
                                entryFocusRequester = tabRowFocusRequester,
                                firstTabModifier = Modifier.leftToRail(),
                            )
                        }

                        // Only the selected tab's section composes (as on MovieDetailsScreen). Episodes emits its
                        // stickyHeader and episode items directly into this LazyColumn, not inside one Box item, so the
                        // episode cards stay lazy.
                        when (tabs.getOrNull(safeTabIndex)) {
                            SeriesDetailTab.EPISODES -> {
                                // The episodes header, pinned while the episode list scrolls under it: season tabs when there are
                                // several (focus moves select, see SeasonTab), and, once the hero is off screen, a compact
                                // "title · season · Play next" line above them (Phase 6). Up from the first episode comes here.
                                stickyHeader(key = "season_tabs", contentType = "header") {
                                    Column(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .layout { measurable, constraints ->
                                                    val placeable = measurable.measure(constraints)
                                                    val compactLine = if (compactHeaderShown) compactLineHeight else 0
                                                    layout(placeable.width, placeable.height - compactLine) { placeable.place(0, 0) }
                                                }
                                                // Opaque, top padding included: cards scroll in underneath.
                                                .panel(PanelFill.Ground)
                                                .padding(horizontal = Spacing.tvSafeMarginHorizontal.scaled(scale))
                                                .padding(top = Spacing.md.scaled(scale))
                                                .onFocusChanged { if (it.hasFocus) focusInSection = true },
                                    ) {
                                        if (compactHeaderShown) {
                                            CompactEpisodesHeader(
                                                modifier = Modifier.onSizeChanged { compactLineHeight = it.height },
                                                title =
                                                    listOfNotNull(
                                                        tmdbTitle ?: cleanTitle(seriesDetail.name.ifEmpty { seriesName }),
                                                        resumeState.selectedSeason?.let {
                                                            stringResource(
                                                                R.string.series_season_label,
                                                                it,
                                                            )
                                                        },
                                                    ).joinToString(" · "),
                                                playLabel = playNextLabel,
                                                onPlay = { playAnchor(false) },
                                                playModifier =
                                                    Modifier
                                                        .focusRequester(compactPlayFocusRequester)
                                                        .onFocusChanged { compactPlayFocused = it.isFocused }
                                                        .onPreviewKeyEvent { event ->
                                                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                                            when {
                                                                event.key == Key.DirectionUp -> {
                                                                    scrollToTabRow()
                                                                    true
                                                                }

                                                                event.key == Key.DirectionDown && hasMultipleSeasons -> {
                                                                    seasonTabsFocusRequester.requestFocus()
                                                                    true
                                                                }

                                                                else -> {
                                                                    false
                                                                }
                                                            }
                                                        }.leftToRail(),
                                            )
                                        }
                                        if (hasMultipleSeasons) {
                                            Box(
                                                // Up from a season tab: Play next when the compact line is
                                                // showing, else the section tab row (on screen with the hero).
                                                modifier =
                                                    Modifier.onPreviewKeyEvent { event ->
                                                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                                            if (compactHeaderShown) {
                                                                compactPlayFocusRequester.requestFocus()
                                                            } else {
                                                                tabRowFocusRequester.requestFocus()
                                                            }
                                                            true
                                                        } else {
                                                            false
                                                        }
                                                    },
                                            ) {
                                                SeasonTabs(
                                                    seasons = sortedSeasons,
                                                    selectedSeason = resumeState.selectedSeason,
                                                    onSeasonSelected = {
                                                        // Skip the call while the first focus is pending. EpisodeResumeState.selectSeason is a no-op for
                                                        // the selected season: focus entering the row fires this for the selected tab, and a state write
                                                        // then recomposed the row in the middle of the focus search, so the first Up/Down missed the tabs.
                                                        if (!awaitingFirstFocus) resumeState.selectSeason(it)
                                                    },
                                                    onSeasonLongPress = if (canMarkManyWatched) openSeasonMenu else null,
                                                    entryFocusRequester = seasonTabsFocusRequester,
                                                )
                                            }
                                        }
                                    }
                                }
                                // Gap before the episode cards as its own non-sticky item: inside the stickyHeader it would pin
                                // with the header and show cards through its unbacked height.
                                item(key = "season_tabs_gap", contentType = "spacer") {
                                    Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                                }

                                val currentSeasonEpisodes = sortedEpisodesBySeason[resumeState.selectedSeason?.toString()] ?: emptyList()
                                itemsIndexed(currentSeasonEpisodes, key = {
                                    _,
                                    episode,
                                    ->
                                    episode.id
                                }, contentType = { _, _ -> "episode" }) { index, episode ->
                                    val isResumeAnchor = episode.id == resumeState.resumeEpisodeId
                                    EpisodeCard(
                                        episode = episode,
                                        cardStyle = episodeCardStyle,
                                        isContinueWatching = isResumeAnchor && (episode.id != initialEpisodeId || routeEpisodePlayed),
                                        watchProgress = episodeProgress[episode.id] ?: 0f,
                                        isWatched = episode.id in watchedEpisodeIds,
                                        isNew = episode.id in newEpisodeIds,
                                        focusRequester = if (isResumeAnchor) resumeCardFocusRequester else null,
                                        modifier =
                                            Modifier
                                                .onPreviewKeyEvent { event ->
                                                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                                    when {
                                                        // Up from the first episode goes to the episodes header in one press (Phase 6, F-E-2), by explicit
                                                        // request: plain focus search picked the wrong season pill and changed season. Left goes to the
                                                        // rail (leftToRail below): every episode row starts at the screen's left edge.
                                                        index == 0 && event.key == Key.DirectionUp -> {
                                                            focusEpisodesHeader()
                                                            true
                                                        }

                                                        // Right stays put: arrows move focus, they do not change season.
                                                        event.key == Key.DirectionRight -> {
                                                            true
                                                        }

                                                        else -> {
                                                            false
                                                        }
                                                    }
                                                }.padding(horizontal = Spacing.tvSafeMarginHorizontal.scaled(scale))
                                                .onFocusChanged { if (it.hasFocus) focusInSection = true }
                                                .then(
                                                    if (episode.id == reopenedEpisodeId) {
                                                        Modifier.focusRequester(reopenedEpisodeFocusRequester)
                                                    } else {
                                                        Modifier
                                                    },
                                                ).testTag("episode_${episode.id}")
                                                .leftToRail(),
                                        onClick = {
                                            selectedEpisode = episode
                                        },
                                        // Hold OK or Menu: the episode's action menu. Not where the source keeps watched state on its
                                        // server (Jellyfin).
                                        onLongPress =
                                            if (mediaRepository.supportsMarkWatched) {
                                                { episodeMenu = episode }
                                            } else {
                                                null
                                            },
                                    )
                                }
                            }

                            SeriesDetailTab.CAST -> {
                                item(key = "tab-section-cast") {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = Spacing.tvSafeMarginHorizontal.scaled(scale))
                                                .padding(top = Spacing.md.scaled(scale))
                                                .focusRestorer()
                                                .onFocusChanged { if (it.hasFocus) focusInSection = true },
                                    ) {
                                        if (tmdbCredits != null) {
                                            TvCreditsTabContent(
                                                credits = tmdbCredits,
                                                section = ThumbnailContentType.TV_SHOW,
                                                returnFocus = returnFocus,
                                                onPersonSelected = onPersonSelected,
                                                topFocusModifier = upToTabRow,
                                            )
                                        } else {
                                            SeriesCastTabContent(cast = seriesDetail.metadata.cast.orEmpty())
                                        }
                                    }
                                }
                            }

                            SeriesDetailTab.DETAILS -> {
                                item(key = "tab-section-details") {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = Spacing.tvSafeMarginHorizontal.scaled(scale))
                                                .padding(top = Spacing.md.scaled(scale))
                                                .focusRestorer()
                                                .onFocusChanged { if (it.hasFocus) focusInSection = true },
                                    ) {
                                        SeriesDetailsTabContent(
                                            seriesDetail = seriesDetail,
                                            seriesName = seriesName,
                                            providerName = providerName,
                                            categoryName = categoryName,
                                            alternateStreams = alternateStreams,
                                            sourceNames = sourceNames,
                                            streamNameFocusRequester = streamNameFocusRequester,
                                            onStreamSelected = {
                                                streamSwitchSignal++
                                                onAlternateStreamSelected(it)
                                            },
                                            onStreamFocusedChanged = { streamRowFocused = it },
                                            onCategorySelected = {
                                                returnFocus.leaveFrom(RETURN_CATEGORY)
                                                onCategorySelected()
                                            },
                                            topFocusModifier = upToTabRow,
                                            categoryButtonFocusRequester = returnFocus.requesterFor(RETURN_CATEGORY),
                                        )
                                    }
                                }
                            }

                            SeriesDetailTab.MORE_LIKE_THIS -> {
                                item(key = "tab-section-similar") {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(top = Spacing.md.scaled(scale))
                                                .focusRestorer()
                                                .then(upToTabRow)
                                                .onFocusChanged { if (it.hasFocus) focusInSection = true },
                                    ) {
                                        // No heading: the More Like This tab above already says what this row is.
                                        RelatedTitlesRow(
                                            title = null,
                                            items = relatedTitles.moreLikeThis,
                                            onItemClick = { item ->
                                                returnFocus.leaveFrom(RETURN_RELATED_PREFIX + item.id)
                                                onRelatedTitleSelected(item)
                                            },
                                            focusTargetId = relatedReturnId,
                                            focusTargetModifier =
                                                Modifier.navReturnFocusTarget(returnFocus, RETURN_RELATED_PREFIX + relatedReturnId),
                                            modifier = Modifier.padding(horizontal = Spacing.tvSafeMarginHorizontal.scaled(scale)),
                                        )
                                    }
                                }
                            }

                            null -> {
                                Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Direction the viewer stepped through episodes in, so focus can stay on that button. */
private enum class EpisodeStep { PREVIOUS, NEXT }

/** Phase 5 tab shell (the tv detail hero ui plan) — mirrors MovieDetailTab. */
private enum class SeriesDetailTab { EPISODES, DETAILS, CAST, MORE_LIKE_THIS }

@Composable
private fun seriesDetailTabLabel(tab: SeriesDetailTab): String =
    when (tab) {
        SeriesDetailTab.EPISODES -> stringResource(R.string.series_episodes_header)
        SeriesDetailTab.CAST -> stringResource(R.string.details_tab_cast)
        SeriesDetailTab.DETAILS -> stringResource(R.string.details_tab_details)
        SeriesDetailTab.MORE_LIKE_THIS -> stringResource(R.string.details_more_like_this)
    }

/** Cast tab without TMDB's credits (CT-D7): the source's comma string split into plain chips, not openable. */
@Composable
private fun SeriesCastTabContent(cast: String) {
    val scale = LocalUiScale.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
    ) {
        cast.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { member ->
            CinemaBadge(
                text = member,
                role = TextRole.Body,
            )
        }
    }
}

/**
 * Details tab: tagline and full synopsis, then one label/value row each — director, the title
 * facts, the stream-name picker, TMDB id and the source last — then the category button. Cast lives in its own
 * tab ([SeriesCastTabContent]), not repeated here.
 */
@Composable
private fun SeriesDetailsTabContent(
    seriesDetail: SeriesDetail,
    seriesName: String,
    providerName: String,
    categoryName: String?,
    alternateStreams: List<MediaItem>,
    sourceNames: Map<Long, String>,
    streamNameFocusRequester: FocusRequester,
    onStreamSelected: (MediaItem) -> Unit,
    onStreamFocusedChanged: (Boolean) -> Unit,
    onCategorySelected: () -> Unit,
    // D-pad Up from this section's topmost focusable goes to the tab row (see `upToTabRow`). The picker
    // is normally that row, but with no alternate streams it is plain Text, so the Category button is
    // the top.
    topFocusModifier: Modifier = Modifier,
    categoryButtonFocusRequester: FocusRequester? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        seriesDetail.metadata.facts.tagline
            ?.let { CinemaText(text = it, tone = TextTone.Secondary) }
        seriesDetail.metadata.plot?.takeIf { it.isNotBlank() }?.let { plot ->
            CinemaText(text = plot)
            Spacer(modifier = Modifier.height(Spacing.sm))
        }
        // Above the picker, as on the film page: focusing the picker brings every row into view;
        // a row below the last focusable one could never be scrolled to.
        seriesDetail.metadata.director?.let { TvDetailRow(label = stringResource(R.string.details_label_director), value = it) }
        titleFactRows(seriesDetail.metadata.facts, seriesDetail.name.ifBlank { seriesName }, isShow = true).forEach {
            TvDetailRow(label = it.label, value = it.value)
        }
        // A child's page has no technical rows (UX-D5).
        if (!rememberChildProfile()) {
            TvDetailRow(label = stringResource(R.string.details_label_stream_name)) {
                StreamNamePicker(
                    currentName = seriesName,
                    alternates = alternateStreams,
                    sourceNames = sourceNames,
                    onSelect = onStreamSelected,
                    focusRequester = streamNameFocusRequester,
                    onFocusedChanged = onStreamFocusedChanged,
                    modifier = topFocusModifier.leftToRail(),
                )
            }
        }
        if (LocalNerd.current) {
            TvDetailRow(
                label = stringResource(R.string.details_label_tmdb),
                value = seriesDetail.metadata.tmdbId ?: stringResource(R.string.details_tmdb_none),
            )
        }
        // For a Nerd only, like the source badges.
        if (LocalNerd.current) TvDetailRow(label = stringResource(R.string.details_label_source), value = providerName)

        if (categoryName != null) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            CinemaSecondaryButton(
                onClick = onCategorySelected,
                text = stringResource(R.string.details_category_format, categoryName),
                // Only the actual top of the section forwards Up to the tab row — with no
                // alternates, the picker above is plain Text and this button is it instead.
                modifier =
                    (if (alternateStreams.isEmpty()) topFocusModifier else Modifier)
                        .then(categoryButtonFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                        .leftToRail(),
            )
        }
    }
}

/**
 * The hero's top-right "Next Up" note (Phase 5, the tv detail hero ui plan): the same
 * continue-watching state as the resume card and progress bars. Plain text, not focusable (UX
 * overhaul Part II Phase 6, F-E-6): Play, one press away, plays the same episode. Drawn on the
 * hero's side scrim with a halo to stay legible; one line each, cut with "…".
 */
@Composable
private fun NextUpCard(
    episode: DomainEpisodeItem,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val scale = LocalUiScale.current
    Column(
        modifier = modifier.width(TvDimensions.heroSideSlotWidth.scaled(scale)),
        horizontalAlignment = Alignment.End,
    ) {
        TvBackdropText(
            text = stringResource(R.string.series_next_up_title),
            role = TextRole.Label,
            tone = TextTone.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
        val label =
            listOfNotNull(
                episode.seasonNumber?.let { stringResource(R.string.series_season_label, it) },
                stringResource(R.string.series_episode_label, episode.episodeNumber),
            ).joinToString(" · ")
        TvBackdropText(text = label, role = TextRole.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
        val ownTitle = episodeOwnTitle(episode.title)
        if (ownTitle.isNotBlank()) {
            TvBackdropText(
                text = ownTitle,
                role = TextRole.Heading,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
        }
        if (progress > 0f) {
            // Remaining time, not elapsed: it tells whether the episode is worth starting now.
            val remainingText =
                remember(episode.metadata.duration, progress) {
                    val totalSecs = episode.metadata.duration?.let { parseDurationToSeconds(it) } ?: return@remember null
                    val remainingSecs = (totalSecs * (1f - progress)).toLong().coerceAtLeast(0)
                    if (remainingSecs <= 0) null else formatDuration(remainingSecs.toString())
                }
            remainingText?.let {
                Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
                TvBackdropText(
                    text = stringResource(R.string.series_remaining_format, it),
                    role = TextRole.Caption,
                    tone = TextTone.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The compact line of the episodes header, shown once the hero has scrolled away (UX overhaul
 * Part II Phase 6, F-E-1): "show title · season" and Play next, so an episode deep in the list
 * keeps its context and the next episode stays one press away.
 */
@Composable
private fun CompactEpisodesHeader(
    title: String,
    playLabel: String,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    playModifier: Modifier = Modifier,
) {
    val scale = LocalUiScale.current
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = Spacing.xs.scaled(scale)),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CinemaText(
            text = title,
            role = TextRole.Heading,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        // Main, as the hero's Play: one kind for the page's one main action.
        CinemaPrimaryButton(onClick = onPlay, text = playLabel, modifier = playModifier, icon = CinemaIcons.PlayArrow)
    }
}

/** An episode title short enough for a button label: a long provider title is cut with an ellipsis. */
private fun shortEpisodeTitle(title: String): String =
    if (title.length <= PLAY_LABEL_TITLE_MAX) title else title.take(PLAY_LABEL_TITLE_MAX - 1).trimEnd() + "…"

private const val PLAY_LABEL_TITLE_MAX = 40

/**
 * The stream name, the value of the Details tab's "Stream name" row. Plain text when [alternates]
 * is empty. Otherwise a picker of the other catalogue entries with the same TMDB id, which opens
 * that entry's detail screen.
 */
@Composable
private fun StreamNamePicker(
    currentName: String,
    alternates: List<MediaItem>,
    sourceNames: Map<Long, String>,
    onSelect: (MediaItem) -> Unit,
    focusRequester: FocusRequester,
    onFocusedChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (alternates.isEmpty()) {
        // The same font and place as the other values of the Details tab (TvDetailRow).
        CinemaText(text = currentName)
        return
    }

    var expanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .testTag("stream_name_picker")
                    .pickerFocus(isFocused)
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        isFocused = it.isFocused
                        onFocusedChanged(it.isFocused)
                        // look-ok: a focusable picker or a season tab chip, not a list row
                    }.clickable {
                        expanded = true
                    },
        ) {
            // The value's own font and start, as the other rows; the focus outline hugs it.
            CinemaText(text = currentName)
            // The "opens a picker" mark of every row (Settings' ›).
            TvIcon(
                imageVector = CinemaIcons.KeyboardArrowRight,
                contentDescription = stringResource(R.string.details_other_instances),
                tone = TextTone.Primary,
            )
        }
        if (expanded) {
            // The copies of this title: this one marked, each other one opens there.
            TvPickerDialog(
                title = stringResource(R.string.details_other_instances),
                options = listOf(currentName to null) + alternates.map { it.name to it },
                selected = null,
                onSelect = { alternate -> alternate?.let(onSelect) },
                onDismiss = { expanded = false },
                // With several sources, each copy's own as the badge at the end of its name (it
                // opens there), as on every row.
                sourceName = { alternate -> alternate?.let { sourceNames[it.providerId] } },
                labelIsName = true,
                labelMaxLines = 2,
            )
        }
    }
}

@Composable
private fun EpisodeDetailPanel(
    episode: DomainEpisodeItem,
    seriesDetail: SeriesDetail,
    seriesName: String,
    categoryId: String,
    providerName: String,
    backdropUrl: String?,
    mediaRepository: MediaRepository,
    previousEpisode: DomainEpisodeItem?,
    nextEpisode: DomainEpisodeItem?,
    // The show's TMDB cast (the Cast tab's), after the episode's guest stars.
    showCredits: TitleCredits?,
    onPersonSelected: (personId: String, name: String, crew: Boolean) -> Unit,
    onNavigate: (DomainEpisodeItem) -> Unit,
    onPlay: (episodeId: String, episodeTitle: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val extension = episode.extension ?: "mp4"
    val scale = LocalUiScale.current

    val playButtonFocusRequester = remember { FocusRequester() }

    // Back from a person page opened from the cast: focus goes back to that actor's card, and the
    // panel's own first focus (Play) stands down for the episode it was opened on.
    val returnFocus = rememberNavReturnFocus()
    val returnedToEpisode = remember { episode.id.takeIf { returnFocus.isReturn } }
    NavReturnFocusEffect(returnFocus, fallback = playButtonFocusRequester)
    val episodeCredits = rememberEpisodeCredits(mediaRepository, seriesDetail.metadata.tmdbId, episode, showCredits)

    // Resume position. Keyed on the episode, so stepping to another one clears the old value at once;
    // the lookup always assigns, so an episode with nothing to resume resets it.
    var resumePositionMs by remember(episode.id) { mutableStateOf(0L) }

    LaunchedEffect(episode.id) {
        val watched = mediaRepository.getPlaybackPositionSuspend(episode.id, ContentType.TV_SHOWS)
        resumePositionMs = watched?.resumeProgress()?.let { watched.playbackPosition } ?: 0L
    }

    // The step button that moved us here, so focus stays on Next/Previous while stepping through
    // episodes. Reset when the panel closes, so an episode opened from the list starts on Play.
    var arrivedVia by remember { mutableStateOf<EpisodeStep?>(null) }
    val previousButtonFocusRequester = remember { FocusRequester() }
    val nextButtonFocusRequester = remember { FocusRequester() }

    // Focus Play when the panel loads, resume data arrives or the episode changes, or the step button
    // just used when there is still an episode that way.
    LaunchedEffect(episode.id, resumePositionMs) {
        if (episode.id == returnedToEpisode) return@LaunchedEffect
        val target =
            when {
                arrivedVia == EpisodeStep.PREVIOUS && previousEpisode != null -> previousButtonFocusRequester
                arrivedVia == EpisodeStep.NEXT && nextEpisode != null -> nextButtonFocusRequester
                else -> playButtonFocusRequester
            }
        target.requestFocusWithRetry(fallback = playButtonFocusRequester)
    }

    val hasResume = resumePositionMs > 0L
    val seasonEpisodeLabel =
        listOfNotNull(
            episode.seasonNumber?.let { stringResource(R.string.series_season_label, it) },
            stringResource(R.string.series_episode_label, episode.episodeNumber),
        ).joinToString(" · ")
    val contentRating = episode.metadata.contentRating ?: seriesDetail.metadata.contentRating
    val endsAtContext = LocalContext.current
    val endsAtText =
        remember(episode.metadata.duration, resumePositionMs) {
            computeEndsAt(endsAtContext, episode.metadata.duration, resumePositionMs)
        }
    val metaLine =
        listOfNotNull(
            seasonEpisodeLabel.ifBlank { null },
            (episode.metadata.rating ?: seriesDetail.metadata.rating)?.let { ratingOutOfTen(it) },
            contentRating,
            episode.metadata.duration
                ?.takeIf(::hasMeaningfulDuration)
                ?.let { formatDuration(it) },
            endsAtText?.let { stringResource(R.string.movie_ends_at_format, it) },
            seriesDetail.metadata.genre,
        )

    val panelScroll = rememberScrollState()
    KeepHeroInView(scrollToTop = { panelScroll.animateScrollTo(0) }) { heroModifier ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.MediaNext -> {
                                nextEpisode?.let {
                                    arrivedVia = EpisodeStep.NEXT
                                    onNavigate(it)
                                    true
                                } ?: false
                            }

                            Key.MediaPrevious -> {
                                previousEpisode?.let {
                                    arrivedVia = EpisodeStep.PREVIOUS
                                    onNavigate(it)
                                    true
                                } ?: false
                            }

                            else -> {
                                false
                            }
                        }
                    }.verticalScroll(panelScroll)
                    .focusable(),
        ) {
            // Full-bleed series backdrop: an episode has no backdrop of its own (the tv detail hero ui plan,
            // Phase 5). The provider's title often only repeats the show and the number ("EN - Show - S01E22").
            val heroTitle = episodeOwnTitle(episode.title).ifBlank { stringResource(R.string.series_episode_label, episode.episodeNumber) }
            TvDetailHero(
                title = heroTitle,
                backdropUrl = backdropUrl,
                logoUrl = null,
                titleFallback = {
                    CinemaText(text = heroTitle, role = TextRole.Display, maxLines = 2, overflow = TextOverflow.Ellipsis)
                },
                metaLine = metaLine,
                plot = episode.metadata.plot,
                modifier = heroModifier,
            ) {
                if (hasResume) {
                    val resumeTimeText = formatTime(resumePositionMs)
                    CinemaPrimaryButton(
                        onClick = { onPlay(episode.id, episode.title, extension, false) },
                        text = stringResource(R.string.movie_resume_from_format, resumeTimeText),
                        icon = CinemaIcons.PlayArrow,
                        modifier = Modifier.focusRequester(playButtonFocusRequester).leftToRail(),
                    )
                    CinemaIconButton(
                        onClick = { onPlay(episode.id, episode.title, extension, true) },
                        icon = {
                            TvIcon(
                                imageVector = CinemaIcons.Replay,
                                contentDescription = stringResource(R.string.player_osd_start_over),
                                modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                            )
                        },
                    )
                } else {
                    CinemaPrimaryButton(
                        onClick = { onPlay(episode.id, episode.title, extension, false) },
                        text = stringResource(R.string.series_play_episode_action),
                        icon = CinemaIcons.PlayArrow,
                        modifier = Modifier.focusRequester(playButtonFocusRequester).leftToRail(),
                    )
                }
                // Step to the next or previous episode on this screen. Buttons, because the Shield and Bravia
                // remotes have no transport keys; the key handler above still serves remotes that have them.
                previousEpisode?.let {
                    CinemaIconButton(
                        onClick = {
                            arrivedVia = EpisodeStep.PREVIOUS
                            onNavigate(it)
                        },
                        icon = {
                            TvIcon(
                                imageVector = CinemaIcons.SkipPrevious,
                                contentDescription = stringResource(R.string.player_prev_episode),
                                modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                            )
                        },
                        modifier = Modifier.focusRequester(previousButtonFocusRequester),
                    )
                }
                nextEpisode?.let {
                    CinemaIconButton(
                        onClick = {
                            arrivedVia = EpisodeStep.NEXT
                            onNavigate(it)
                        },
                        icon = {
                            TvIcon(
                                imageVector = CinemaIcons.SkipNext,
                                contentDescription = stringResource(R.string.player_next_episode),
                                modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                            )
                        },
                        modifier = Modifier.focusRequester(nextButtonFocusRequester),
                    )
                }
                // The trailer is the show's, not this episode's — Xtream and Jellyfin only ever
                // carry one per series.
                seriesDetail.metadata.trailerUrl?.let { trailer ->
                    val playTrailer = rememberPlayTrailer()
                    CinemaIconButton(
                        onClick = { playTrailer(trailer, seriesDetail.metadata.tmdbTrailerUrl) },
                        icon = {
                            TvIcon(
                                imageVector = CinemaIcons.Movie,
                                contentDescription = stringResource(R.string.details_watch_trailer_description),
                                modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                            )
                        },
                    )
                }
            }

            // Diagnostics, not headline facts. A plain column, like the series details block below its hero.
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.tvSafeMarginHorizontal.scaled(scale))
                        .padding(top = Spacing.xl.scaled(scale), bottom = Spacing.tvSafeMarginVertical.scaled(scale)),
            ) {
                // The episode's cast (the episode cast plan, EC-D1): its guest stars and the show's
                // cast, each under its own heading, as the Cast tab's cards; without TMDB's, the
                // source's names as the tab's chips.
                val sourceCast = (episode.metadata.cast ?: seriesDetail.metadata.cast)?.takeIf { it.isNotBlank() }
                // Each episode's own cards: Next and Previous start its rows at the first actor.
                key(episode.id) {
                    if (episodeCredits != null) {
                        listOfNotNull(
                            episodeCredits.guests?.let { R.string.episode_guest_stars to it },
                            episodeCredits.regulars?.let { R.string.episode_series_cast to it },
                        ).forEach { (heading, credits) ->
                            CinemaText(text = stringResource(heading), role = TextRole.Heading)
                            TvCreditsTabContent(
                                credits = credits,
                                section = ThumbnailContentType.TV_SHOW,
                                returnFocus = returnFocus,
                                onPersonSelected = onPersonSelected,
                            )
                            Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                        }
                    } else if (sourceCast != null) {
                        CinemaText(text = stringResource(R.string.details_tab_cast), role = TextRole.Heading)
                        SeriesCastTabContent(cast = sourceCast)
                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                    }
                }

                // A child's page has no technical rows: TMDB id, container, bitrate (UX-D5).
                val child = rememberChildProfile()
                if (!child) {
                    CinemaText(
                        text =
                            stringResource(
                                R.string.details_tmdb_format,
                                episode.metadata.tmdbId ?: seriesDetail.metadata.tmdbId ?: stringResource(R.string.details_tmdb_none),
                            ),
                        tone = TextTone.Secondary,
                    )
                }

                val director = episode.metadata.director ?: seriesDetail.metadata.director
                director?.let {
                    Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                    CinemaText(
                        text = stringResource(R.string.movie_director_format, it),
                        tone = TextTone.Secondary,
                    )
                }

                episode.extension?.takeIf { it.isNotBlank() && !child }?.let { ext ->
                    Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                    CinemaText(
                        text = "${stringResource(R.string.tech_container_label)} ${ext.uppercase()}",
                        tone = TextTone.Secondary,
                    )
                }

                episode.metadata.airDate?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                    CinemaText(
                        text = stringResource(R.string.series_aired_format, displayDate(it)),
                        tone = TextTone.Secondary,
                    )
                }

                episode.metadata.bitrate?.takeIf { it > 0 && !child }?.let {
                    Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                    CinemaText(
                        text = stringResource(R.string.series_bitrate_format, it),
                        tone = TextTone.Secondary,
                    )
                }

                // The source last and labelled: alone at the top it read as a heading (TV UI audit, #12).
                // For a Nerd only, like the source badges.
                if (LocalNerd.current) {
                    Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                    CinemaText(
                        text = "${stringResource(R.string.details_label_source)} $providerName",
                        tone = TextTone.Secondary,
                    )
                }
            }
        }
    }
}

/**
 * One row of season pills. D-pad left/right moves focus between them, and [SeasonTab] selects
 * on focus, so moving alone switches the season, as the mobile swipe does.
 *
 * Small filled chips, not tabs: two rows of the same tab look under the section tabs read as two
 * equal levels (TV UI audit, #13).
 */
@Composable
private fun SeasonTabs(
    seasons: List<SeasonInfo>,
    selectedSeason: Int?,
    onSeasonSelected: (Int) -> Unit,
    onSeasonLongPress: ((Int) -> Unit)?,
    entryFocusRequester: FocusRequester,
) {
    val scale = LocalUiScale.current
    // One FocusRequester per tab, wired to its left/right neighbour below: default geometry search is
    // not reliable in this row (Down from above landed on the nearest tab, and Left/Right sometimes did
    // not move focus).
    val focusRequesters = remember(seasons) { seasons.map { FocusRequester() } }
    val selectedIndex =
        remember(seasons, selectedSeason) {
            seasons.indexOfFirst { it.seasonNumber == selectedSeason }.coerceAtLeast(0)
        }
    // Scrolls sideways, so a show with 20+ seasons does not squeeze its tabs. A focused tab scrolls
    // itself into view; the selected one is also scrolled to, for a season picked without focus (the
    // resume season). Scrolled directly, not with bringIntoView, which would also scroll the episode
    // list away from the resume episode.
    val tabScroll = rememberScrollState()
    val tabStarts = remember(seasons) { IntArray(seasons.size) }
    LaunchedEffect(selectedIndex) {
        withFrameNanos { }
        tabStarts.getOrNull(selectedIndex)?.let { tabScroll.animateScrollTo(it) }
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                // Opaque: this row is pinned, so episode cards scroll in under it.
                .panel(PanelFill.Ground)
                .padding(vertical = Spacing.sm.scaled(scale))
                .horizontalScroll(tabScroll)
                // Entering this row from outside always lands on the selected tab. `onEnter` fires only when the
                // Row is a focus group, so `focusGroup()` is required.
                .focusGroup()
                .focusProperties {
                    onEnter = { focusRequesters.getOrNull(selectedIndex)?.requestFocus() }
                },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        seasons.forEachIndexed { index, season ->
            // The first chip is at the screen's left edge: Left from it opens the TV navigation rail.
            Box(
                modifier =
                    Modifier
                        .onPlaced { tabStarts[index] = it.positionInParent().x.roundToInt() }
                        .then(if (index == 0) Modifier.leftToRail() else Modifier),
            ) {
                SeasonTab(
                    season = season,
                    isSelected = season.seasonNumber == selectedSeason,
                    onSelected = { onSeasonSelected(season.seasonNumber) },
                    onLongPress = onSeasonLongPress?.let { { it(season.seasonNumber) } },
                    focusRequester = focusRequesters[index],
                    previousTabFocusRequester = focusRequesters.getOrNull(index - 1),
                    nextTabFocusRequester = focusRequesters.getOrNull(index + 1),
                    // Second requester on the same node, only on the selected tab: the Category chip's explicit
                    // `down` (see caller) resolves to it.
                    entryFocusRequester = if (index == selectedIndex) entryFocusRequester else null,
                )
            }
        }
    }
}

@Composable
private fun SeasonTab(
    season: SeasonInfo,
    isSelected: Boolean,
    onSelected: () -> Unit,
    onLongPress: (() -> Unit)?,
    focusRequester: FocusRequester,
    previousTabFocusRequester: FocusRequester?,
    nextTabFocusRequester: FocusRequester?,
    entryFocusRequester: FocusRequester?,
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusScale by animateFloatAsState(
        targetValue = if (isFocused) TvFocusTokens.focusedScaleSubtle else TvFocusTokens.defaultScale,
        animationSpec = tween(durationMillis = CinemaAnimation.focusDurationMs),
        label = "season_tab_focus_scale",
    )
    val seasonChipShape = CinemaTheme.shapes.chip

    // Focus-follow-select, as immediate as the mobile swipe. Called from a LaunchedEffect, not from
    // onFocusChanged: onSelected changes EpisodeResumeState, and doing that inside the focus-change
    // callback can recompose this row in the middle of the focus transfer.
    LaunchedEffect(isFocused) {
        if (isFocused) onSelected()
    }

    // The selected season's text is the "current" colour (TvFocusTokens.currentText: the soft
    // accent); focused, the chip is white and its text dark.
    val textTone =
        when {
            isFocused -> TextTone.OnFocus
            isSelected -> TextTone.AccentSoft
            else -> TextTone.Secondary
        }

    Box(
        modifier =
            Modifier
                .focusRequester(focusRequester)
                .then(entryFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .focusProperties {
                    previousTabFocusRequester?.let { left = it }
                    nextTabFocusRequester?.let { right = it }
                }.graphicsLayer {
                    scaleX = focusScale
                    scaleY = focusScale
                }.currentIndicator(
                    active = isSelected,
                    shape = seasonChipShape,
                    edge = CurrentIndicatorEdge.Bottom,
                ).tabChipFill(isFocused)
                // No separate .focusable(): .clickable() already makes a focus target, and a second one on the same
                // node made the tabs unreachable by D-pad Up/Down.
                .onFocusChanged { isFocused = it.isFocused }
                // Hold OK or Menu: the season's actions (mark it watched or unwatched).
                .then(onLongPress?.let { Modifier.tvLongPress(it).openActionsOnMenuKey(it) } ?: Modifier)
                // look-ok: a focusable picker or a season tab chip, not a list row
                .clickable { onSelected() }
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        // A page tab: the tab role, as TvSectionTabs and Search's tabs.
        CinemaText(
            text = stringResource(R.string.series_season_label, season.seasonNumber),
            role = TvInputDefaults.tabLabelRole,
            tone = textTone,
        )
    }
}

/** A season or show mark that failed (its episodes couldn't be loaded): what went wrong, and OK. */
@Composable
private fun MarkWatchedErrorDialog(
    message: String,
    onDismiss: () -> Unit,
) {
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.watched_mark_many_failed),
        text = { CinemaText(message, tone = TextTone.Secondary) },
        main = DialogAction(stringResource(R.string.common_ok), onClick = onDismiss),
    )
}

@Composable
private fun EpisodeCard(
    episode: DomainEpisodeItem,
    cardStyle: TvCardLook,
    isContinueWatching: Boolean = false,
    watchProgress: Float = 0f,
    isWatched: Boolean = false,
    isNew: Boolean = false,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    val scale = LocalUiScale.current
    var isFocused by remember { mutableStateOf(false) }
    TvCard(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .height(TvDimensions.episodeCardHeight.scaled(scale))
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { isFocused = it.isFocused }
                .then(onLongPress?.let { Modifier.tvLongPress(it).openActionsOnMenuKey(it) } ?: Modifier),
        look = cardStyle,
    ) {
        // Box, not a Column with a weighted row: the card has a fixed cardHeight that its content fills,
        // so a footer with layout height falls outside the clip and never paints.
        // Continue watching is the P5 "current" mark (bar + title colour), not an outline (F-E-3).
        Box(modifier = Modifier.fillMaxSize().currentIndicator(isContinueWatching)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(Spacing.md.scaled(scale)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Episode thumbnail, with the watched check overlaid on it. The resume bar is a
                // card-width footer below this row instead — see the note there.
                Box(
                    modifier =
                        Modifier.size(
                            width = TvDimensions.posterWidth.scaled(scale),
                            height = TvDimensions.posterHeight.scaled(scale),
                        ),
                ) {
                    CinemaThumbnail(
                        url = episode.thumbnailUrl,
                        fallbackLetter = episode.title.firstOrNull(),
                        contentType = ThumbnailContentType.TV_SHOW,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (isWatched) {
                        WatchedBadge(
                            size = TvDimensions.iconMedium.scaled(scale),
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(Spacing.xxs.scaled(scale)),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))

                CinemaText(
                    text = stringResource(R.string.series_episode_number_short, episode.episodeNumber),
                    role = TextRole.Heading,
                    tone = TextTone.Secondary,
                    // Grows for E1992 instead of wrapping to two lines (Phase 6, F-E-4).
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    modifier = Modifier.widthIn(min = Spacing.xxl.scaled(scale)),
                )

                Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    if (isContinueWatching) {
                        CinemaText(
                            text = stringResource(R.string.series_continue_watching_badge),
                            role = TextRole.Caption,
                            tone = TextTone.Accent,
                        )
                    }
                    // Nothing when the provider's title only repeats the show and the number.
                    val ownTitle = remember(episode.title) { episodeOwnTitle(episode.title) }
                    // The mark follows the title, so that every row's title starts in the same place.
                    if (ownTitle.isNotBlank() || isNew) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (ownTitle.isNotBlank()) {
                                TvRowTitle(
                                    text = ownTitle,
                                    isCurrent = isContinueWatching,
                                    isFocused = false,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                            }
                            if (isNew) NewEpisodeMark()
                        }
                    }
                    episode.metadata.plot?.let { plotText ->
                        CinemaText(
                            text = plotText,
                            role = TextRole.Body,
                            tone = TextTone.Secondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                episode.metadata.duration?.takeIf(::hasMeaningfulDuration)?.let { duration ->
                    Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
                    CinemaText(
                        // "1h 33m", as everywhere else — not the provider's "01:33:44" (TV UI audit, X10).
                        text = formatDuration(duration),
                        role = TextRole.Body,
                        tone = TextTone.Secondary,
                    )
                }
                // The "⋮" hint on the focused row: hold OK or Menu opens its actions.
                if (isFocused && onLongPress != null) {
                    Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))
                    RowActionsHint()
                }
            }

            // Resume progress along the full card width at the bottom, as in StreamList, so a half-watched
            // episode and a half-watched film look the same.
            if (watchProgress > 0f) {
                CinemaProgressBar(
                    progress = { watchProgress.coerceIn(0f, 1f) },
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = TvDimensions.borderFocused.scaled(scale))
                            .height(TvDimensions.resumeBarHeight.scaled(scale)),
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    val scale = LocalUiScale.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CinemaSpinner(modifier = Modifier.size(TvDimensions.progressIndicator.scaled(scale)))
            Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
            CinemaText(
                text = stringResource(R.string.series_loading_episodes),
                role = TextRole.Title,
                tone = TextTone.Secondary,
            )
        }
    }
}

/** The section tab row's index in the episode list: right after the hero. */
private const val TABS_ITEM_INDEX = 1

/** The pinned episodes header's index in the episode list: right after the section tab row. */
private const val EPISODES_HEADER_ITEM_INDEX = 2

// Keys for the controls that navigate away from series details — see rememberNavReturnFocus.
private const val RETURN_CATEGORY = "category"
private const val RETURN_RELATED_PREFIX = "related:"

/** How long the screen waits for the resume episode's card to scroll into view before focusing Play instead. */
private const val RESUME_CARD_WAIT_MS = 2_000L
