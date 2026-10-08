package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.GuideSource
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.friendlyErrorMessage
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.isCategoryMarker
import org.njarasoa.fijerena.core.player.model.EpgChannelRow
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.TimeSlot
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.utils.launchGuarded
import java.time.LocalDate
import java.time.ZoneId

class EpgViewModel(
    private val context: Context,
    private val categoryId: String,
) : ViewModel() {
    sealed class UiState {
        data object Loading : UiState()

        /**
         * The grid: every channel of the list, in [channelRows]; listings are loaded a page of
         * [PAGE_SIZE] rows at a time (GD4), so the rows of a page not loaded yet have no programmes.
         * [listedCount] of the [loadedCount] loaded rows have at least one programme on
         * [selectedDate] — a channel that answered with nothing is not listed; [totalCount] is
         * every channel. [lastListingEndSec] is the latest end among the loaded day's listings.
         */
        data class Ready(
            val channelRows: List<EpgChannelRow>,
            val timeSlots: List<TimeSlot>,
            val currentTimeSlot: Int,
            val selectedDate: LocalDate,
            val listedCount: Int,
            val totalCount: Int,
            val source: GuideSource,
            val updatedAtMs: Long?,
            /** Dev mode only: channels that answered, pages loaded, first page's load time. */
            val devStats: String,
            val loadedCount: Int = totalCount,
            val lastListingEndSec: Long? = null,
            /** Indexes of the pages loaded; null: all of them. */
            val loadedPages: Set<Int>? = null,
        ) : UiState() {
            /** Whether row [index]'s page has loaded, so an empty row means "no listings", not "not yet". */
            fun isRowLoaded(index: Int): Boolean = loadedPages?.contains(index / PAGE_SIZE) ?: true
        }

        /** Channels found, but not one of them has a programme on [selectedDate]. */
        data class NoListings(
            val reason: NoListingsReason,
            val selectedDate: LocalDate,
            /** The layer that answered, when one did; null when neither had anything. */
            val source: GuideSource?,
            val updatedAtMs: Long?,
        ) : UiState()

        /** This source has no guide source and no native EPG: Settings → Source & guide. */
        data object NoGuide : UiState()

        /** The list has no channels to show a guide for — nothing failed. */
        data object NoChannels : UiState()

        data class Error(
            val message: String,
        ) : UiState()
    }

    enum class NoListingsReason {
        /** The index is built but holds nothing for these channels. */
        INDEX_EMPTY,

        /** Listings exist but none fall on the selected day — the data stops before it. */
        STALE,

        /** Neither the index nor the source's own EPG has anything for these channels. */
        NONE,
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Lazily initialized in init coroutine to avoid blocking the UI thread
    private lateinit var repository: MediaRepository

    private var currentDate = LocalDate.now()

    // The whole channel list (ids, names, logos), resolved once and kept across day changes;
    // Refresh resolves it again. Listings come per page, cached per (day, page) by the pager.
    private var channels: List<MediaItem>? = null
    private val pager = GuidePager(PAGE_SIZE) { date, items -> loadPage(date, items) }

    // The rows the grid last showed, so a day change loads the pages the user is looking at.
    private var visibleFirst = 0
    private var visibleLast = 0
    private var loadJob: Job? = null
    private val pageJobs = HashMap<Pair<LocalDate, Int>, Job>()
    private var firstPageMs = 0L

    init {
        loadJob =
            viewModelScope.launchGuarded("EpgViewModel.init", onError = ::showError) {
                repository = AppContainer.getInstance(context).getMediaRepository()
                loadEpgDataInternal(currentDate)
            }
    }

    private fun showError(e: Throwable) {
        _uiState.value = UiState.Error(friendlyErrorMessage(e, context, AppSettings(context).isDevMode))
    }

    fun loadEpgData(date: LocalDate = currentDate) {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launchGuarded("EpgViewModel.loadEpgData", onError = ::showError) {
                if (!::repository.isInitialized) {
                    repository = AppContainer.getInstance(context).getMediaRepository()
                }
                loadEpgDataInternal(date)
            }
    }

    private suspend fun loadEpgDataInternal(date: LocalDate) {
        _uiState.value = UiState.Loading
        currentDate = date
        cancelPageLoads()
        val startTime = System.currentTimeMillis()

        // Per source (GD4): this source's own EPG or its guide sources, not whether some other
        // source's guide happens to be indexed.
        if (!repository.hasGuideForSource()) {
            _uiState.value = UiState.NoGuide
            return
        }

        val items =
            channels ?: run {
                val itemsResult = loadChannels()
                val loaded = itemsResult.getOrNull()
                if (loaded == null) {
                    val reason = itemsResult.exceptionOrNull()?.message
                    _uiState.value = UiState.Error(context.getString(R.string.epg_error_load_channels_format, reason))
                    return
                }
                loaded.also {
                    channels = it
                    pager.channels = it
                }
            }
        if (items.isEmpty()) {
            _uiState.value = UiState.NoChannels
            return
        }

        try {
            for (page in pagesToLoad(visibleFirst, visibleLast, items.size, PAGE_SIZE)) pager.page(date, page)
            // Nothing listed on the pages in view: look a few pages further before deciding the day
            // is empty — a long list can start with channels that have no guide.
            while (!pager.hasListings(date) && pager.loadedPages(date) < PROBE_PAGES) {
                val next = pager.firstUnloaded(date) ?: break
                pager.page(date, next)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.value = UiState.Error(context.getString(R.string.epg_error_load_data_format, e.message))
            return
        }
        firstPageMs = System.currentTimeMillis() - startTime

        val guide = assembleGuide(items, PAGE_SIZE, pager.pages(date))
        if (guide.listedCount == 0 && guide.loadedCount == items.size) {
            _uiState.value =
                UiState.NoListings(
                    reason = noListingsReason(hasAnyListing = guide.hasAnyListing, indexHasData = repository.hasIndexedEpgData()),
                    selectedDate = date,
                    source = guide.source.takeIf { guide.hasAnyListing },
                    updatedAtMs = guide.updatedAtMs.takeIf { guide.hasAnyListing },
                )
            return
        }
        _uiState.value = readyState(date, guide)
    }

    /** One page's listings for [date], from the index or the source (whichever answers), sorted. */
    private suspend fun loadPage(
        date: LocalDate,
        items: List<MediaItem>,
    ): GuidePage {
        val zone = ZoneId.systemDefault()
        val dayStart = date.atStartOfDay(zone).toEpochSecond()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toEpochSecond()
        val guide = repository.getGuideForItemsInWindow(items, dayStart, dayEnd).getOrThrow()
        return GuidePage(
            listings = guide.epg.mapValues { (_, response) -> response.listings.sortedBy { it.startTime } },
            source = guide.source,
            updatedAtMs = guide.updatedAtMs,
            latestEndSec = guide.latestEndSec,
        )
    }

    private fun readyState(
        date: LocalDate,
        guide: AssembledGuide,
    ): UiState.Ready {
        val timeSlots = generateTimeSlots(date)
        return UiState.Ready(
            channelRows = guide.rows,
            timeSlots = timeSlots,
            currentTimeSlot = calculateCurrentTimeSlot(timeSlots),
            selectedDate = date,
            listedCount = guide.listedCount,
            totalCount = guide.rows.size,
            source = guide.source ?: GuideSource.XMLTV,
            updatedAtMs = guide.updatedAtMs,
            devStats =
                "${guide.answered}/${guide.loadedCount} channels answered · " +
                    "${pager.loadedPages(date)}/${pager.pageCount} pages · first ${firstPageMs}ms",
            loadedCount = guide.loadedCount,
            lastListingEndSec = guide.lastListingEndSec,
            loadedPages = pager.pages(date).keys,
        )
    }

    /**
     * The grid shows rows [first]..[last] (or focus is there): load the pages they fall on, and the
     * next page once they come within [PREFETCH_ROWS] of it. Loaded pages come from the cache;
     * each page that arrives updates the grid. A failed page stays empty and is asked for again the
     * next time its rows come into view.
     */
    fun onRowsVisible(
        first: Int,
        last: Int,
    ) {
        visibleFirst = first
        visibleLast = last
        val state = _uiState.value as? UiState.Ready ?: return
        val date = state.selectedDate
        val items = channels ?: return
        for (page in pagesToLoad(first, last, items.size, PAGE_SIZE)) {
            val key = date to page
            if (pager.isLoaded(date, page) || pageJobs[key]?.isActive == true) continue
            pageJobs[key] =
                viewModelScope.launchGuarded("EpgViewModel.loadPage") {
                    try {
                        pager.page(date, page)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Not an error screen: the rows stay placeholders and are asked for again.
                        Log.w(TAG, "Guide page $page for $date failed: ${e.message}")
                        return@launchGuarded
                    }
                    if (date == currentDate && _uiState.value is UiState.Ready) {
                        _uiState.value = readyState(date, assembleGuide(items, PAGE_SIZE, pager.pages(date)))
                    }
                }
        }
    }

    private fun cancelPageLoads() {
        pageJobs.values.forEach { it.cancel() }
        pageJobs.clear()
    }

    /** Recent and Favourites resolved the way the category screen does; anything else from the source. */
    private suspend fun loadChannels(): kotlin.Result<List<MediaItem>> {
        val items =
            CategoryViewModel.virtualCategoryItems(repository, categoryId, ContentType.LIVE_TV)
                ?: repository.getItems(categoryId, ContentType.LIVE_TV).getOrElse { return kotlin.Result.failure(it) }
        return kotlin.Result.success(guideChannels(items))
    }

    fun forceRefresh() {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _isRefreshing.value = true
                try {
                    if (!::repository.isInitialized) {
                        repository = AppContainer.getInstance(context).getMediaRepository()
                    }
                    repository.clearEpgCache()
                    repository.clearXmltvCache()
                    cancelPageLoads()
                    channels = null
                    pager.clear()
                    // Call the suspending internal loader directly (not the fire-and-forget
                    // loadEpgData() wrapper), so isRefreshing only flips back once the reload
                    // actually finishes instead of immediately after merely scheduling it.
                    loadEpgDataInternal(currentDate)
                } finally {
                    _isRefreshing.value = false
                }
            }
    }

    // Row actions on a guide channel (GD6, plan Part II P3): the channel lists' menu, same calls.

    /** Whether [channelId] is a favourite; off the main thread, as the first read loads the favourites table. */
    suspend fun isFavoriteChannel(channelId: String): Boolean =
        withContext(Dispatchers.Default) { repository.isFavorite(channelId, ContentType.LIVE_TV) }

    /**
     * Returns whether the rows reload: a favourite removed in the Favourites guide leaves it, as
     * Remove from Recent does in the Recent guide — reloaded without it, on the same day.
     */
    fun toggleFavoriteChannel(channel: MediaItem): Boolean {
        val removing = repository.isFavorite(channel.id, ContentType.LIVE_TV)
        if (removing) {
            repository.removeFavorite(channel.id, ContentType.LIVE_TV)
        } else {
            repository.addFavorite(channel.id, channel.name, channel.categoryId, ContentType.LIVE_TV)
        }
        val reload = removing && categoryId == CategoryViewModel.FAVORITES_CATEGORY_ID
        if (reload) {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launchGuarded("EpgViewModel.toggleFavoriteChannel", onError = ::showError) {
                    channels = null
                    loadEpgDataInternal(currentDate)
                }
        }
        return reload
    }

    /** Remove from Recent: on the Recent guide, for a source that keeps its own history. */
    val canRemoveFromRecent: Boolean
        get() = categoryId == CategoryViewModel.RECENT_CATEGORY_ID && ::repository.isInitialized && repository.supportsRemoveFromRecent

    /** Removes [channel] from Recent and reloads the rows without it, on the same day. */
    fun removeFromRecent(channel: MediaItem) {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launchGuarded("EpgViewModel.removeFromRecent", onError = ::showError) {
                repository.removeFromRecent(channel.id, ContentType.LIVE_TV)
                channels = null
                loadEpgDataInternal(currentDate)
            }
    }

    fun selectPreviousDay() = loadEpgData(currentDate.minusDays(1))

    fun selectNextDay() = loadEpgData(currentDate.plusDays(1))

    fun jumpToNow() = loadEpgData(LocalDate.now())

    private fun generateTimeSlots(date: LocalDate): List<TimeSlot> {
        val slots = mutableListOf<TimeSlot>()
        val dayStart = date.atStartOfDay(ZoneId.systemDefault())

        for (i in 0 until 48) {
            val slotStart = dayStart.plusMinutes(i * 30L)
            val slotEnd = slotStart.plusMinutes(30)
            slots.add(
                TimeSlot(
                    startTime = slotStart.toEpochSecond(),
                    endTime = slotEnd.toEpochSecond(),
                    slotIndex = i,
                ),
            )
        }
        return slots
    }

    private fun calculateCurrentTimeSlot(timeSlots: List<TimeSlot>): Int {
        val now = System.currentTimeMillis() / 1000
        // -1 (not 0) when "now" isn't in any slot of the selected date — e.g. any
        // non-today date — so callers don't mistake "no match" for "slot 0 is current".
        return timeSlots.indexOfFirst { now in it.startTime..it.endTime }
    }

    companion object {
        private const val TAG = "EpgViewModel"

        /** Rows whose listings load together: one index query (or one native batch) per page. */
        const val PAGE_SIZE = 30

        /** The next page is asked for once the rows in view come this close to it. */
        const val PREFETCH_ROWS = 5

        /** Pages read on open, at most, looking for a listing before the empty grid is shown. */
        const val PROBE_PAGES = 4
    }
}

/** The guide's channel set for a list: every channel, category markers (`##### 4K #####`) dropped. */
internal fun guideChannels(items: List<MediaItem>): List<MediaItem> = items.filterNot { it.isCategoryMarker }

/**
 * Why a guide has nothing to show: data that stops before the day ([hasAnyListing]) beats an
 * index that has nothing for these channels, which beats having no guide data at all.
 */
internal fun noListingsReason(
    hasAnyListing: Boolean,
    indexHasData: Boolean,
): EpgViewModel.NoListingsReason =
    when {
        hasAnyListing -> EpgViewModel.NoListingsReason.STALE
        indexHasData -> EpgViewModel.NoListingsReason.INDEX_EMPTY
        else -> EpgViewModel.NoListingsReason.NONE
    }

/**
 * The pages holding rows [first]..[last] of [rowCount], plus the next page when [last] is within
 * [prefetchRows] of it — the guide's paging trigger, for the TV's focus and both platforms' scroll.
 */
internal fun pagesToLoad(
    first: Int,
    last: Int,
    rowCount: Int,
    pageSize: Int,
    prefetchRows: Int = EpgViewModel.PREFETCH_ROWS,
): List<Int> {
    if (rowCount <= 0) return emptyList()
    val from = first.coerceIn(0, rowCount - 1)
    val to = (maxOf(first, last) + prefetchRows).coerceIn(from, rowCount - 1)
    return (from / pageSize..to / pageSize).toList()
}

/**
 * Whether "now" ([nowSec]) is on the day shown and past its last listing ([lastListingEndSec]) —
 * when the grid should say "Listings end at …" instead of showing empty rows without a word.
 */
fun guideListingsEnded(
    lastListingEndSec: Long?,
    nowSec: Long,
    dayStartSec: Long,
    dayEndSec: Long,
): Boolean = lastListingEndSec != null && nowSec >= dayStartSec && nowSec < dayEndSec && nowSec >= lastListingEndSec

/** One page of the guide for one day: item id → the day's programmes, sorted by start. */
internal data class GuidePage(
    val listings: Map<String, List<EpgProgram>>,
    val source: GuideSource,
    val updatedAtMs: Long?,
    /** The last listing end these channels have anywhere (not only on this day). */
    val latestEndSec: Long?,
) {
    val hasListings: Boolean get() = listings.values.any { it.isNotEmpty() }
}

/**
 * Pages of [pageSize] rows of [channels], loaded by [load] and cached per (day, page). Confined to
 * one thread (the ViewModel's main thread): no locking.
 */
internal class GuidePager(
    private val pageSize: Int,
    private val load: suspend (date: LocalDate, items: List<MediaItem>) -> GuidePage,
) {
    private val cache = HashMap<Pair<LocalDate, Int>, GuidePage>()

    /** Setting a new list drops every cached page: page n is a different set of channels. */
    var channels: List<MediaItem> = emptyList()
        set(value) {
            field = value
            cache.clear()
        }

    val pageCount: Int get() = (channels.size + pageSize - 1) / pageSize

    fun isLoaded(
        date: LocalDate,
        page: Int,
    ): Boolean = cache.containsKey(date to page)

    fun loadedPages(date: LocalDate): Int = cache.keys.count { it.first == date }

    fun hasListings(date: LocalDate): Boolean = cache.any { (key, page) -> key.first == date && page.hasListings }

    fun firstUnloaded(date: LocalDate): Int? = (0 until pageCount).firstOrNull { !isLoaded(date, it) }

    /** [date]'s loaded pages by page index. */
    fun pages(date: LocalDate): Map<Int, GuidePage> {
        val pages = HashMap<Int, GuidePage>()
        for ((key, page) in cache) if (key.first == date) pages[key.second] = page
        return pages
    }

    /** The page from the cache, else loaded (and cached once it arrives). */
    suspend fun page(
        date: LocalDate,
        page: Int,
    ): GuidePage {
        cache[date to page]?.let { return it }
        val from = page * pageSize
        val items = channels.subList(from.coerceAtMost(channels.size), (from + pageSize).coerceAtMost(channels.size))
        return load(date, items).also { cache[date to page] = it }
    }

    fun clear() = cache.clear()
}

/** The grid's rows and counts for a day, built from whichever pages are loaded. */
internal data class AssembledGuide(
    val rows: List<EpgChannelRow>,
    /** Rows with at least one programme on the day. */
    val listedCount: Int,
    /** Rows on loaded pages. */
    val loadedCount: Int,
    /** Rows the answering layer returned anything for (possibly nothing on the day). */
    val answered: Int,
    /** The layer behind the first page with listings (else the first loaded page); null when none is loaded. */
    val source: GuideSource?,
    val updatedAtMs: Long?,
    /** Some loaded channel has a listing somewhere, even if not on this day. */
    val hasAnyListing: Boolean,
    /** The latest end among the day's loaded listings. */
    val lastListingEndSec: Long?,
)

/** Every channel as a row; rows of a page not loaded yet are empty (the grid's placeholder rows). */
internal fun assembleGuide(
    channels: List<MediaItem>,
    pageSize: Int,
    pages: Map<Int, GuidePage>,
): AssembledGuide {
    var listed = 0
    var loaded = 0
    var answered = 0
    var lastEnd: Long? = null
    val rows =
        channels.mapIndexed { index, channel ->
            val page = pages[index / pageSize]
            if (page != null) loaded++
            val programs = page?.listings?.get(channel.id).orEmpty()
            if (page != null && page.listings.containsKey(channel.id)) answered++
            if (programs.isNotEmpty()) {
                listed++
                val end = programs.maxOf { it.endTime }
                lastEnd = maxOf(lastEnd ?: end, end)
            }
            EpgChannelRow(channel, programs)
        }
    val ordered = pages.toSortedMap().values
    val lead = ordered.firstOrNull { it.hasListings } ?: ordered.firstOrNull { it.latestEndSec != null } ?: ordered.firstOrNull()
    return AssembledGuide(
        rows = rows,
        listedCount = listed,
        loadedCount = loaded,
        answered = answered,
        source = lead?.source,
        updatedAtMs = lead?.updatedAtMs,
        hasAnyListing = ordered.any { it.hasListings || it.latestEndSec != null },
        lastListingEndSec = lastEnd,
    )
}
