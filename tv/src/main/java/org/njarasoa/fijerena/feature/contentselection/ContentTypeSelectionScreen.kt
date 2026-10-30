@file:OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.njarasoa.fijerena.feature.contentselection

import android.text.format.DateUtils
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.friendlyErrorMessage
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.tmdb.TmdbChart
import org.njarasoa.fijerena.core.network.wanted.GoneFavorite
import org.njarasoa.fijerena.core.network.wanted.WantedTitles
import org.njarasoa.fijerena.core.network.xtream.ProviderSyncRunner
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.ContinueWatchingItem
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.player.domain.sourceKey
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ProfileAvatar
import org.njarasoa.fijerena.core.ui.components.SectionPanel
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.initialOf
import org.njarasoa.fijerena.core.ui.components.staggeredEntrance
import org.njarasoa.fijerena.core.ui.components.textStyleOf
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.history.MergedLists
import org.njarasoa.fijerena.core.ui.home.BrowseSource
import org.njarasoa.fijerena.core.ui.home.ChartRows
import org.njarasoa.fijerena.core.ui.home.LiveRowEntry
import org.njarasoa.fijerena.core.ui.home.ProfileSections
import org.njarasoa.fijerena.core.ui.home.favoriteChannelsRow
import org.njarasoa.fijerena.core.ui.home.mergeLiveRow
import org.njarasoa.fijerena.core.ui.home.rememberProfileSyncStates
import org.njarasoa.fijerena.core.ui.home.syncSummary
import org.njarasoa.fijerena.core.ui.model.FavoriteMenuTarget
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentDark
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaBackground
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaGlassBorder
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaOrange
import org.njarasoa.fijerena.core.ui.theme.CinemaOrangeDark
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.LocalPosterBackdrop
import org.njarasoa.fijerena.core.ui.utils.launchGuarded
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.core.ui.wanted.WantedCard
import org.njarasoa.fijerena.core.ui.wanted.cards
import org.njarasoa.fijerena.core.ui.wanted.gone
import org.njarasoa.fijerena.core.ui.wanted.toAnnounce
import org.njarasoa.fijerena.feature.category.components.FavoriteContextMenuDialog
import org.njarasoa.fijerena.feature.category.components.MinimalBringIntoView
import org.njarasoa.fijerena.feature.category.components.RowMenuList
import org.njarasoa.fijerena.feature.contentselection.components.SourcesStatusSheet
import org.njarasoa.fijerena.feature.contentselection.components.SyncSummaryLine
import org.njarasoa.fijerena.feature.contentselection.components.TvContinueWatchingShelf
import org.njarasoa.fijerena.feature.contentselection.components.TvFavoritesRow
import org.njarasoa.fijerena.feature.contentselection.components.TvLiveRow
import org.njarasoa.fijerena.feature.contentselection.components.TvWantedBanner
import org.njarasoa.fijerena.feature.contentselection.components.TvWantedRow
import org.njarasoa.fijerena.ui.components.AmbientBackdrop
import org.njarasoa.fijerena.ui.components.DetailsMoreMenu
import org.njarasoa.fijerena.ui.components.TvEmptyState
import org.njarasoa.fijerena.ui.components.TvUndoBar
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.clearOfTvClock
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.TvOptionRow
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.rail.LocalTvNavRail
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.components.rememberUndoBarState
import org.njarasoa.fijerena.ui.components.undoOnRemoteKeys
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled
import org.njarasoa.fijerena.core.navigation.ContentType as NavContentType
import org.njarasoa.fijerena.ui.theme.CornerRadius as CinemaCornerRadius

@Composable
fun ContentTypeSelectionScreen(
    onEpgBrowser: () -> Unit = {},
    // The merged sections, once read (with none: nothing is called).
    onCapabilitiesResolved: (Set<String>) -> Unit = {},
    onContinueWatchingSelected: (ContinueWatchingItem) -> Unit = {},
    // A channel from the Live row, the list it zaps through (CategoryViewModel's virtual
    // "favorites" or "recent" category id) and the source it is on.
    onLiveChannelSelected: (streamId: String, contextCategoryId: String, providerId: Long) -> Unit = { _, _, _ -> },
    // A favourite movie (ContentType.MOVIES) or show (ContentType.TV_SHOWS) from its Home row.
    onFavoriteSelected: (item: MediaItem, contentType: String) -> Unit = { _, _ -> },
    // A "Browse <source>" entry of a profile with no Xtream source (O1): that source's sections.
    onBrowse: (BrowseSource) -> Unit = {},
    // A profile with no source: Add Source. Null for a profile without Expert mode: it asks a grown-up.
    onAddSource: (() -> Unit)? = null,
    // A Wanted card: its source copy's page when a source has it, else its TMDB page.
    onWantedSelected: (Screen) -> Unit = {},
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()
    // A child profile's Home (docs/plans/20261019_kids-home-plan.md, Phase 1): their name in the
    // header, only what a parent put there, bigger cards, no card menus. Read once: a profile
    // switch builds Home again.
    val child = remember { appSettings.isChildProfile(appSettings.activeProfileId) }
    val profiles by remember {
        org.njarasoa.fijerena.core.network.profile
            .ProfileRepository(context.applicationContext)
            .observeProfiles()
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    // What the profile browses (multi-source plan, Phase 6): its Xtream sources' sections, merged,
    // and a Browse entry per other source; null until read. There is no source in use.
    var sections by remember { mutableStateOf<ProfileSections?>(null) }
    val supportedContentTypes = sections?.merged.orEmpty()

    // The header's one line for all the profile's sources (O3), and its sheet.
    val syncStates = rememberProfileSyncStates()
    val syncSummary = remember(syncStates) { syncSummary(syncStates, showUpdated = AppSettings(context).isDevMode) }
    var showSourcesSheet by remember { mutableStateOf(false) }
    val firstSyncs by ProviderSyncRunner.firstSyncs.collectAsStateWithLifecycle()

    // The rows' lists: every Xtream source of the profile's, merged (multi-source plan, Phases 4
    // and 6). Each card acts on, and opens on, its own source. Stored data only: an offline source
    // holds nothing up.
    var mergedLists by remember { mutableStateOf<MergedLists?>(null) }
    // Read again whenever the profile's sources change (one turned on or off, added or deleted),
    // and only then: the sections, the Browse entries and every row follow.
    LaunchedEffect(Unit) {
        ProviderRepository(context.applicationContext).profileSourceSet(appSettings.activeProfileId).collect {
            val loaded = ProfileSections.load(context.applicationContext)
            sections = loaded
            mergedLists = MergedLists.forSections(context.applicationContext)
            if (loaded.merged.isNotEmpty()) onCapabilitiesResolved(loaded.merged)
        }
    }
    val sourceNames = mergedLists?.sourceNames.orEmpty()
    var backdropImageUrl by remember { mutableStateOf<String?>(null) }

    // Art of the row card focused last (Phase 6). Read only inside HomeBackdrop, so a focus move
    // recomposes the backdrop, not the page; focus on a tile or the header keeps it. A title's art
    // shows crisp behind the rows; a channel's logo only as the blurred wash.
    var focusedArt by remember { mutableStateOf<HomeArt?>(null) }
    val artOnFocus: (String?, Boolean) -> Modifier = { url, title ->
        Modifier.onFocusChanged { if (it.hasFocus && !url.isNullOrBlank()) focusedArt = HomeArt(url, title) }
    }
    var continueWatchingItems by remember { mutableStateOf<List<ContinueWatchingItem>>(emptyList()) }

    // False until the shelf's first load for this repository, so entry focus can wait for it.
    var continueWatchingLoaded by remember { mutableStateOf(false) }

    // The Live row (TV home overhaul plan, Phase 4): loaded like the shelf, its Now lines each minute.
    var liveRowEntries by remember { mutableStateOf<List<LiveRowEntry>>(emptyList()) }
    // Favourite channels have their own row, after Channels, in their own order.
    var favoriteChannelEntries by remember { mutableStateOf<List<LiveRowEntry>>(emptyList()) }
    var liveRowLoaded by remember { mutableStateOf(false) }
    var liveNowPlaying by remember { mutableStateOf<Map<String, EpgProgram>>(emptyMap()) }

    // Favourite movies and shows rows (Phase 5), loaded with the Live row.
    var favoriteMovies by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var favoriteShows by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    // The TMDB chart rows (TMDB charts plan, Phase 3): each chart's titles the sources have, last in its section's group.
    var chartItems by remember { mutableStateOf<Map<TmdbChart, List<MediaItem>>>(emptyMap()) }
    // True once the chart rows were loaded (or are off): the shows' charts are above the favourite
    // movies, so the entry focus waits for them rather than land on a lower row and be pushed down.
    var chartsLoaded by remember { mutableStateOf(false) }
    val showCharts by remember { appSettings.observeShowTmdbCharts() }
        .collectAsStateWithLifecycle(initialValue = appSettings.showTmdbCharts)
    // The Trending rows' switch (the charts plan, follow-up F3): also the titles no source has, as
    // TMDB-only cards. The profile's own, on this device.
    val chartsShowMissing by remember { appSettings.observeChartsShowMissing(appSettings.activeProfileId) }
        .collectAsStateWithLifecycle(initialValue = appSettings.chartsShowMissing(appSettings.activeProfileId))
    // The row whose switch was just flipped: focus goes back to that switch once the rows reload.
    var chartSwitchFlipped by remember { mutableStateOf<HomeShelf?>(null) }

    // The Undo of a card's removal (Hold OK / Menu on a card, below).
    val undoBar = rememberUndoBarState()

    // The Wanted list (wanted titles plan, Phase 5): a row per section, in that section's group,
    // never empty and never on a child's Home (its state is empty there).
    val wantedTitles = remember { WantedTitles.getInstance(context.applicationContext) }
    val wantedState by wantedTitles.state.collectAsStateWithLifecycle()
    var wantedCards by remember { mutableStateOf<List<WantedCard>>(emptyList()) }
    var wantedMenu by remember { mutableStateOf<WantedCard?>(null) }
    // Home is for what can be played: only the titles a source has (user, 2026-10-22); the
    // others wait in the sections' Wishlist lists.
    LaunchedEffect(wantedState) { wantedCards = if (child) emptyList() else wantedState.cards().filter { it.isAvailable } }
    // What the banner announces: available titles no banner has told about yet. They are marked
    // told (on every device) only once the banner was on screen: on OK, or when Home is left
    // after showing it — not when Home is built behind the profile picker.
    val announced = remember(wantedState) { if (child) emptyList() else wantedState.toAnnounce() }
    val currentAnnounced by rememberUpdatedState(announced)
    val homeLifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    var bannerSeen by remember { mutableStateOf(false) }
    LaunchedEffect(announced.isNotEmpty(), homeLifecycle) {
        if (announced.isNotEmpty() && homeLifecycle.isAtLeast(Lifecycle.State.RESUMED)) bannerSeen = true
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (bannerSeen) wantedTitles.markNotifiedLater(currentAnnounced.map { it.wantedKey })
        bannerSeen = false
    }

    // Show EPG Browser button when EPG index has data. Collected live (not a one-shot
    // `remember`) so a source that finishes indexing while this screen is on-screen shows the
    // icon immediately, instead of waiting for the composable to be torn down and rebuilt.
    val epgIndexState by remember {
        org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
            .getInstance(context.applicationContext)
            .state
    }.collectAsStateWithLifecycle()
    val hasEpgData = epgIndexState is org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState.Indexed

    // Pull a recently-watched poster for the ambient backdrop wash — falls back to the plain
    // gradient (AmbientBackdrop's default) if there's no watch history yet or the provider
    // doesn't support it. From the profile's first Xtream source.
    LaunchedEffect(mergedLists) {
        val mp = mergedLists?.primary?.getProvider() ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            backdropImageUrl =
                listOf(ContentType.MOVIES, ContentType.TV_SHOWS, ContentType.LIVE_TV).firstNotNullOfOrNull { contentType ->
                    mp
                        .getRecentlyPlayed(contentType)
                        ?.getOrNull()
                        ?.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }
                        ?.thumbnailUrl
                }
        }
    }

    // "Jump Back In" shelf — reload whenever the repository changes (provider switch) and again
    // on every ON_RESUME, so returning from playback immediately reflects updated progress.
    LaunchedEffect(mergedLists) {
        // A removal's Undo belongs to the source it was made on.
        undoBar.dismiss()
        val merged = mergedLists ?: return@LaunchedEffect
        continueWatchingItems = merged.continueWatching()
        continueWatchingLoaded = true
    }
    LaunchedEffect(mergedLists, supportedContentTypes) {
        val merged = mergedLists ?: return@LaunchedEffect
        // A child's Home has no recent Channels row: it opens a list.
        liveRowEntries = if (!child && ContentType.LIVE_TV in supportedContentTypes) loadLiveRow(merged) else emptyList()
        favoriteChannelEntries = if (ContentType.LIVE_TV in supportedContentTypes) loadFavoriteChannels(merged) else emptyList()
        favoriteMovies = loadFavorites(merged, ContentType.MOVIES, supportedContentTypes)
        favoriteShows = loadFavorites(merged, ContentType.TV_SHOWS, supportedContentTypes)
        liveRowLoaded = true
    }
    // The chart rows after the other rows, so their TMDB calls never hold those up. Again when the
    // Admin switch changes. All six at once: a later second update recomposed the rows under a
    // card focus had just come back to (Back from a chart card), and focus jumped to a first card.
    // Again when a Trending row's switch flips.
    // Admin → Refresh Charts: read again.
    val chartRefreshes by ChartRows.refreshes.collectAsStateWithLifecycle()
    LaunchedEffect(mergedLists, supportedContentTypes, showCharts, chartsShowMissing, chartRefreshes) {
        val merged = mergedLists ?: return@LaunchedEffect
        snapshotFlow { liveRowLoaded }.first { it }
        val loaded =
            listOf(ContentType.MOVIES, ContentType.TV_SHOWS)
                .filter { showCharts && !child && it in supportedContentTypes }
                .flatMap { type -> withContext(Dispatchers.IO) { ChartRows.load(context.applicationContext, merged, type) } }
                .associate { it.chart to it.items }
        // Rows the same after a flip (no title more): focus never left the switch.
        if (loaded != chartItems) chartItems = loaded else chartSwitchFlipped = null
        chartsLoaded = true
    }
    LaunchedEffect(mergedLists, liveRowEntries, favoriteChannelEntries) {
        val merged = mergedLists ?: return@LaunchedEffect
        val items = (liveRowEntries + favoriteChannelEntries).map { it.item }.distinctBy { it.sourceKey }
        if (items.isEmpty()) return@LaunchedEffect
        while (true) {
            // Each channel from its own source's guide.
            val now = HashMap<String, EpgProgram>()
            for ((source, channels) in items.groupBy { it.providerId }) now += merged.repoFor(source).getNowPlayingFromIndex(channels)
            liveNowPlaying = now
            val nowMs = System.currentTimeMillis()
            delay(DateUtils.MINUTE_IN_MILLIS - nowMs % DateUtils.MINUTE_IN_MILLIS)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mergedLists) {
        val merged = mergedLists
        val observer =
            LifecycleEventObserver { _, event ->
                // A source synced meanwhile (Live TV shows once one has channels): the sections are
                // read again. A change to the profile's sources is followed above.
                if (event == Lifecycle.Event.ON_RESUME && sections != null) {
                    coroutineScope.launch { sections = ProfileSections.load(context.applicationContext) }
                }
                if (event == Lifecycle.Event.ON_RESUME && merged != null) {
                    coroutineScope.launch {
                        continueWatchingItems = merged.continueWatching()
                    }
                    if (ContentType.LIVE_TV in supportedContentTypes) {
                        coroutineScope.launch {
                            if (!child) liveRowEntries = loadLiveRow(merged)
                            favoriteChannelEntries = loadFavoriteChannels(merged)
                        }
                    }
                    coroutineScope.launch {
                        favoriteMovies = loadFavorites(merged, ContentType.MOVIES, supportedContentTypes)
                        favoriteShows = loadFavorites(merged, ContentType.TV_SHOWS, supportedContentTypes)
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Back from whatever a card or header button opened lands on that card or button, not on the
    // first focusable (the "Switch Source" chip). A Continue Watching card waits for the shelf to
    // reload and is scrolled into view first; a card that left the shelf (finished) falls back to
    // the first hero card.
    val returnFocus = rememberNavReturnFocus()
    val shelfListState = rememberLazyListState()

    // The first card of each row: where focus enters a row it has not been in yet (HomeRow).
    val shelfFirstFocus = remember { FocusRequester() }
    val liveRowFirstFocus = remember { FocusRequester() }
    val liveRowListState = rememberLazyListState()
    val favoriteChannelsFirstFocus = remember { FocusRequester() }
    val favoriteChannelsListState = rememberLazyListState()
    val favoriteMoviesFirstFocus = remember { FocusRequester() }
    val favoriteMoviesListState = rememberLazyListState()
    val favoriteShowsFirstFocus = remember { FocusRequester() }
    val favoriteShowsListState = rememberLazyListState()
    // A Wishlist row per section, in that section's group.
    val wantedShelves = HomeShelf.entries.filter { it.wantedContentType != null }
    val wantedFirstFocus = remember { wantedShelves.associateWith { FocusRequester() } }
    val wantedListState = wantedShelves.associateWith { rememberLazyListState() }
    val wantedOf: (HomeShelf) -> List<WantedCard> = { shelf -> wantedCards.filter { it.entity.contentType == shelf.wantedContentType } }
    val wantedBannerFocus = remember { FocusRequester() }
    // A row per TMDB chart, in its section's group.
    val chartShelves = HomeShelf.entries.filter { it.chart != null }
    val chartFirstFocus = remember { chartShelves.associateWith { FocusRequester() } }
    val chartListState = chartShelves.associateWith { rememberLazyListState() }
    val chartOf: (HomeShelf) -> List<MediaItem> = { shelf -> shelf.chart?.let { chartItems[it] }.orEmpty() }
    // A chart row shows when loaded: a Trending row with no title still has its switch.
    val chartShows: (HomeShelf) -> Boolean = { shelf -> shelf.chart in chartItems }
    val chartSwitchFocus = remember { chartShelves.associateWith { FocusRequester() } }
    // The row's items before its first card: a Trending row's switch.
    val itemsBeforeCards: (HomeShelf) -> Int = { shelf -> if (shelf.chart?.canShowMissing == true) 1 else 0 }
    // After a flip the row's cards change behind its switch: the switch keeps focus, in view.
    LaunchedEffect(chartItems) {
        val shelf = chartSwitchFlipped ?: return@LaunchedEffect
        chartSwitchFlipped = null
        withFrameNanos { }
        val listState = chartListState.getValue(shelf)
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == 0 }) listState.scrollToItem(0)
        chartSwitchFocus.getValue(shelf).requestFocusWithRetry()
    }
    // The first row of a section's group (TV Shows or Movies) that has a card.
    val sectionFirstFocus: (String) -> FocusRequester? = { type ->
        val wanted = wantedShelves.first { it.wantedContentType == type }
        when {
            type == ContentType.TV_SHOWS && favoriteShows.isNotEmpty() -> favoriteShowsFirstFocus
            type == ContentType.MOVIES && favoriteMovies.isNotEmpty() -> favoriteMoviesFirstFocus
            wantedOf(wanted).isNotEmpty() -> wantedFirstFocus.getValue(wanted)
            else -> chartShelves.firstOrNull { it.chart?.contentType == type && chartShows(it) }?.let { chartFirstFocus.getValue(it) }
        }
    }
    // The first card of the first row that has one: where focus opens and where Down from the header
    // goes. Null with no row at all; the sections are on the navigation rail then.
    // Derived, so the entry focus below reads the rows as they are now. A value read through
    // rememberUpdatedState catches up only at the next recomposition: a row that had just loaded
    // (Continue Watching) was not the first yet, focus went to a lower row's card, and the rows
    // loading above it pushed that card out of view, so nothing had focus (Back from Settings).
    val firstRowFocusState =
        remember(announced) {
            derivedStateOf {
                when {
                    // News first: titles that just became available.
                    announced.isNotEmpty() -> wantedBannerFocus

                    continueWatchingItems.isNotEmpty() -> shelfFirstFocus

                    favoriteChannelEntries.isNotEmpty() -> favoriteChannelsFirstFocus

                    liveRowEntries.isNotEmpty() -> liveRowFirstFocus

                    else -> sectionFirstFocus(ContentType.TV_SHOWS) ?: sectionFirstFocus(ContentType.MOVIES)
                }
            }
        }
    val firstRowFocus = firstRowFocusState.value
    val rail = LocalTvNavRail.current
    // The first button of the panel shown in place of the rows without merged sections.
    val sourcesPanelFocus = remember { FocusRequester() }
    // Read by the entry focus below after it has waited for the rows: the value from its launch
    // was from before they loaded (null: no focus at all after a profile switch).
    val currentFirstRowFocusState by rememberUpdatedState(firstRowFocusState)
    LaunchedEffect(Unit) {
        // Back hands focus to the control that was left (NavReturnFocusEffect below).
        if (returnFocus.isReturn) return@LaunchedEffect
        // Wait for the shelf and the Live row, which take entry focus when they have something
        // (TV home overhaul plan, Phase 3).
        withTimeoutOrNull(ENTRY_FOCUS_WAIT_MS) {
            snapshotFlow { sections?.merged?.isEmpty() == true || (continueWatchingLoaded && liveRowLoaded) }.first { it }
        }
        // The chart rows load after the others; a short wait more for them (a first TMDB read can be slow).
        withTimeoutOrNull(CHART_ENTRY_WAIT_MS) { snapshotFlow { chartsLoaded }.first { it } }
        // No merged sections: the panel's first button (Browse <source>, or Add Source); the rail
        // when the panel has no button (no source and no Expert mode).
        if (sections?.merged?.isEmpty() == true) {
            if (sections?.hasNoSources == true && onAddSource == null) {
                rail?.entry?.requestFocusWithRetry()
            } else {
                sourcesPanelFocus.requestFocusWithRetry(fallback = rail?.entry)
            }
            return@LaunchedEffect
        }
        // No row at all (a new source, nothing watched yet): the sections are on the rail, so
        // focus starts there.
        // After a profile or source switch the rows can come later than that wait: keep waiting
        // for one, then the rail (hidden by the profile picker a moment ago, so retried until it
        // is back).
        val target =
            currentFirstRowFocusState.value
                ?: withTimeoutOrNull(LATE_ROWS_WAIT_MS) { snapshotFlow { currentFirstRowFocusState.value }.first { it != null } }
        if (target != null) target.requestFocusWithRetry() else rail?.entry?.requestFocusWithRetry()
    }
    NavReturnFocusEffect(returnFocus, fallback = firstRowFocus) { key ->
        if (key.startsWith(RETURN_CONTINUE_WATCHING_PREFIX)) {
            val itemId = key.removePrefix(RETURN_CONTINUE_WATCHING_PREFIX)
            val items =
                withTimeoutOrNull(RETURN_SHELF_WAIT_MS) {
                    snapshotFlow { continueWatchingItems }.first { items -> items.any { it.sourceKey == itemId } }
                }
            val index = items?.indexOfFirst { it.sourceKey == itemId } ?: -1
            if (index >= 0) shelfListState.scrollToItem(index)
        }
        for ((prefix, row) in listOf(
            RETURN_LIVE_ROW_PREFIX to liveRowListState,
            RETURN_FAVORITE_CHANNELS_PREFIX to favoriteChannelsListState,
        )) {
            if (!key.startsWith(prefix)) continue
            val itemId = key.removePrefix(prefix)
            val entries =
                withTimeoutOrNull(RETURN_SHELF_WAIT_MS) {
                    snapshotFlow { if (prefix == RETURN_LIVE_ROW_PREFIX) liveRowEntries else favoriteChannelEntries }
                        .first { entries -> entries.any { it.item.sourceKey == itemId } }
                }
            val index = entries?.indexOfFirst { it.item.sourceKey == itemId } ?: -1
            if (index >= 0) row.scrollToItem(index)
        }
        for ((prefix, row) in listOf(
            RETURN_FAVORITE_MOVIES_PREFIX to favoriteMoviesListState,
            RETURN_FAVORITE_SHOWS_PREFIX to favoriteShowsListState,
        )) {
            if (!key.startsWith(prefix)) continue
            val itemId = key.removePrefix(prefix)
            val items =
                withTimeoutOrNull(RETURN_SHELF_WAIT_MS) {
                    snapshotFlow { if (prefix == RETURN_FAVORITE_MOVIES_PREFIX) favoriteMovies else favoriteShows }
                        .first { items -> items.any { it.sourceKey == itemId } }
                }
            val index = items?.indexOfFirst { it.sourceKey == itemId } ?: -1
            if (index >= 0) row.scrollToItem(index)
        }
        for (shelf in chartShelves) {
            val prefix = chartReturnPrefix(shelf)
            if (!key.startsWith(prefix)) continue
            val itemId = key.removePrefix(prefix)
            val items =
                withTimeoutOrNull(RETURN_CHART_WAIT_MS) {
                    snapshotFlow { chartOf(shelf) }.first { items -> items.any { it.sourceKey == itemId } }
                }
            val index = items?.indexOfFirst { it.sourceKey == itemId } ?: -1
            if (index >= 0) chartListState.getValue(shelf).scrollToItem(index + itemsBeforeCards(shelf))
        }
        if (key.startsWith(RETURN_WANTED_PREFIX)) {
            val cardKey = key.removePrefix(RETURN_WANTED_PREFIX)
            val shelf = wantedShelves.first { cardKey.startsWith("${it.wantedContentType}:") }
            val cards =
                withTimeoutOrNull(RETURN_SHELF_WAIT_MS) {
                    snapshotFlow { wantedOf(shelf) }.first { cards -> cards.any { it.key == cardKey } }
                }
            val index = cards?.indexOfFirst { it.key == cardKey } ?: -1
            if (index >= 0) wantedListState.getValue(shelf).scrollToItem(index)
        }
    }
    val leaveTo: (String, () -> Unit) -> Unit = { key, navigate ->
        returnFocus.leaveFrom(key)
        navigate()
    }

    // Favourites their source no longer lists (wanted titles plan → Favourites gone from their
    // source): marked on their cards; opened, a menu moves one to its other copy, else a message.
    val goneLabel = stringResource(R.string.favorite_gone_from_source)
    val resources = LocalResources.current
    val goneStatus: (MediaItem, String) -> String? = { item, type ->
        goneLabel.takeIf {
            wantedState.gone(item.providerId, item.id, type) !=
                null
        }
    }
    var goneMenu by remember { mutableStateOf<Triple<MediaItem, String, GoneFavorite>?>(null) }
    val openFavorite: (MediaItem, String, () -> Unit) -> Unit = { item, type, open ->
        val gone = wantedState.gone(item.providerId, item.id, type)
        when {
            gone?.copy != null -> {
                goneMenu = Triple(item, type, gone)
            }

            gone != null -> {
                val name = parseDisplayTitle(item.name).title.ifBlank { item.name }
                android.widget.Toast
                    .makeText(context, resources.getString(R.string.favorite_gone_message, name), android.widget.Toast.LENGTH_LONG)
                    .show()
            }

            else -> {
                open()
            }
        }
    }

    // Hold OK / Menu on a card (the tv recents favorites plan → A, "Home's
    // cards"): the list rows' menu. A removal takes the card out of its row at once, focus going
    // to the card that took its place (cardAfterChange), with the Undo bar; Menu while it shows
    // puts the card back, focus on it.
    var cardMenu by remember { mutableStateOf<HomeCardMenu?>(null) }
    // One requester per card, attached for good: attaching one only while a request was pending,
    // then taking it off, changed the focused card's modifiers and focus fell back to the row's
    // first card (the removal's neighbour got focus, then lost it).
    val cardFocus = remember { mutableMapOf<HomeCard, FocusRequester>() }
    var cardFocusRequest by remember { mutableStateOf<CardFocusRequest?>(null) }
    val shelfKeys: (HomeShelf) -> List<String> = { shelf ->
        when (shelf) {
            HomeShelf.CONTINUE_WATCHING -> continueWatchingItems.map { it.sourceKey }

            HomeShelf.CHANNELS -> liveRowEntries.map { it.item.sourceKey }

            HomeShelf.FAVORITE_CHANNELS -> favoriteChannelEntries.map { it.item.sourceKey }

            HomeShelf.FAVORITE_MOVIES -> favoriteMovies.map { it.sourceKey }

            HomeShelf.FAVORITE_SHOWS -> favoriteShows.map { it.sourceKey }

            HomeShelf.WANTED_SHOWS, HomeShelf.WANTED_MOVIES -> wantedOf(shelf).map { it.key }

            HomeShelf.CHART_TRENDING_MOVIES,
            HomeShelf.CHART_POPULAR_MOVIES,
            HomeShelf.CHART_TOP_RATED_MOVIES,
            HomeShelf.CHART_TRENDING_SHOWS,
            HomeShelf.CHART_POPULAR_SHOWS,
            HomeShelf.CHART_TOP_RATED_SHOWS,
            -> chartOf(shelf).map { it.sourceKey }
        }
    }
    val listStateOf: (HomeShelf) -> LazyListState = { shelf ->
        when (shelf) {
            HomeShelf.CONTINUE_WATCHING -> shelfListState

            HomeShelf.CHANNELS -> liveRowListState

            HomeShelf.FAVORITE_CHANNELS -> favoriteChannelsListState

            HomeShelf.FAVORITE_MOVIES -> favoriteMoviesListState

            HomeShelf.FAVORITE_SHOWS -> favoriteShowsListState

            HomeShelf.WANTED_SHOWS, HomeShelf.WANTED_MOVIES -> wantedListState.getValue(shelf)

            HomeShelf.CHART_TRENDING_MOVIES,
            HomeShelf.CHART_POPULAR_MOVIES,
            HomeShelf.CHART_TOP_RATED_MOVIES,
            HomeShelf.CHART_TRENDING_SHOWS,
            HomeShelf.CHART_POPULAR_SHOWS,
            HomeShelf.CHART_TOP_RATED_SHOWS,
            -> chartListState.getValue(shelf)
        }
    }
    val focusAfterChange: (HomeShelf, List<String>, String) -> Unit = { shelf, oldKeys, key ->
        cardFocusRequest = CardFocusRequest(cardAfterChange(shelf, oldKeys, key, HomeShelf.entries.associateWith(shelfKeys)))
    }
    // The card asked for, scrolled into its row when off screen; no card left: the rail. Never
    // nothing focused.
    LaunchedEffect(cardFocusRequest) {
        val request = cardFocusRequest ?: return@LaunchedEffect
        // After the card menu has closed: its window hands focus back to Home as it goes, and a
        // request made while it was still up landed, then fell back to the row's first card.
        snapshotFlow { cardMenu == null && wantedMenu == null }.first { it }
        repeat(2) { withFrameNanos { } }
        val card = request.card
        if (card == null) {
            rail?.entry?.requestFocusWithRetry()
        } else {
            val listState = listStateOf(card.shelf)
            val index = shelfKeys(card.shelf).indexOf(card.key).let { if (it >= 0) it + itemsBeforeCards(card.shelf) else it }
            if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.scrollToItem(index)
            val requester = cardFocus.getOrPut(card) { FocusRequester() }
            requester.requestFocusWithRetry(fallback = rail?.entry)
            // Coming from outside the row (the menu had focus), the request enters the row's
            // focusRestorer, which hands it to the card it remembers — the one just removed — or
            // the first card. Asked again from inside the row, it lands on the card.
            withFrameNanos { }
            requester.requestFocus()
        }
        cardFocusRequest = null
    }
    val cardFocusTarget: (HomeShelf, String) -> Modifier = { shelf, key ->
        Modifier.focusRequester(cardFocus.getOrPut(HomeCard(shelf, key)) { FocusRequester() })
    }

    // Takes the card out of its row (returns how to put it back where it was).
    fun <T> takeOut(
        before: List<T>,
        key: String,
        keyOf: (T) -> String,
        current: () -> List<T>,
        set: (List<T>) -> Unit,
    ): () -> Unit {
        set(before.filterNot { keyOf(it) == key })
        return { set(current().withCardPutBack(before, key, keyOf)) }
    }

    fun takeOut(
        shelf: HomeShelf,
        key: String,
    ): () -> Unit =
        when (shelf) {
            HomeShelf.CONTINUE_WATCHING -> {
                takeOut(continueWatchingItems, key, { it.sourceKey }, { continueWatchingItems }) {
                    continueWatchingItems =
                        it
                }
            }

            HomeShelf.CHANNELS -> {
                takeOut(liveRowEntries, key, { it.item.sourceKey }, { liveRowEntries }) { liveRowEntries = it }
            }

            HomeShelf.FAVORITE_CHANNELS -> {
                takeOut(favoriteChannelEntries, key, { it.item.sourceKey }, { favoriteChannelEntries }) {
                    favoriteChannelEntries =
                        it
                }
            }

            HomeShelf.FAVORITE_MOVIES -> {
                takeOut(favoriteMovies, key, { it.sourceKey }, { favoriteMovies }) { favoriteMovies = it }
            }

            HomeShelf.FAVORITE_SHOWS -> {
                takeOut(favoriteShows, key, { it.sourceKey }, { favoriteShows }) { favoriteShows = it }
            }

            HomeShelf.WANTED_SHOWS, HomeShelf.WANTED_MOVIES -> {
                takeOut(wantedCards, key, { it.key }, { wantedCards }) { wantedCards = it }
            }

            // A chart card is never removed.
            HomeShelf.CHART_TRENDING_MOVIES,
            HomeShelf.CHART_POPULAR_MOVIES,
            HomeShelf.CHART_TOP_RATED_MOVIES,
            HomeShelf.CHART_TRENDING_SHOWS,
            HomeShelf.CHART_POPULAR_SHOWS,
            HomeShelf.CHART_TOP_RATED_SHOWS,
            -> {
                {}
            }
        }

    // A card leaving its row: out at once, focus on its neighbour, then [remove] (which returns
    // its own undo); the Undo bar puts the card back, focus on it, and runs that undo.
    fun removeCard(
        shelf: HomeShelf,
        key: String,
        name: String,
        remove: suspend () -> (suspend () -> Unit),
    ) {
        val oldKeys = shelfKeys(shelf)
        val putBack = takeOut(shelf, key)
        focusAfterChange(shelf, oldKeys, key)
        var undo: (suspend () -> Unit)? = null
        val removing = coroutineScope.launchGuarded("Home.removeCard") { undo = remove() }
        undoBar.show(parseDisplayTitle(name).title.ifBlank { name }) {
            val keysNow = shelfKeys(shelf)
            putBack()
            focusAfterChange(shelf, keysNow, key)
            coroutineScope.launchGuarded("Home.undoRemoveCard") {
                removing.join()
                undo?.invoke()
            }
        }
    }

    val reloadFavorites: suspend (MergedLists, String) -> Unit = { merged, contentType ->
        when (contentType) {
            ContentType.LIVE_TV -> favoriteChannelEntries = loadFavoriteChannels(merged)
            ContentType.MOVIES -> favoriteMovies = loadFavorites(merged, contentType, supportedContentTypes)
            ContentType.TV_SHOWS -> favoriteShows = loadFavorites(merged, contentType, supportedContentTypes)
        }
    }
    // The shelf again after a watched mark; a card it no longer has hands focus on (an episode's
    // card turns into its show's "Up next" and stays).
    val reloadContinueWatching: suspend (MergedLists, String?) -> Unit = { merged, focusedKey ->
        val oldKeys = shelfKeys(HomeShelf.CONTINUE_WATCHING)
        continueWatchingItems = merged.continueWatching()
        if (focusedKey != null && focusedKey in oldKeys && focusedKey !in shelfKeys(HomeShelf.CONTINUE_WATCHING)) {
            focusAfterChange(HomeShelf.CONTINUE_WATCHING, oldKeys, focusedKey)
        }
    }

    // Opens the menu once the card's favourite and watched states are known.
    val openCardMenu: (HomeShelf, HomeCardItem) -> Unit = { shelf, ref ->
        // The card's own source.
        mergedLists?.repoFor(ref.providerId)?.let { repo ->
            coroutineScope.launchGuarded("Home.openCardMenu") {
                val isFavorite = shelf.isFavorites || repo.isFavoriteSuspend(ref.id, ref.contentType)
                // Null hides "Mark watched", as on a source that keeps watched state on its server.
                val isWatched =
                    when {
                        ref.watchedItemId == null || !repo.supportsMarkWatched -> {
                            null
                        }

                        shelf == HomeShelf.CONTINUE_WATCHING -> {
                            false
                        }

                        else -> {
                            repo.getPlaybackPositions(listOf(ref.watchedItemId), ref.contentType)[ref.watchedItemId]?.isCompleted ==
                                true
                        }
                    }
                // A favourite show's "Mark show watched / unwatched"
                // (the season show watched plan Phase 3): null hides it, as on a
                // source that keeps watched state on its server.
                val isShowWatched =
                    if ((shelf == HomeShelf.FAVORITE_SHOWS || shelf.chart?.contentType == ContentType.TV_SHOWS) &&
                        repo.supportsMarkWatched
                    ) {
                        repo.episodesWatchState(ref.id, null) == true
                    } else {
                        null
                    }
                cardMenu =
                    HomeCardMenu(
                        shelf = shelf,
                        ref = ref,
                        target =
                            FavoriteMenuTarget.Stream(
                                itemId = ref.id,
                                itemName = ref.name,
                                categoryId = ref.categoryId,
                                contentType = ref.contentType,
                                isFavorite = isFavorite,
                                isWatched = isWatched,
                                // A source that keeps its own history (Jellyfin) has no Remove from Recent;
                                // a chart card has no removal at all.
                                isInRecent = !shelf.isFavorites && shelf.chart == null && repo.supportsRemoveFromRecent,
                                isShowWatched = isShowWatched,
                            ),
                    )
            }
        }
    }
    goneMenu?.let { (item, type, gone) ->
        val copy = gone.copy
        DetailsMoreMenu(
            title = stringResource(R.string.favorite_gone_message, parseDisplayTitle(item.name).title.ifBlank { item.name }),
            refreshLabel = stringResource(R.string.favorite_move_to_copy),
            icon = CinemaIcons.SwapHoriz,
            onRefresh = {
                val merged = mergedLists
                if (merged != null && copy != null) {
                    // The user's move: the copy becomes the favourite, then the old one goes.
                    coroutineScope.launchGuarded("Home.moveToCopy") {
                        merged.repoFor(copy.providerId).addFavoriteSuspend(copy.itemId, copy.name, copy.categoryId, type)
                        merged.repoFor(gone.providerId).removeFavoriteSuspend(item.id, type)
                        wantedTitles.refresh()
                        reloadFavorites(merged, type)
                    }
                }
            },
            onDismiss = { goneMenu = null },
        )
    }
    // A Wanted card's menu: Remove from Wanted, with its Undo.
    wantedMenu?.let { card ->
        DetailsMoreMenu(
            title = card.entity.title,
            refreshLabel = stringResource(R.string.wanted_remove),
            icon = CinemaIcons.Delete,
            onRefresh = {
                removeCard(wantedShelves.first { it.wantedContentType == card.entity.contentType }, card.key, card.entity.title) {
                    wantedTitles.remove(card.wantedKey)
                    suspend { wantedTitles.add(card.entity) }
                }
            },
            onDismiss = { wantedMenu = null },
        )
    }
    cardMenu?.let { menu ->
        val merged = mergedLists
        val ref = menu.ref
        // The card's own source acts on it (multi-source plan, Phase 4).
        val repo = merged?.repoFor(ref.providerId)
        val markShowFailed = stringResource(R.string.watched_mark_many_failed)
        FavoriteContextMenuDialog(
            target = menu.target,
            onConfirm = {
                when {
                    repo == null || merged == null -> {}

                    menu.shelf.isFavorites -> {
                        removeCard(menu.shelf, ref.key, ref.name) {
                            repo.removeFavoriteSuspend(ref.id, ref.contentType)
                            suspend { repo.addFavoriteSuspend(ref.id, ref.name, ref.categoryId, ref.contentType) }
                        }
                    }

                    menu.target.isFavorite -> {
                        // The card stays in its row; its favourite leaves the favourites row below.
                        coroutineScope.launchGuarded("Home.removeFavorite") {
                            repo.removeFavoriteSuspend(ref.id, ref.contentType)
                            reloadFavorites(merged, ref.contentType)
                        }
                        undoBar.show(parseDisplayTitle(ref.name).title.ifBlank { ref.name }) {
                            coroutineScope.launchGuarded("Home.undoRemoveFavorite") {
                                repo.addFavoriteSuspend(ref.id, ref.name, ref.categoryId, ref.contentType)
                                reloadFavorites(merged, ref.contentType)
                            }
                        }
                    }

                    else -> {
                        coroutineScope.launchGuarded("Home.addFavorite") {
                            repo.addFavoriteSuspend(ref.id, ref.name, ref.categoryId, ref.contentType)
                            reloadFavorites(merged, ref.contentType)
                        }
                    }
                }
            },
            onDismiss = { cardMenu = null },
            onToggleWatched =
                ref.watchedItemId?.let { watchedItemId ->
                    {
                        when {
                            repo == null || merged == null -> {}

                            // A film watched leaves Continue Watching: a removal, with its Undo.
                            menu.shelf == HomeShelf.CONTINUE_WATCHING && ref.contentType == ContentType.MOVIES -> {
                                removeCard(menu.shelf, ref.key, ref.name) {
                                    repo.setWatched(watchedItemId, ref.contentType, true)
                                    suspend { repo.setWatched(watchedItemId, ref.contentType, false) }
                                }
                            }

                            else -> {
                                coroutineScope.launchGuarded("Home.toggleWatched") {
                                    repo.setWatched(watchedItemId, ref.contentType, menu.target.isWatched != true)
                                    reloadContinueWatching(merged, ref.key.takeIf { menu.shelf == HomeShelf.CONTINUE_WATCHING })
                                }
                            }
                        }
                    }
                },
            onRemoveFromRecent =
                if (menu.target.isInRecent && repo != null) {
                    {
                        removeCard(menu.shelf, ref.key, ref.name) {
                            val removal = repo.removeFromRecent(ref.id, ref.contentType)
                            suspend { removal?.let { repo.restoreRecent(it) } }
                        }
                    }
                } else {
                    null
                },
            list =
                when {
                    menu.shelf.isFavorites -> RowMenuList.FAVORITES
                    menu.shelf.chart != null -> RowMenuList.OTHER
                    else -> RowMenuList.RECENT
                },
            onToggleShowWatched =
                menu.target.isShowWatched?.let { isShowWatched ->
                    {
                        if (repo != null && merged != null) {
                            coroutineScope.launchGuarded("Home.setShowWatched") {
                                repo
                                    .setEpisodesWatched(ref.id, null, !isShowWatched)
                                    .onSuccess { reloadContinueWatching(merged, null) }
                                    .onFailure { e ->
                                        val message = markShowFailed + "\n" + friendlyErrorMessage(e, context, appSettings.isDevMode)
                                        android.widget.Toast
                                            .makeText(context, message, android.widget.Toast.LENGTH_LONG)
                                            .show()
                                    }
                            }
                        }
                    }
                },
        )
    }

    val scale = LocalUiScale.current
    // A section's Wishlist row: the titles a source has (user, 2026-10-22).
    val wantedRow: @Composable (HomeShelf) -> Unit = { shelf ->
        val cards = wantedOf(shelf)
        if (cards.isNotEmpty()) {
            val newCount = cards.count { it.isNew }
            val wantedTitle = stringResource(R.string.wanted_title)
            TvWantedRow(
                title =
                    if (newCount >
                        0
                    ) {
                        stringResource(R.string.wanted_title_new_format, wantedTitle, newCount)
                    } else {
                        wantedTitle
                    },
                cards = cards,
                onCardSelected = { card ->
                    leaveTo(RETURN_WANTED_PREFIX + card.key) {
                        if (card.isNew) {
                            coroutineScope.launchGuarded(
                                "Home.wantedSeen",
                            ) { wantedTitles.markSeen(card.wantedKey) }
                        }
                        onWantedSelected(card.route)
                    }
                },
                onOpenActions = { card -> wantedMenu = card },
                firstItemFocus = wantedFirstFocus.getValue(shelf),
                restoreFocus = returnFocus.requesterWhere { it.startsWith(RETURN_WANTED_PREFIX) },
                listState = wantedListState.getValue(shelf),
                itemModifier = { card ->
                    Modifier
                        .navReturnFocusTarget(returnFocus, RETURN_WANTED_PREFIX + card.key)
                        .then(cardFocusTarget(shelf, card.key))
                        .then(artOnFocus(card.posterUrl, true))
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xl.scaled(scale)),
            )
        }
    }
    // A TMDB chart row, in its section's group. Cards as on
    // the favourite rows; their menu has the favourite toggle and no removal.
    // A Trending row starts with its switch: OK also shows the titles no source
    // has (TMDB-only cards, which open their TMDB page), or hides them again.
    val chartRow: @Composable (HomeShelf) -> Unit = { shelf ->
        val chart = shelf.chart
        if (chart != null && chartShows(shelf)) {
            val items = chartOf(shelf)
            val isShows = chart.contentType == ContentType.TV_SHOWS
            val prefix = chartReturnPrefix(shelf)
            TvFavoritesRow(
                title = stringResource(ChartRows.titleRes(chart)),
                items = items,
                thumbnailType = if (isShows) ThumbnailContentType.TV_SHOW else ThumbnailContentType.MOVIE,
                onItemSelected = { item ->
                    leaveTo(prefix + item.sourceKey) { onFavoriteSelected(item, chart.contentType) }
                },
                firstItemFocus = chartFirstFocus.getValue(shelf),
                restoreFocus = returnFocus.requesterWhere { it.startsWith(prefix) },
                onOpenActions =
                    if (child) {
                        null
                    } else {
                        (
                            { item ->
                                openCardMenu(
                                    shelf,
                                    item.toCardItem(chart.contentType, watchedItemId = item.id.takeUnless { isShows }),
                                )
                            }
                        )
                    },
                listState = chartListState.getValue(shelf),
                itemModifier = { item ->
                    Modifier
                        .navReturnFocusTarget(returnFocus, prefix + item.sourceKey)
                        .then(cardFocusTarget(shelf, item.sourceKey))
                        .then(artOnFocus(item.thumbnailUrl, true))
                },
                sourceNames = sourceNames,
                leading =
                    if (chart.canShowMissing) {
                        { switchModifier ->
                            TvIconAction(
                                onClick = {
                                    chartSwitchFlipped = shelf
                                    ChartRows.setShowMissing(context, !chartsShowMissing)
                                },
                                icon = if (chartsShowMissing) CinemaIcons.VisibilityOff else CinemaIcons.Visibility,
                                label =
                                    if (chartsShowMissing) {
                                        stringResource(R.string.chart_hide_missing)
                                    } else {
                                        stringResource(R.string.chart_show_missing)
                                    },
                                modifier = switchModifier.focusRequester(chartSwitchFocus.getValue(shelf)),
                            )
                        }
                    } else {
                        null
                    },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xl.scaled(scale)),
            )
        }
    }

    // Menu undoes the last removal while its bar shows.
    Box(modifier = Modifier.fillMaxSize().undoOnRemoteKeys(undoBar)) {
        HomeBackdrop(fallbackUrl = backdropImageUrl, focused = { focusedArt })
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = Spacing.tvSafeMarginHorizontal,
                        vertical = Spacing.tvSafeMarginVertical,
                    ),
        ) {
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
                        modifier = Modifier.staggeredEntrance(0),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        val childProfile = profiles.firstOrNull { it.id == appSettings.activeProfileId }
                        if (child && childProfile != null) {
                            // Whose Home this is, as the picker showed it; no clock, no sources line.
                            ProfileAvatar(
                                name = initialOf(childProfile.name),
                                colorIndex = childProfile.colorIndex,
                                size = TvDimensions.iconLarge,
                                fontSize = textStyleOf(TextRole.Title).fontSize,
                            )
                            CinemaText(text = childProfile.name, role = TextRole.Title)
                        } else if (!child) {
                            CinemaText(text = stringResource(R.string.login_app_name), role = TextRole.Title)
                        }
                    }
                    if (!child) {
                        Row(
                            // Applies to every button in the row (none is a focus group). The leftover
                            // width, end-aligned: at a large Text & grid size the source pill gives way
                            // (ellipsised) and the icon buttons always stay on screen.
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .padding(start = Spacing.md)
                                    .clearOfTvClock()
                                    .then(
                                        if (firstRowFocus == null) {
                                            Modifier
                                        } else {
                                            Modifier.focusProperties { down = firstRowFocus }
                                        },
                                    ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                        ) {
                            // One line for all the profile's sources (O3): focusable, opening the sources
                            // sheet, only when there is something to say. The header's first focusable
                            // item hands Left to the navigation rail: the line when it opens, else
                            // Search the guide.
                            val summaryOpens = syncSummary.opensSheet
                            if (summaryOpens) {
                                var summaryFocused by remember { mutableStateOf(false) }
                                val summaryScale by animateFloatAsState(
                                    targetValue = if (summaryFocused) TvFocusTokens.focusedScaleSubtle else TvFocusTokens.defaultScale,
                                    animationSpec =
                                        tween(
                                            durationMillis = org.njarasoa.fijerena.core.ui.theme.CinemaAnimation.focusDurationMs,
                                        ),
                                    label = "sources_summary_scale",
                                )
                                val summaryDescription = stringResource(R.string.home_sources_status_title)
                                GlassPanel(
                                    modifier =
                                        Modifier
                                            .weight(1f, fill = false)
                                            .scale(summaryScale)
                                            .border(
                                                width = TvFocusTokens.focusBorderWidth,
                                                color =
                                                    if (summaryFocused) {
                                                        CinemaTheme.colors.accentSoft
                                                    } else {
                                                        CinemaTheme.colors.clear
                                                    },
                                                shape = CinemaTheme.shapes.dialog,
                                            ).leftToRail()
                                            .onFocusChanged { summaryFocused = it.isFocused }
                                            .clickable(role = Role.Button) { showSourcesSheet = true }
                                            .semantics { contentDescription = summaryDescription },
                                ) {
                                    SyncSummaryLine(
                                        summary = syncSummary,
                                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                                    )
                                }
                            }
                            if (hasEpgData) {
                                CinemaIconButton(
                                    onClick = { leaveTo(RETURN_EPG_BROWSER, onEpgBrowser) },
                                    modifier =
                                        Modifier
                                            .then(if (summaryOpens) Modifier else Modifier.leftToRail())
                                            .navReturnFocusTarget(returnFocus, RETURN_EPG_BROWSER),
                                    icon = {
                                        Icon(
                                            imageVector = CinemaIcons.MenuBook,
                                            contentDescription = stringResource(R.string.epg_browser_title),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                val loadedSections = sections
                if (loadedSections != null && loadedSections.merged.isEmpty()) {
                    // No Xtream source: a Browse entry per source (O1); no source at all: Add Source,
                    // or without Expert mode only the words, and focus stays on the rail.
                    HomeSourcesPanel(
                        message =
                            stringResource(
                                when {
                                    !loadedSections.hasNoSources -> R.string.home_only_browse_sources
                                    onAddSource == null -> R.string.home_no_sources_ask
                                    else -> R.string.home_no_sources
                                },
                            ),
                        actions =
                            if (loadedSections.hasNoSources) {
                                listOfNotNull(onAddSource?.let { stringResource(R.string.provider_add_title) to it })
                            } else {
                                loadedSections.browse.map { source ->
                                    stringResource(R.string.browse_source, source.name) to
                                        { onBrowse(source) }
                                }
                            },
                        firstActionFocus = sourcesPanelFocus,
                        scale = scale,
                        modifier = Modifier.weight(1f),
                    )
                } else if (firstRowFocus == null && loadedSections?.xtreamIds.orEmpty().any { it in firstSyncs }) {
                    // The source's first catalogue sync: nothing to show yet, and blank would read as
                    // an empty library. Not focusable; the sections stay on the navigation rail.
                    TvEmptyState(
                        message = stringResource(R.string.home_library_still_loading),
                        modifier = Modifier.weight(1f),
                    )
                } else if (firstRowFocus == null && continueWatchingLoaded && liveRowLoaded) {
                    // Nothing watched or favourited yet (a new profile): say what fills Home, not a
                    // blank screen. Not focusable; the sections are on the rail, which has focus. A
                    // child can't fill it: a grown-up does, from Settings.
                    TvEmptyState(
                        message = stringResource(if (child) R.string.home_kids_empty else R.string.home_empty),
                        icon = CinemaIcons.Home,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    // The rows (TV home overhaul plan, Phase 2; the section tiles went 2026-10-09, the
                    // navigation rail opens the sections). Scrollable: on a lower-density TV the rows
                    // can run past the bottom edge. Scrolled only as far as the focused card needs:
                    // Android TV's default pulls it a third of the way down.
                    // A child's cards one size up (TvDimensions.kidsHomeScale), text and art alike.
                    val density = LocalDensity.current
                    CompositionLocalProvider(
                        LocalBringIntoViewSpec provides MinimalBringIntoView,
                        LocalDensity provides
                            if (child) Density(density.density * TvDimensions.kidsHomeScale, density.fontScale) else density,
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (announced.isNotEmpty()) {
                                TvWantedBanner(
                                    text = pluralStringResource(R.plurals.wanted_banner, announced.size, announced.size),
                                    action = stringResource(R.string.wanted_banner_open),
                                    onClick = {
                                        // To the row first, then the banner goes: it has told its news.
                                        coroutineScope.launch {
                                            // The Wishlist row of the first title it tells about.
                                            val type = announced.first().wantedKey.contentType
                                            val shelf =
                                                wantedShelves.firstOrNull {
                                                    it.wantedContentType == type &&
                                                        wantedOf(it).isNotEmpty()
                                                }
                                            shelf?.let { wantedFirstFocus.getValue(it).requestFocusWithRetry() }
                                            wantedTitles.markNotifiedLater(announced.map { it.wantedKey })
                                        }
                                    },
                                    modifier = Modifier.focusRequester(wantedBannerFocus).leftToRail(),
                                )
                            }

                            if (continueWatchingItems.isNotEmpty()) {
                                TvContinueWatchingShelf(
                                    items = continueWatchingItems,
                                    firstItemFocus = shelfFirstFocus,
                                    restoreFocus = returnFocus.requesterWhere { it.startsWith(RETURN_CONTINUE_WATCHING_PREFIX) },
                                    onItemSelected = { item ->
                                        leaveTo(RETURN_CONTINUE_WATCHING_PREFIX + item.sourceKey) { onContinueWatchingSelected(item) }
                                    },
                                    onOpenActions =
                                        if (child) null else ({ item -> openCardMenu(HomeShelf.CONTINUE_WATCHING, item.toCardItem()) }),
                                    listState = shelfListState,
                                    itemModifier = { item ->
                                        Modifier
                                            .navReturnFocusTarget(returnFocus, RETURN_CONTINUE_WATCHING_PREFIX + item.sourceKey)
                                            .then(cardFocusTarget(HomeShelf.CONTINUE_WATCHING, item.sourceKey))
                                            .then(artOnFocus(item.thumbnailUrl, true))
                                    },
                                    sourceNames = sourceNames,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = Spacing.xl.scaled(scale)),
                                )
                            }

                            // A group per section (user, 2026-10-29): Channels, TV Shows, Movies, each on
                            // its section's tint, its favourites first.
                            if (favoriteChannelEntries.isNotEmpty() || liveRowEntries.isNotEmpty()) {
                                SectionPanel(
                                    section = ThumbnailContentType.LIVE_TV,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = Spacing.xl.scaled(scale)),
                                    // Each row brings its own gap above it; the cards keep off the tint's sides.
                                    contentPadding =
                                        PaddingValues(
                                            start = Spacing.lg.scaled(scale),
                                            end = Spacing.lg.scaled(scale),
                                            bottom = Spacing.xl.scaled(scale),
                                        ),
                                ) {
                                    if (favoriteChannelEntries.isNotEmpty()) {
                                        TvLiveRow(
                                            title = stringResource(R.string.home_favorite_channels),
                                            entries = favoriteChannelEntries,
                                            nowPlaying = liveNowPlaying,
                                            onEntrySelected = { entry ->
                                                openFavorite(entry.item, ContentType.LIVE_TV) {
                                                    leaveTo(RETURN_FAVORITE_CHANNELS_PREFIX + entry.item.sourceKey) {
                                                        onLiveChannelSelected(entry.item.id, zapListId(entry), entry.item.providerId)
                                                    }
                                                }
                                            },
                                            firstItemFocus = favoriteChannelsFirstFocus,
                                            restoreFocus = returnFocus.requesterWhere { it.startsWith(RETURN_FAVORITE_CHANNELS_PREFIX) },
                                            onOpenActions =
                                                if (child) {
                                                    null
                                                } else {
                                                    (
                                                        { entry ->
                                                            openCardMenu(
                                                                HomeShelf.FAVORITE_CHANNELS,
                                                                entry.item.toCardItem(ContentType.LIVE_TV),
                                                            )
                                                        }
                                                    )
                                                },
                                            listState = favoriteChannelsListState,
                                            statusOf = { entry -> goneStatus(entry.item, ContentType.LIVE_TV) },
                                            itemModifier = { entry ->
                                                Modifier
                                                    .navReturnFocusTarget(
                                                        returnFocus,
                                                        RETURN_FAVORITE_CHANNELS_PREFIX + entry.item.sourceKey,
                                                    ).then(cardFocusTarget(HomeShelf.FAVORITE_CHANNELS, entry.item.sourceKey))
                                                    .then(artOnFocus(entry.item.thumbnailUrl, false))
                                            },
                                            sourceNames = sourceNames,
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = Spacing.xl.scaled(scale)),
                                        )
                                    }

                                    if (liveRowEntries.isNotEmpty()) {
                                        TvLiveRow(
                                            title = stringResource(R.string.home_live_row_title),
                                            entries = liveRowEntries,
                                            nowPlaying = liveNowPlaying,
                                            onEntrySelected = { entry ->
                                                leaveTo(RETURN_LIVE_ROW_PREFIX + entry.item.sourceKey) {
                                                    onLiveChannelSelected(entry.item.id, zapListId(entry), entry.item.providerId)
                                                }
                                            },
                                            firstItemFocus = liveRowFirstFocus,
                                            restoreFocus = returnFocus.requesterWhere { it.startsWith(RETURN_LIVE_ROW_PREFIX) },
                                            onOpenActions =
                                                if (child) {
                                                    null
                                                } else {
                                                    (
                                                        { entry ->
                                                            openCardMenu(HomeShelf.CHANNELS, entry.item.toCardItem(ContentType.LIVE_TV))
                                                        }
                                                    )
                                                },
                                            listState = liveRowListState,
                                            itemModifier = { entry ->
                                                Modifier
                                                    .navReturnFocusTarget(returnFocus, RETURN_LIVE_ROW_PREFIX + entry.item.sourceKey)
                                                    .then(cardFocusTarget(HomeShelf.CHANNELS, entry.item.sourceKey))
                                                    .then(artOnFocus(entry.item.thumbnailUrl, false))
                                            },
                                            sourceNames = sourceNames,
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = Spacing.xl.scaled(scale)),
                                        )
                                    }
                                }
                            }

                            if (favoriteShows.isNotEmpty() || wantedOf(HomeShelf.WANTED_SHOWS).isNotEmpty() ||
                                chartShelves.any { it.chart?.contentType == ContentType.TV_SHOWS && chartShows(it) }
                            ) {
                                SectionPanel(
                                    section = ThumbnailContentType.TV_SHOW,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = Spacing.xl.scaled(scale)),
                                    // Each row brings its own gap above it; the cards keep off the tint's sides.
                                    contentPadding =
                                        PaddingValues(
                                            start = Spacing.lg.scaled(scale),
                                            end = Spacing.lg.scaled(scale),
                                            bottom = Spacing.xl.scaled(scale),
                                        ),
                                ) {
                                    if (favoriteShows.isNotEmpty()) {
                                        TvFavoritesRow(
                                            title = stringResource(R.string.home_favorite_shows),
                                            items = favoriteShows,
                                            thumbnailType = ThumbnailContentType.TV_SHOW,
                                            onItemSelected = { item ->
                                                openFavorite(item, ContentType.TV_SHOWS) {
                                                    leaveTo(
                                                        RETURN_FAVORITE_SHOWS_PREFIX + item.sourceKey,
                                                    ) { onFavoriteSelected(item, ContentType.TV_SHOWS) }
                                                }
                                            },
                                            statusOf = { item -> goneStatus(item, ContentType.TV_SHOWS) },
                                            firstItemFocus = favoriteShowsFirstFocus,
                                            restoreFocus = returnFocus.requesterWhere { it.startsWith(RETURN_FAVORITE_SHOWS_PREFIX) },
                                            onOpenActions =
                                                if (child) {
                                                    null
                                                } else {
                                                    (
                                                        { item ->
                                                            openCardMenu(HomeShelf.FAVORITE_SHOWS, item.toCardItem(ContentType.TV_SHOWS))
                                                        }
                                                    )
                                                },
                                            listState = favoriteShowsListState,
                                            itemModifier = { item ->
                                                Modifier
                                                    .navReturnFocusTarget(returnFocus, RETURN_FAVORITE_SHOWS_PREFIX + item.sourceKey)
                                                    .then(cardFocusTarget(HomeShelf.FAVORITE_SHOWS, item.sourceKey))
                                                    .then(artOnFocus(item.thumbnailUrl, true))
                                            },
                                            sourceNames = sourceNames,
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = Spacing.xl.scaled(scale)),
                                        )
                                    }

                                    wantedRow(HomeShelf.WANTED_SHOWS)
                                    for (shelf in chartShelves) {
                                        if (shelf.chart?.contentType == ContentType.TV_SHOWS) chartRow(shelf)
                                    }
                                }
                            }

                            if (favoriteMovies.isNotEmpty() || wantedOf(HomeShelf.WANTED_MOVIES).isNotEmpty() ||
                                chartShelves.any { it.chart?.contentType == ContentType.MOVIES && chartShows(it) }
                            ) {
                                SectionPanel(
                                    section = ThumbnailContentType.MOVIE,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = Spacing.xl.scaled(scale)),
                                    // Each row brings its own gap above it; the cards keep off the tint's sides.
                                    contentPadding =
                                        PaddingValues(
                                            start = Spacing.lg.scaled(scale),
                                            end = Spacing.lg.scaled(scale),
                                            bottom = Spacing.xl.scaled(scale),
                                        ),
                                ) {
                                    if (favoriteMovies.isNotEmpty()) {
                                        TvFavoritesRow(
                                            title = stringResource(R.string.home_favorite_movies),
                                            items = favoriteMovies,
                                            thumbnailType = ThumbnailContentType.MOVIE,
                                            onItemSelected = { item ->
                                                openFavorite(item, ContentType.MOVIES) {
                                                    leaveTo(
                                                        RETURN_FAVORITE_MOVIES_PREFIX + item.sourceKey,
                                                    ) { onFavoriteSelected(item, ContentType.MOVIES) }
                                                }
                                            },
                                            statusOf = { item -> goneStatus(item, ContentType.MOVIES) },
                                            firstItemFocus = favoriteMoviesFirstFocus,
                                            restoreFocus = returnFocus.requesterWhere { it.startsWith(RETURN_FAVORITE_MOVIES_PREFIX) },
                                            onOpenActions =
                                                if (child) {
                                                    null
                                                } else {
                                                    (
                                                        { item ->
                                                            openCardMenu(
                                                                HomeShelf.FAVORITE_MOVIES,
                                                                item.toCardItem(ContentType.MOVIES, watchedItemId = item.id),
                                                            )
                                                        }
                                                    )
                                                },
                                            listState = favoriteMoviesListState,
                                            itemModifier = { item ->
                                                Modifier
                                                    .navReturnFocusTarget(returnFocus, RETURN_FAVORITE_MOVIES_PREFIX + item.sourceKey)
                                                    .then(cardFocusTarget(HomeShelf.FAVORITE_MOVIES, item.sourceKey))
                                                    .then(artOnFocus(item.thumbnailUrl, true))
                                            },
                                            sourceNames = sourceNames,
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = Spacing.xl.scaled(scale)),
                                        )
                                    }

                                    wantedRow(HomeShelf.WANTED_MOVIES)
                                    for (shelf in chartShelves) {
                                        if (shelf.chart?.contentType == ContentType.MOVIES) chartRow(shelf)
                                    }
                                }
                            }
                        }
                    }
                }
                // Under the rows, taking its own room, not over the bottom row's cards.
                TvUndoBar(undoBar, Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.sm))
            }

            if (showSourcesSheet) {
                SourcesStatusSheet(states = syncStates, onDismiss = { showSourcesSheet = false })
            }
        }
    }
}

// Keys for the controls that navigate away from Home — see rememberNavReturnFocus.
private const val RETURN_EPG_BROWSER = "epgBrowser"
private const val RETURN_CONTINUE_WATCHING_PREFIX = "cw:"
private const val RETURN_LIVE_ROW_PREFIX = "live:"
private const val RETURN_FAVORITE_CHANNELS_PREFIX = "favLive:"
private const val RETURN_FAVORITE_MOVIES_PREFIX = "favMovie:"
private const val RETURN_FAVORITE_SHOWS_PREFIX = "favShow:"
private const val RETURN_WANTED_PREFIX = "wanted:"

/** A chart card's key prefix: the chart in it, as a title can be on two charts. */
private fun chartReturnPrefix(shelf: HomeShelf): String = "chart:${shelf.name}:"

/** What a card's menu acts on. */
private class HomeCardItem(
    val id: String,
    val name: String,
    val categoryId: String,
    val contentType: String,
    /** What Mark as watched marks: the film, or the episode a show's card resumes; null: no such action. */
    val watchedItemId: String? = null,
    /** The card's source, which its actions go to. */
    val providerId: Long,
    /** The card's key in its row ([sourceKey]). */
    val key: String,
)

/** A Live, favourite movie or favourite show card's menu target. */
private fun MediaItem.toCardItem(
    contentType: String,
    watchedItemId: String? = null,
): HomeCardItem = HomeCardItem(id, name, categoryId, contentType, watchedItemId, providerId, sourceKey)

/** A Continue Watching card: Mark as watched on a film and on an episode to resume, not on an "Up next" one. */
private fun ContinueWatchingItem.toCardItem(): HomeCardItem =
    HomeCardItem(
        id = id,
        name = name,
        categoryId = categoryId,
        contentType = contentType,
        providerId = providerId,
        key = sourceKey,
        watchedItemId =
            when (val browseTarget = target) {
                is BrowseTarget.Series -> browseTarget.resumeEpisodeId?.raw
                is BrowseTarget.Episode -> browseTarget.episodeId.raw
                else -> id
            }.takeUnless { upNext },
    )

/** The card whose actions menu is open, and what the menu shows. */
private class HomeCardMenu(
    val shelf: HomeShelf,
    val ref: HomeCardItem,
    val target: FavoriteMenuTarget.Stream,
)

/** Focus to move to [card] (null: the navigation rail). A class, not a data class: each request is new. */
private class CardFocusRequest(
    val card: HomeCard?,
)

/** The active profile's favourites of [contentType], none when the source doesn't have that type. */
private suspend fun loadFavorites(
    merged: MergedLists,
    contentType: String,
    supportedContentTypes: Set<String>,
): List<MediaItem> = if (contentType in supportedContentTypes) merged.favorites(contentType) else emptyList()

/**
 * The Channels row for the active profile: last watched — the newest of its sources' last channels
 * (multi-source plan, Phase 5) — then Recent.
 */
private suspend fun loadLiveRow(merged: MergedLists): List<LiveRowEntry> =
    merged.lastWatchedChannel().let { last ->
        mergeLiveRow(lastItemId = last?.id, recent = merged.recent(ContentType.LIVE_TV), lastSource = last?.providerId)
    }

/** The list a Live card zaps through once opened: Favorites from Favorite channels, Recent from Channels. */
private fun zapListId(entry: LiveRowEntry): String =
    if (entry.fromFavorites) CategoryViewModel.FAVORITES_CATEGORY_ID else CategoryViewModel.RECENT_CATEGORY_ID

/** The Favorite channels row for the active profile. */
private suspend fun loadFavoriteChannels(merged: MergedLists): List<LiveRowEntry> =
    favoriteChannelsRow(merged.favorites(ContentType.LIVE_TV))

/** How long Back waits for the Continue Watching shelf to reload before giving up on its card. */
private const val RETURN_SHELF_WAIT_MS = 2_000L

// The chart rows come after every other row is loaded: longer to wait for on a big catalogue.
private const val RETURN_CHART_WAIT_MS = 10_000L

/** How long a fresh open waits for the rows before focusing anyway. */
private const val ENTRY_FOCUS_WAIT_MS = 2_000L

// How much longer the entry focus waits for the chart rows, above the lower groups' rows.
private const val CHART_ENTRY_WAIT_MS = 3_000L

/** How much longer focus waits for a row that comes late (a source switch) before going to the rail. */
private const val LATE_ROWS_WAIT_MS = 6_000L

/** A focused Home card's art: [title] for a film or show (shown crisp), false for a channel's logo. */
private data class HomeArt(
    val url: String,
    val title: Boolean,
)

/**
 * Home's backdrop: the focused card's art once focus has rested on it for [BACKDROP_SETTLE_MS]
 * (so scrolling along a row doesn't flash every card's art), else [fallbackUrl], as the blurred
 * wash; a film's or show's art also shows crisp in the top right, fading into the ground to the
 * left and below (TV visual refresh, Phase 4), crossfading from one title to the next.
 * [focused] is read here, not by the caller, so only this recomposes when focus moves.
 */
@Composable
private fun HomeBackdrop(
    fallbackUrl: String?,
    focused: () -> HomeArt?,
) {
    val target = focused()
    var shown by remember { mutableStateOf<HomeArt?>(null) }
    LaunchedEffect(target) {
        delay(BACKDROP_SETTLE_MS)
        shown = target
    }
    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackdrop(modifier = Modifier.fillMaxSize(), imageUrl = shown?.url ?: fallbackUrl)
        // The title art goes with the wash when the profile turns the poster background off.
        Crossfade(
            targetState = shown?.takeIf { it.title && LocalPosterBackdrop.current }?.url,
            animationSpec = tween(CinemaAnimation.fadeInDurationMs),
            label = "home_art",
        ) { url ->
            if (url != null) TitleArt(url)
        }
    }
}

/**
 * A title's art in the top right of Home, fading out to the left and below (an alpha mask, so the
 * wash behind shows through rather than an edge of flat colour).
 */
@Composable
private fun TitleArt(url: String) {
    val opaque = CinemaTheme.colors.mediaScrim
    val clear = CinemaTheme.colors.clear
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = TITLE_ART_ALPHA,
            modifier =
                Modifier
                    .fillMaxWidth(TITLE_ART_WIDTH)
                    .fillMaxHeight(TITLE_ART_HEIGHT)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.horizontalGradient(listOf(clear, opaque), endX = size.width * TITLE_ART_FADE),
                            blendMode = BlendMode.DstIn,
                        )
                        drawRect(Brush.verticalGradient(listOf(opaque, clear)), blendMode = BlendMode.DstIn)
                    },
        )
    }
}

private const val TITLE_ART_WIDTH = 0.72f
private const val TITLE_ART_HEIGHT = 0.8f
private const val TITLE_ART_ALPHA = 0.6f
private const val TITLE_ART_FADE = 0.55f

/** How long focus rests on a card before the backdrop switches to its art. */
private const val BACKDROP_SETTLE_MS = 250L

/**
 * In place of the rows when the profile has no merged sections: [message] over one button per
 * action — a "Browse <source>" per source (O1), or Add Source with none (no button without Expert
 * mode). Buttons stack, so Up/Down walk them; each hands Left to the navigation rail.
 */
@Composable
private fun HomeSourcesPanel(
    message: String,
    actions: List<Pair<String, () -> Unit>>,
    firstActionFocus: FocusRequester,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CinemaText(text = message, role = TextRole.Title)
        actions.forEachIndexed { index, (label, onClick) ->
            Spacer(modifier = Modifier.height(if (index == 0) Spacing.lg.scaled(scale) else Spacing.sm.scaled(scale)))
            CinemaPrimaryButton(
                onClick = onClick,
                text = label,
                modifier = (if (index == 0) Modifier.focusRequester(firstActionFocus) else Modifier).leftToRail(),
            )
        }
    }
}
