package org.njarasoa.fijerena.feature.home

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.wanted.GoneFavorite
import org.njarasoa.fijerena.core.network.wanted.TmdbOnlyItem
import org.njarasoa.fijerena.core.network.wanted.WantedTitles
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.ContinueWatchingItem
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.episodeTitleWithoutSeries
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.player.domain.sourceKey
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.formatDuration
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaProgressBar
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.SectionPanel
import org.njarasoa.fijerena.core.ui.components.SourceBadge
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.TitleBadges
import org.njarasoa.fijerena.core.ui.history.MergedLists
import org.njarasoa.fijerena.core.ui.home.ChartRows
import org.njarasoa.fijerena.core.ui.home.LiveRowEntry
import org.njarasoa.fijerena.core.ui.home.favoriteChannelsRow
import org.njarasoa.fijerena.core.ui.home.mergeLiveRow
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.utils.launchGuarded
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.core.ui.wanted.NotAvailableMark
import org.njarasoa.fijerena.core.ui.wanted.WantedCard
import org.njarasoa.fijerena.core.ui.wanted.cards
import org.njarasoa.fijerena.core.ui.wanted.gone
import org.njarasoa.fijerena.core.ui.wanted.route
import org.njarasoa.fijerena.core.ui.wanted.statusLabel
import org.njarasoa.fijerena.core.ui.wanted.toAnnounce
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.ui.components.AmbientBackdrop
import org.njarasoa.fijerena.ui.components.ItemActionsDialog
import org.njarasoa.fijerena.ui.components.ItemMenuAction
import org.njarasoa.fijerena.ui.components.MobileEmptyState
import org.njarasoa.fijerena.ui.components.UndoSnackbarHost
import org.njarasoa.fijerena.ui.components.buttons.IconAction
import org.njarasoa.fijerena.ui.components.cards.CinemaCard
import org.njarasoa.fijerena.ui.components.rememberUndoSnackbar
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing

/** Home's rows, read together (docs/plans/20261019_phone-home-tab-plan.md). */
private data class HomeRows(
    val continueWatching: List<ContinueWatchingItem>,
    val channels: List<LiveRowEntry>,
    val favoriteChannels: List<LiveRowEntry>,
    val favoriteMovies: List<MediaItem>,
    val favoriteShows: List<MediaItem>,
    /** Each source's name by id when the rows mix more than one, else empty. */
    val sourceNames: Map<Long, String>,
) {
    val isEmpty: Boolean
        get() =
            continueWatching.isEmpty() && channels.isEmpty() && favoriteChannels.isEmpty() &&
                favoriteMovies.isEmpty() && favoriteShows.isEmpty()
}

/**
 * The phone's Home tab: TV Home's rows for the profile's merged sources, phone-sized — Continue
 * watching, Channels, the Wanted list, each section's TMDB chart rows, then the favourites — each
 * hidden when empty, read again on every return. The chart rows load on their own so they never
 * hold Home up. [supportedTypes]: the profile's merged
 * sections; a row of a section they don't have stays empty. A card opens through its callback:
 * [onChannel] with the list it zaps through (Recent or Favourites).
 */
@Composable
fun MobileHomeScreen(
    supportedTypes: Set<String>,
    sourceTopBar: @Composable (onSearch: (() -> Unit)?) -> Unit,
    onSearch: () -> Unit,
    onContinueWatching: (ContinueWatchingItem) -> Unit,
    onChannel: (streamId: String, listId: String, providerId: Long) -> Unit,
    onFavorite: (item: MediaItem, contentType: String) -> Unit,
    // A Wanted card: its source copy's page when a source has it, else its TMDB page.
    onWanted: (Screen) -> Unit = {},
) {
    val context = LocalContext.current
    var rows by remember { mutableStateOf<HomeRows?>(null) }
    // The Wanted list (wanted titles plan, Phase 5): its own row, last; empty on a child's profile.
    val wantedTitles = remember { WantedTitles.getInstance(context.applicationContext) }
    val wantedState by wantedTitles.state.collectAsStateWithLifecycle()
    // Home is for what can be played: only the titles a source has (user, 2026-10-22); the
    // others wait in the sections' Wishlist lists.
    val wantedCards = remember(wantedState) { wantedState.cards().filter { it.isAvailable } }
    // What the banner announces: available titles no banner has told about yet, marked told (on
    // every device) once the banner was on screen: on a tap, or when Home is left after showing it.
    val announced = remember(wantedState) { wantedState.toAnnounce() }
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
    val wantedScope = rememberCoroutineScope()

    var nowPlaying by remember { mutableStateOf<Map<String, EpgProgram>>(emptyMap()) }
    // Bumped on each return (from playback, details, another tab): progress and favourites changed.
    var reloads by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reloads++ }
    var merged by remember { mutableStateOf<MergedLists?>(null) }

    // Favourites their source no longer lists (wanted titles plan → Favourites gone from their
    // source): marked on their cards; opened, a menu moves one to its other copy, else a message.
    var goneMenu by remember { mutableStateOf<Triple<MediaItem, String, GoneFavorite>?>(null) }
    goneMenu?.let { (item, type, gone) ->
        val copy = gone.copy
        ItemActionsDialog(
            title = stringResource(R.string.favorite_gone_message, parseDisplayTitle(item.name).title.ifBlank { item.name }),
            isFavorite = true,
            onToggleFavorite = null,
            onDismiss = { goneMenu = null },
            actions =
                listOf(
                    ItemMenuAction(CinemaIcons.SwapHoriz, stringResource(R.string.favorite_move_to_copy)) {
                        val lists = merged
                        if (lists != null && copy != null) {
                            // The user's move: the copy becomes the favourite, then the old one goes.
                            wantedScope.launchGuarded("Home.moveToCopy") {
                                lists.repoFor(copy.providerId).addFavoriteSuspend(copy.itemId, copy.name, copy.categoryId, type)
                                lists.repoFor(gone.providerId).removeFavoriteSuspend(item.id, type)
                                wantedTitles.refresh()
                                reloads++
                            }
                        }
                    },
                ),
        )
    }
    val goneLabel = stringResource(R.string.favorite_gone_from_source)
    val resources = LocalResources.current
    val gone: (MediaItem, String) -> GoneFavorite? = { item, type -> wantedState.gone(item.providerId, item.id, type) }
    val openFavorite: (MediaItem, String, () -> Unit) -> Unit = { item, type, open ->
        val found = gone(item, type)
        when {
            found?.copy != null -> {
                goneMenu = Triple(item, type, found)
            }

            found != null -> {
                val name = parseDisplayTitle(item.name).title.ifBlank { item.name }
                Toast.makeText(context, resources.getString(R.string.favorite_gone_message, name), Toast.LENGTH_LONG).show()
            }

            else -> {
                open()
            }
        }
    }
    LaunchedEffect(reloads, supportedTypes) {
        val lists = withContext(Dispatchers.IO) { MergedLists.forSections(context.applicationContext) } ?: return@LaunchedEffect
        merged = lists
        val settings = AppSettings(context.applicationContext)
        rows = loadRows(lists, supportedTypes, child = settings.isChildProfile(settings.activeProfileId))
    }
    // The TMDB chart rows (docs/plans/20261029_tmdb-charts-plan.md, Phase 4): read apart from the
    // other rows, as they may ask TMDB, and again when the Admin switch or the Trending rows' switch
    // (follow-up F3: the titles no source has) changes. None for a child.
    val showCharts by remember { AppSettings(context.applicationContext).observeShowTmdbCharts() }
        .collectAsStateWithLifecycle(initialValue = false)
    val chartsShowMissing by remember {
        AppSettings(context.applicationContext).let { it.observeChartsShowMissing(it.activeProfileId) }
    }.collectAsStateWithLifecycle(initialValue = false)
    var charts by remember { mutableStateOf<List<ChartRows.Row>>(emptyList()) }
    // Admin → Refresh Charts: read again.
    val chartRefreshes by ChartRows.refreshes.collectAsStateWithLifecycle()
    LaunchedEffect(merged, supportedTypes, showCharts, chartsShowMissing, chartRefreshes) {
        val lists = merged ?: return@LaunchedEffect
        if (!showCharts) {
            charts = emptyList()
            return@LaunchedEffect
        }
        charts =
            withContext(Dispatchers.IO) {
                listOf(ContentType.MOVIES, ContentType.TV_SHOWS)
                    .filter { it in supportedTypes }
                    .flatMap { ChartRows.load(context.applicationContext, lists, it) }
            }
    }
    // The channels' Now lines, each from its own source's guide, again at each minute.
    LaunchedEffect(merged, rows) {
        val lists = merged ?: return@LaunchedEffect
        val loaded = rows ?: return@LaunchedEffect
        val items = (loaded.channels + loaded.favoriteChannels).map { it.item }.distinctBy { it.sourceKey }
        if (items.isEmpty()) return@LaunchedEffect
        while (true) {
            val now = HashMap<String, EpgProgram>()
            for ((source, channels) in items.groupBy { it.providerId }) now += lists.repoFor(source).getNowPlayingFromIndex(channels)
            nowPlaying = now
            val nowMs = System.currentTimeMillis()
            delay(DateUtils.MINUTE_IN_MILLIS - nowMs % DateUtils.MINUTE_IN_MILLIS)
        }
    }

    // Long-press on a card: its menu, as on TV Home, each removal with the Undo snackbar (wishlist
    // UX plan, Phase 6). None on a child's Home.
    val undo = rememberUndoSnackbar()
    val child = remember { AppSettings(context.applicationContext).let { it.isChildProfile(it.activeProfileId) } }
    var homeMenu by remember { mutableStateOf<HomeMenu?>(null) }
    homeMenu?.let { menu ->
        val lists = merged
        val done = { reloads++ }
        when (menu) {
            is HomeMenu.Recent -> {
                ItemActionsDialog(
                    title = parseDisplayTitle(menu.name).title.ifBlank { menu.name },
                    isFavorite = false,
                    onToggleFavorite = null,
                    onDismiss = { homeMenu = null },
                    actions =
                        listOf(
                            ItemMenuAction(CinemaIcons.Delete, stringResource(R.string.recent_remove)) {
                                if (lists != null) {
                                    wantedScope.launchGuarded("Home.removeFromRecent") {
                                        val removal = lists.removeFromRecent(menu.providerId, menu.itemId, menu.contentType)
                                        done()
                                        undo.show(menu.name) {
                                            wantedScope.launchGuarded("Home.undoRemoveFromRecent") {
                                                removal?.let { lists.restoreRecent(it) }
                                                done()
                                            }
                                        }
                                    }
                                }
                            },
                        ),
                )
            }

            is HomeMenu.Favorite -> {
                val item = menu.item
                ItemActionsDialog(
                    title = parseDisplayTitle(item.name).title.ifBlank { item.name },
                    isFavorite = menu.isFavorite,
                    onToggleFavorite = {
                        if (lists != null) {
                            val repo = lists.repoFor(item.providerId)
                            if (!menu.isFavorite) {
                                // A chart card that is not a favourite yet.
                                wantedScope.launchGuarded("Home.addFavorite") {
                                    repo.addFavoriteSuspend(item.id, item.name, item.categoryId, menu.contentType)
                                    done()
                                }
                            } else {
                                wantedScope.launchGuarded("Home.removeFavorite") {
                                    repo.removeFavoriteSuspend(item.id, menu.contentType)
                                    done()
                                    undo.show(item.name) {
                                        wantedScope.launchGuarded("Home.undoRemoveFavorite") {
                                            repo.addFavoriteSuspend(item.id, item.name, item.categoryId, menu.contentType)
                                            done()
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onDismiss = { homeMenu = null },
                )
            }

            is HomeMenu.Wishlist -> {
                val card = menu.card
                ItemActionsDialog(
                    title = card.entity.title,
                    isFavorite = false,
                    onToggleFavorite = null,
                    onDismiss = { homeMenu = null },
                    actions =
                        listOf(
                            ItemMenuAction(CinemaIcons.Delete, stringResource(R.string.wanted_remove)) {
                                wantedScope.launchGuarded("Home.removeWanted") {
                                    wantedTitles.remove(card.wantedKey)
                                    undo.show(card.entity.title) {
                                        wantedScope.launchGuarded("Home.undoRemoveWanted") { wantedTitles.add(card.entity) }
                                    }
                                }
                            },
                        ),
                )
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        // The plain wash: a poster's would sit pale behind the first row's heading.
        AmbientBackdrop(modifier = Modifier.fillMaxSize())
        Scaffold(
            containerColor = CinemaTheme.colors.clear,
            snackbarHost = { UndoSnackbarHost(undo) },
            topBar = { sourceTopBar(onSearch) },
        ) { padding ->
            val loaded = rows ?: return@Scaffold
            if (loaded.isEmpty && wantedCards.isEmpty() && charts.isEmpty()) {
                MobileEmptyState(
                    message = stringResource(R.string.home_empty),
                    icon = CinemaIcons.Home,
                    modifier = Modifier.padding(padding),
                )
                return@Scaffold
            }
            HomeRowsColumn(
                loaded,
                charts,
                nowPlaying,
                padding,
                onContinueWatching,
                onChannel = { id, listId, source ->
                    val item =
                        (loaded.favoriteChannels + loaded.channels).map { it.item }.firstOrNull {
                            it.id == id &&
                                it.providerId == source
                        }
                    if (item !=
                        null
                    ) {
                        openFavorite(item, ContentType.LIVE_TV) { onChannel(id, listId, source) }
                    } else {
                        onChannel(id, listId, source)
                    }
                },
                onFavorite = { item, type -> openFavorite(item, type) { onFavorite(item, type) } },
                // A TMDB-only card (follow-up F3) opens the title's TMDB page.
                onChart = { item, type -> TmdbOnlyItem.titleOf(item, type)?.let { onWanted(it.route()) } ?: onFavorite(item, type) },
                onShowMissing = { show -> ChartRows.setShowMissing(context.applicationContext, show) },
                goneStatus = { item, type -> goneLabel.takeIf { gone(item, type) != null } },
                wanted =
                    WantedRow(wantedCards, announced) { card ->
                        if (card.isNew) wantedScope.launchGuarded("Home.wantedSeen") { wantedTitles.markSeen(card.wantedKey) }
                        onWanted(card.route)
                    },
                onBannerSeen = { wantedTitles.markNotifiedLater(announced.map { it.wantedKey }) },
                onMenu = { menu -> if (!child) homeMenu = menu },
            )
        }
    }
}

@Composable
private fun HomeRowsColumn(
    rows: HomeRows,
    charts: List<ChartRows.Row>,
    nowPlaying: Map<String, EpgProgram>,
    padding: PaddingValues,
    onContinueWatching: (ContinueWatchingItem) -> Unit,
    onChannel: (streamId: String, listId: String, providerId: Long) -> Unit,
    onFavorite: (item: MediaItem, contentType: String) -> Unit,
    // A chart card: opened as a favourite is, without the Gone from source check.
    onChart: (item: MediaItem, contentType: String) -> Unit,
    // A Trending row's switch: also the titles no source has, or not.
    onShowMissing: (Boolean) -> Unit,
    wanted: WantedRow,
    onBannerSeen: () -> Unit,
    // A favourite card's short state: Gone from source when its source no longer lists it.
    goneStatus: (MediaItem, String) -> String?,
    // A card's long-press: its menu.
    onMenu: (HomeMenu) -> Unit,
) {
    val channelCard: @Composable (LiveRowEntry) -> Unit = { entry ->
        val listId = if (entry.fromFavorites) CategoryViewModel.FAVORITES_CATEGORY_ID else CategoryViewModel.RECENT_CATEGORY_ID
        ChannelCard(
            entry,
            nowPlaying[entry.item.id],
            rows.sourceNames[entry.item.providerId],
            goneStatus(entry.item, ContentType.LIVE_TV),
            onLongClick = {
                onMenu(
                    if (entry.fromFavorites) {
                        HomeMenu.Favorite(entry.item, ContentType.LIVE_TV)
                    } else {
                        HomeMenu.Recent(entry.item.providerId, entry.item.id, ContentType.LIVE_TV, entry.item.name)
                    },
                )
            },
        ) {
            onChannel(entry.item.id, listId, entry.item.providerId)
        }
    }
    val favoritesRow: @Composable (List<MediaItem>, String, Int) -> Unit = { items, contentType, title ->
        HomeRow(stringResource(title), items, { it.sourceKey }) { item ->
            TitleCard(
                title = item.name,
                subtitle = goneStatus(item, contentType),
                thumbnailUrl = item.thumbnailUrl,
                thumbnailType = if (contentType == ContentType.TV_SHOWS) ThumbnailContentType.TV_SHOW else ThumbnailContentType.MOVIE,
                progress = null,
                sourceName = rows.sourceNames[item.providerId],
                providerId = item.providerId,
                onLongClick = { onMenu(HomeMenu.Favorite(item, contentType)) },
            ) { onFavorite(item, contentType) }
        }
    }
    // A section's chart rows: the same cards as its favourites; the long-press menu has the
    // favourite switch only. A tap opens the title on the card's own source. A Trending row has its
    // switch at the end of its title, and shows with no card so the switch stays (follow-up F3); its
    // TMDB-only cards are marked Not available, open the TMDB page and have no menu.
    val chartRows: @Composable (String, List<MediaItem>) -> Unit = { contentType, favorites ->
        for (chart in charts.filter { it.chart.contentType == contentType }) {
            val action: (@Composable () -> Unit)? =
                if (chart.chart.canShowMissing) {
                    {
                        IconAction(
                            onClick = { onShowMissing(!chart.showsMissing) },
                            icon = if (chart.showsMissing) CinemaIcons.VisibilityOff else CinemaIcons.Visibility,
                            label = stringResource(if (chart.showsMissing) R.string.chart_hide_missing else R.string.chart_show_missing),
                            // Not the default content colour: Home has no surface under it, so the
                            // icon was drawn black on the dark ground. The accent while it is on.
                            tint = if (chart.showsMissing) CinemaTheme.colors.accent else CinemaTheme.colors.textPrimary,
                        )
                    }
                } else {
                    null
                }
            HomeRow(
                stringResource(ChartRows.titleRes(chart.chart)),
                chart.items,
                { it.sourceKey },
                action = action,
                showEmpty = chart.chart.canShowMissing,
            ) { item ->
                val tmdbOnly = item.id.startsWith(TmdbOnlyItem.PREFIX)
                TitleCard(
                    title = item.listTitle ?: item.name,
                    subtitle = null,
                    thumbnailUrl = item.thumbnailUrl,
                    thumbnailType = if (contentType == ContentType.TV_SHOWS) ThumbnailContentType.TV_SHOW else ThumbnailContentType.MOVIE,
                    progress = null,
                    sourceName = rows.sourceNames[item.providerId],
                    providerId = item.providerId,
                    onLongClick =
                        if (tmdbOnly) {
                            null
                        } else {
                            {
                                val isFavorite = favorites.any { it.sourceKey == item.sourceKey }
                                onMenu(HomeMenu.Favorite(item, contentType, isFavorite))
                            }
                        },
                    notAvailable = tmdbOnly,
                    tags = item.listTitle == null,
                ) { onChart(item, contentType) }
            }
        }
    }
    // The Wishlist rows: one per section, each brought into view by the banner.
    val wantedInView = remember { mapOf(ContentType.TV_SHOWS to BringIntoViewRequester(), ContentType.MOVIES to BringIntoViewRequester()) }
    val wantedTitle = stringResource(R.string.wanted_title)
    // A section's group: its favourites, its Wishlist titles, then its charts; none when all are empty.
    val sectionRows: @Composable (String, List<MediaItem>, Int) -> Unit = { contentType, favorites, title ->
        val wantedCards = wanted.cards.filter { it.entity.contentType == contentType }
        val sectionCharts = charts.filter { it.chart.contentType == contentType }
        if (favorites.isNotEmpty() || wantedCards.isNotEmpty() || sectionCharts.any { it.items.isNotEmpty() || it.chart.canShowMissing }) {
            HomeSection(if (contentType == ContentType.TV_SHOWS) ThumbnailContentType.TV_SHOW else ThumbnailContentType.MOVIE) {
                favoritesRow(favorites, contentType, title)
                // Only with cards: an empty box would still take a gap.
                if (wantedCards.isNotEmpty()) {
                    Box(Modifier.bringIntoViewRequester(wantedInView.getValue(contentType))) {
                        val sectionNew = wantedCards.count { it.isNew }
                        HomeRow(
                            if (sectionNew > 0) stringResource(R.string.wanted_title_new_format, wantedTitle, sectionNew) else wantedTitle,
                            wantedCards,
                            { it.key },
                        ) { card -> WantedTitleCard(card, onLongClick = { onMenu(HomeMenu.Wishlist(card)) }) { wanted.onOpen(card) } }
                    }
                }
                chartRows(contentType, favorites)
            }
        }
    }
    val scrollState = rememberScrollState()
    val scrollScope = rememberCoroutineScope()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(scrollState)
                // Ends with the bar's height so the last row scrolls clear of it.
                .padding(bottom = Spacing.md + LocalDockInset.current),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        if (wanted.announced.isNotEmpty()) {
            // The news: tapping it brings the Wishlist row of the first title it tells about into view.
            WantedBanner(pluralStringResource(R.plurals.wanted_banner, wanted.announced.size, wanted.announced.size)) {
                onBannerSeen()
                val type =
                    wanted.announced
                        .first()
                        .entity.contentType
                scrollScope.launch { wantedInView[type]?.bringIntoView() }
            }
        }
        HomeRow(stringResource(R.string.series_continue_watching_badge), rows.continueWatching, { it.sourceKey }) { item ->
            TitleCard(
                title = item.name,
                subtitle = continueWatchingLine(item),
                thumbnailUrl = item.thumbnailUrl,
                thumbnailType = if (item.contentType == ContentType.TV_SHOWS) ThumbnailContentType.TV_SHOW else ThumbnailContentType.MOVIE,
                progress = item.progress.takeUnless { item.upNext },
                sourceName = rows.sourceNames[item.providerId],
                providerId = item.providerId,
                onLongClick = { onMenu(HomeMenu.Recent(item.providerId, item.id, item.contentType, item.name)) },
            ) { onContinueWatching(item) }
        }
        // A group per section (user, 2026-10-29): Channels, TV Shows, Movies, each on its
        // section's tint, its favourites first.
        if (rows.favoriteChannels.isNotEmpty() || rows.channels.isNotEmpty()) {
            HomeSection(ThumbnailContentType.LIVE_TV) {
                HomeRow(
                    stringResource(R.string.home_favorite_channels),
                    rows.favoriteChannels,
                    { it.item.sourceKey },
                    content = channelCard,
                )
                HomeRow(stringResource(R.string.home_live_row_title), rows.channels, { it.item.sourceKey }, content = channelCard)
            }
        }
        sectionRows(ContentType.TV_SHOWS, rows.favoriteShows, R.string.home_favorite_shows)
        sectionRows(ContentType.MOVIES, rows.favoriteMovies, R.string.home_favorite_movies)
    }
}

/** A section's group of rows on Home: its tint behind them, the rows' own gap between them. */
@Composable
private fun HomeSection(
    section: ThumbnailContentType,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionPanel(
        section = section,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        content = content,
    )
}

/**
 * A Home card's long-press menu: Remove from Recent, from Favourites, or from the Wishlist. A chart
 * card has the Favourite menu, with [Favorite.isFavorite] false when it is not a favourite yet.
 */
private sealed interface HomeMenu {
    class Recent(
        val providerId: Long,
        val itemId: String,
        val contentType: String,
        val name: String,
    ) : HomeMenu

    class Favorite(
        val item: MediaItem,
        val contentType: String,
        val isFavorite: Boolean = true,
    ) : HomeMenu

    class Wishlist(
        val card: WantedCard,
    ) : HomeMenu
}

/** Home's Wanted row and banner: the cards, what the banner announces, and what opening a card does. */
private class WantedRow(
    val cards: List<WantedCard>,
    val announced: List<WantedCard>,
    val onOpen: (WantedCard) -> Unit,
)

/** A wanted title: its poster, its name and Available or Not available; dimmed while no source has it. */
@Composable
private fun WantedTitleCard(
    card: WantedCard,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {
    val width = MobileDimensions.homeCardWidth
    CinemaCard(
        modifier =
            Modifier
                .width(width)
                .homeCardClicks(onClick, onLongClick)
                .alpha(if (card.isAvailable) 1f else CinemaAlpha.textDisabled),
    ) {
        CinemaThumbnail(
            url = card.posterUrl,
            fallbackLetter = card.listTitle.firstOrNull(),
            contentType = ThumbnailContentType.MOVIE,
            overlayGradient = true,
            modifier = Modifier.width(width).aspectRatio(CARD_ASPECT),
        )
        // "Available · panel-a · FR" (wishlist UX plan, Phase 5).
        val status = card.statusLabel(LocalResources.current)
        CardLines(card.listTitle, listOfNotNull(status, card.entity.year?.toString()).joinToString(" • "), null, 0L, width, tags = false)
    }
}

/** "2 titles you wanted are now available", once; tapping it goes to the Wanted row. */
@Composable
private fun WantedBanner(
    text: String,
    onClick: () -> Unit,
) {
    CinemaCard(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = CinemaIcons.Bookmark, contentDescription = null, tint = CinemaTheme.colors.accent)
            CinemaText(text = text, role = TextRole.Body, modifier = Modifier.weight(1f))
            CinemaText(text = stringResource(R.string.wanted_banner_open), role = TextRole.Label, tone = TextTone.Accent)
        }
    }
}

/**
 * One titled row of cards; nothing at all when [items] is empty, unless [showEmpty] (then its title
 * alone). [action]: an icon button at the end of the title line.
 */
@Composable
internal fun <T> HomeRow(
    title: String,
    items: List<T>,
    key: (T) -> String,
    action: (@Composable () -> Unit)? = null,
    showEmpty: Boolean = false,
    content: @Composable (T) -> Unit,
) {
    if (items.isEmpty() && !showEmpty) return
    // A card put back at the start (an Undo) would sit off screen to the left: the row keeps its
    // first visible card. While the row is at its start, it shows the new first card.
    val listState = rememberLazyListState()
    val firstKey = items.firstOrNull()?.let(key)
    LaunchedEffect(firstKey) { if (listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0) }
    Column {
        if (action == null) {
            CinemaText(text = title, role = TextRole.Heading, modifier = Modifier.padding(horizontal = Spacing.md))
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CinemaText(text = title, role = TextRole.Heading, modifier = Modifier.weight(1f))
                action()
            }
        }
        if (items.isEmpty()) return@Column
        Spacer(modifier = Modifier.height(Spacing.sm))
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            contentPadding = PaddingValues(horizontal = Spacing.md),
        ) {
            items(items, key = key) { content(it) }
        }
    }
}

/**
 * A film or show: its art in the same 16:9 card as a channel (as on TV Home), resume progress under
 * it when given, its name and [subtitle].
 */
@Composable
internal fun TitleCard(
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    thumbnailType: ThumbnailContentType,
    progress: Float?,
    sourceName: String?,
    providerId: Long,
    // Null: no long-press menu (a TMDB-only card).
    onLongClick: (() -> Unit)?,
    // A TMDB title no source has: the Not available mark on its art.
    notAvailable: Boolean = false,
    // False: [title] is a TMDB list's title (MediaItem.listTitle), shown as it is, without tags.
    tags: Boolean = true,
    onClick: () -> Unit,
) {
    val width = MobileDimensions.homeCardWidth
    CinemaCard(modifier = Modifier.width(width).homeCardClicks(onClick, onLongClick)) {
        Box {
            CinemaThumbnail(
                url = thumbnailUrl,
                fallbackLetter = title.firstOrNull(),
                contentType = thumbnailType,
                overlayGradient = true,
                modifier = Modifier.width(width).aspectRatio(CARD_ASPECT),
            )
            if (notAvailable) {
                NotAvailableMark(
                    iconSize = MobileDimensions.iconSmall,
                    modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xxs),
                )
            }
        }
        if (progress != null) {
            CinemaProgressBar(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(MobileDimensions.resumeBarHeight),
            )
        } else {
            // No bar, but its height, so the cards in a row line up.
            Spacer(modifier = Modifier.height(MobileDimensions.resumeBarHeight))
        }
        CardLines(title, subtitle, sourceName, providerId, width, tags)
    }
}

/** A channel: its logo, its name and what is on now (or Last watched). */
@Composable
private fun ChannelCard(
    entry: LiveRowEntry,
    now: EpgProgram?,
    sourceName: String?,
    // Gone from source, first on the second line.
    status: String?,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {
    val width = MobileDimensions.homeCardWidth
    val subtitle =
        listOfNotNull(
            status,
            now?.let { stringResource(R.string.epg_now_prefix, it.title) }
                ?: if (entry.lastWatched) stringResource(R.string.home_live_last_watched) else null,
        ).joinToString(" • ").ifEmpty { null }
    CinemaCard(modifier = Modifier.width(width).homeCardClicks(onClick, onLongClick)) {
        CinemaThumbnail(
            url = entry.item.thumbnailUrl,
            fallbackLetter = entry.item.name.firstOrNull(),
            contentType = ThumbnailContentType.LIVE_TV,
            modifier = Modifier.width(width).aspectRatio(CARD_ASPECT),
        )
        CardLines(entry.item.name, subtitle, sourceName, entry.item.providerId, width)
    }
}

/**
 * A card's clean name ("From (2022)", not "ES - From (2022)"), then its source (Expert mode), the
 * name's tags as badges and [subtitle] on one line — as TV Home's cards and the lists show it.
 * Without [tags], [title] shows as it is and has no badges: a TMDB list's title.
 */
@Composable
private fun CardLines(
    title: String,
    subtitle: String?,
    sourceName: String?,
    providerId: Long,
    width: Dp,
    tags: Boolean = true,
) {
    val parsed = remember(title) { parseDisplayTitle(title) }
    Column(modifier = Modifier.width(width).padding(Spacing.xs)) {
        CinemaText(
            text = if (tags) parsed.title.ifBlank { title } else title,
            role = TextRole.Heading,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            sourceName?.let { SourceBadge(it, providerId = providerId) }
            if (tags) TitleBadges(parsed)
            CinemaText(
                text = subtitle.orEmpty(),
                role = TextRole.Caption,
                tone = TextTone.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "S18E01 • 35m left", "Up next • S18E02" or, for a film, "35m left": a card's width holds no more. */
@Composable
private fun continueWatchingLine(item: ContinueWatchingItem): String {
    // The episode's title repeats the show's name; its number stands for it when there is one.
    val episode =
        item.subtitle?.let { subtitle ->
            val own = episodeTitleWithoutSeries(subtitle, item.name)
            EPISODE_NUMBER.find(own)?.value ?: own
        }
    if (item.upNext) {
        val upNext = stringResource(R.string.continue_watching_up_next)
        return episode?.let { "$upNext • $it" } ?: upNext
    }
    val timeLeft = formatDuration((item.remainingMs / 1000).toString())
    val left = stringResource(R.string.tv_home_time_left_format, timeLeft)
    return episode?.let { "$it • $left" } ?: left
}

/**
 * Every row from stored data: an offline source holds nothing up. A [child] profile's Home has
 * only Continue watching and its favourite channels, films and shows: no Channels (recent) row
 * (docs/plans/20261019_kids-home-plan.md, K-D8, the phone's light version).
 */
private suspend fun loadRows(
    merged: MergedLists,
    supportedTypes: Set<String>,
    child: Boolean,
): HomeRows =
    withContext(Dispatchers.IO) {
        val hasLive = ContentType.LIVE_TV in supportedTypes
        val last = if (hasLive) merged.lastWatchedChannel() else null
        HomeRows(
            continueWatching = merged.continueWatching(),
            channels =
                if (hasLive && !child) {
                    mergeLiveRow(lastItemId = last?.id, recent = merged.recent(ContentType.LIVE_TV), lastSource = last?.providerId)
                } else {
                    emptyList()
                },
            favoriteChannels = if (hasLive) favoriteChannelsRow(merged.favorites(ContentType.LIVE_TV)) else emptyList(),
            favoriteMovies = if (ContentType.MOVIES in supportedTypes) merged.favorites(ContentType.MOVIES) else emptyList(),
            favoriteShows = if (ContentType.TV_SHOWS in supportedTypes) merged.favorites(ContentType.TV_SHOWS) else emptyList(),
            sourceNames = merged.sourceNames,
        )
    }

private const val CARD_ASPECT = 16f / 9f
private val EPISODE_NUMBER = Regex("""S\d{1,3}\s?E\d{1,4}""", RegexOption.IGNORE_CASE)

/** A Home card's tap and long-press (its menu, none when null), with the long-press named for accessibility. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.homeCardClicks(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
): Modifier =
    combinedClickable(
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = onLongClick?.let { stringResource(R.string.row_more_actions) },
    )
