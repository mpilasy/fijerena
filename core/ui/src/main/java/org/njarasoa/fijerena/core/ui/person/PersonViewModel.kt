package org.njarasoa.fijerena.core.ui.person

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.tmdb.PersonRole
import org.njarasoa.fijerena.core.network.tmdb.PersonRows
import org.njarasoa.fijerena.core.network.tmdb.PersonTitleSource
import org.njarasoa.fijerena.core.network.tmdb.ShowAppearance
import org.njarasoa.fijerena.core.network.tmdb.ShowTarget
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.network.tmdb.TmdbPeople
import org.njarasoa.fijerena.core.network.tmdb.TmdbPerson
import org.njarasoa.fijerena.core.network.tmdb.TmdbRecommendation
import org.njarasoa.fijerena.core.network.tmdb.personCredits
import org.njarasoa.fijerena.core.network.tmdb.personRows
import org.njarasoa.fijerena.core.network.tmdb.showAppearances
import org.njarasoa.fijerena.core.network.tmdb.showTarget
import org.njarasoa.fijerena.core.network.xtream.ProviderSyncRunner
import org.njarasoa.fijerena.core.player.diagnostics.Redact
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.SeriesId
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.history.MergedLists
import org.njarasoa.fijerena.core.ui.utils.launchGuarded

/**
 * A person's page (docs/plans/20261030_cast-tab-plan.md, Design 3): the person TMDB knows, then
 * their films and shows that the profile's sources have. With [crew], the titles the person directed
 * or wrote (CT-D4). The Movies row and the TV Shows row each have their own switch for the titles
 * no source has (UX-D7); both start off each time the page opens and are not stored (CT-D8); a
 * child profile does not get them (CT-D9). The rows are made again after each catalogue sync.
 */
class PersonViewModel(
    private val personId: Int?,
    private val crew: Boolean,
    private val data: Data,
) : ViewModel() {
    /** What the page reads: TMDB's person and the profile's catalogue. A fake in the tests. */
    interface Data {
        /** Whether TMDB can be asked at all (a TMDB key is set). */
        val hasKey: Boolean

        /** Whether the profile in use is a child's. */
        val child: Boolean

        /** Whether a failure shows its raw message too (Expert mode). */
        val devMode: Boolean

        /** Goes up after each catalogue sync: the rows are made again. */
        val catalogueUpdates: Flow<Long>

        suspend fun person(personId: Int): TmdbPerson

        suspend fun rows(
            person: TmdbPerson,
            crew: Boolean,
            missing: ShowMissing,
        ): PersonRows

        /**
         * The person's appearances in [show] (credit [creditId] of person [personId], one TMDB call
         * kept with the person), matched to the show's episodes on its source. A failure throws.
         */
        suspend fun appearances(
            personId: Int,
            creditId: String,
            show: MediaItem,
        ): ShowLines
    }

    /** A person's lines in a show ([showAppearances]) and the seasons the show's source has. */
    data class ShowLines(
        val lines: List<ShowAppearance>,
        val sourceSeasons: Set<Int>,
    )

    /**
     * What a show card on the page is doing (docs/plans/20261030_episode-cast-plan.md, EC-D2): the
     * credit call in flight ([Loading]), the list of the person's appearances open ([Lines]), or
     * the show page to open now ([Open], once: the screen navigates, then calls [opened]).
     */
    sealed interface ShowOpening {
        val show: MediaItem

        data class Loading(
            override val show: MediaItem,
        ) : ShowOpening

        data class Lines(
            override val show: MediaItem,
            val lines: List<ShowAppearance>,
            val sourceSeasons: Set<Int>,
        ) : ShowOpening

        data class Open(
            override val show: MediaItem,
            val target: ShowTarget,
        ) : ShowOpening
    }

    /** The page's two rows, each with its own switch (UX-D7). */
    enum class Row { MOVIES, SHOWS }

    /** Which rows also show the titles no source has. */
    data class ShowMissing(
        val movies: Boolean = false,
        val shows: Boolean = false,
    ) {
        operator fun get(row: Row): Boolean =
            when (row) {
                Row.MOVIES -> movies
                Row.SHOWS -> shows
            }

        fun with(
            row: Row,
            show: Boolean,
        ): ShowMissing =
            when (row) {
                Row.MOVIES -> copy(movies = show)
                Row.SHOWS -> copy(shows = show)
            }
    }

    sealed interface State {
        data object Loading : State

        /** TMDB failed or has no key. [raw]: the failure's own text, in Expert mode only. */
        data class Error(
            val raw: String?,
        ) : State

        /**
         * [rows] is null until matched. [hasMovies] and [hasShows]: whether the person has credits of
         * that kind at all, so a row can keep its switch while it has no card.
         */
        data class Loaded(
            val person: TmdbPerson,
            val rows: PersonRows?,
            val hasMovies: Boolean,
            val hasShows: Boolean,
        ) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Whether the page has the switches for the titles no source has (CT-D9: not for a child). */
    val canShowMissing: Boolean = !data.child

    private val _showMissing = MutableStateFlow(ShowMissing())

    /** Each row's switch: also the titles no source has. Both off each time the page opens (CT-D8). */
    val showMissing: StateFlow<ShowMissing> = _showMissing.asStateFlow()

    private val _showOpening = MutableStateFlow<ShowOpening?>(null)

    /** The show card being opened; null when none is. Kept over the show's page and Back. */
    val showOpening: StateFlow<ShowOpening?> = _showOpening.asStateFlow()

    private var loadJob: Job? = null
    private var rowsJob: Job? = null

    init {
        load()
    }

    /** Reads the person again, after a failure. */
    fun retry() = load()

    /** Flips [row]'s switch only; the other row keeps its own. */
    fun setShowMissing(
        row: Row,
        show: Boolean,
    ) {
        if (!canShowMissing || show == _showMissing.value[row]) return
        _showMissing.value = _showMissing.value.with(row, show)
        (_state.value as? State.Loaded)?.let { loadRows(it.person) }
    }

    /**
     * A show card of the page ([role]: the person's part in it). An acting role with a TMDB credit
     * reads which episodes or seasons it is (one TMDB call, kept with the person), then opens the
     * one there is, or lists them (EC-D2). Without one, or when the call fails, the show opens as
     * it does from any other card. Main only, as the other calls.
     */
    fun openShow(
        show: MediaItem,
        role: PersonRole?,
    ) {
        val creditId = role?.creditId
        when {
            _showOpening.value is ShowOpening.Loading -> {
                Unit
            }

            crew || creditId == null || personId == null -> {
                _showOpening.value = ShowOpening.Open(show, ShowTarget())
            }

            else -> {
                _showOpening.value = ShowOpening.Loading(show)
                viewModelScope.launchGuarded(
                    "PersonViewModel.openShow",
                    Dispatchers.IO,
                    onError = { _showOpening.value = ShowOpening.Open(show, ShowTarget()) },
                ) {
                    _showOpening.value =
                        try {
                            showOpeningOf(show, data.appearances(personId, creditId, show))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // No TMDB or no episode list: the show opens as from any other card.
                            android.util.Log.w(TAG, "Credit $creditId: ${e.message}")
                            ShowOpening.Open(show, ShowTarget())
                        }
                }
            }
        }
    }

    /** The screen has opened the show page of [ShowOpening.Open]. */
    fun opened() {
        if (_showOpening.value is ShowOpening.Open) _showOpening.value = null
    }

    /** Closes the list of appearances (Back, or the sheet dismissed). */
    fun closeShow() {
        _showOpening.value = null
    }

    private fun load() {
        loadJob?.cancel()
        rowsJob?.cancel()
        _state.value = State.Loading
        loadJob =
            viewModelScope.launchGuarded("PersonViewModel.load", Dispatchers.IO) {
                val person =
                    try {
                        if (personId == null || !data.hasKey) error("No TMDB key or person id")
                        data.person(personId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "Person $personId: ${e.message}")
                        _state.value = State.Error(e.message?.takeIf { data.devMode }?.let(Redact::text))
                        null
                    }
                if (person != null) {
                    val credits = personCredits(person, crew)
                    _state.value =
                        State.Loaded(
                            person = person,
                            rows = null,
                            hasMovies = credits.any { it.mediaType == MOVIE },
                            hasShows = credits.any { it.mediaType == TV },
                        )
                    // Matched now, and again after each catalogue sync. On Main, as setShowMissing:
                    // rowsJob is only cancelled and replaced from one thread, so no load is lost.
                    withContext(Dispatchers.Main) {
                        data.catalogueUpdates.distinctUntilChanged().collect { loadRows(person) }
                    }
                }
            }
    }

    /** On Main only: [rowsJob] is read and replaced without a lock. The work itself runs on IO. */
    private fun loadRows(person: TmdbPerson) {
        rowsJob?.cancel()
        val missing = _showMissing.value
        rowsJob =
            viewModelScope.launchGuarded("PersonViewModel.rows", Dispatchers.IO) {
                val rows = data.rows(person, crew, missing)
                // The rows kept on screen until the new ones are ready: no flash of an empty page.
                (_state.value as? State.Loaded)?.let { _state.value = it.copy(rows = rows) }
            }
    }

    class Factory(
        context: Context,
        private val personId: Int?,
        private val crew: Boolean,
    ) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PersonViewModel::class.java)) { "Unknown ViewModel class: ${modelClass.name}" }
            return PersonViewModel(personId, crew, AppData(appContext)) as T
        }
    }

    /** The real [Data]: TMDB through [TmdbPeople], the catalogue through the profile's merged sources. */
    private class AppData(
        private val context: Context,
    ) : Data {
        private val settings = AppSettings(context)

        override val hasKey: Boolean get() = TmdbApiService.getInstance(context).hasApiKey()
        override val child: Boolean get() = settings.isChildProfile(settings.activeProfileId)
        override val devMode: Boolean get() = settings.isDevMode
        override val catalogueUpdates: Flow<Long> get() = ProviderSyncRunner.catalogueUpdates

        override suspend fun person(personId: Int): TmdbPerson = TmdbPeople.getInstance(context).person(personId)

        override suspend fun rows(
            person: TmdbPerson,
            crew: Boolean,
            missing: ShowMissing,
        ): PersonRows {
            val merged = MergedLists.forSections(context)
            val key =
                Key(
                    personId = person.id,
                    crew = crew,
                    missing = missing,
                    profileId = settings.activeProfileId,
                    sources = merged?.inProfileOrder?.map { it.providerId }.orEmpty(),
                    catalogueUpdates = ProviderSyncRunner.catalogueUpdates.value,
                )
            synchronized(kept) { kept[key] }?.let { return it }
            val sources: List<PersonTitleSource> =
                merged?.inProfileOrder.orEmpty().map { source ->
                    { titles: List<TmdbRecommendation>, contentType: String ->
                        source.repo.findTmdbTitles(titles, contentType).map { it?.copy(providerId = source.providerId) }
                    }
                }
            val rows = personRows(person, crew, sources, missingMovies = missing.movies, missingShows = missing.shows)
            synchronized(kept) {
                // Rows of an older sync, another profile or other sources are of no more use.
                kept.keys.removeAll {
                    it.catalogueUpdates != key.catalogueUpdates || it.profileId != key.profileId || it.sources != key.sources
                }
                kept[key] = rows
            }
            return rows
        }

        override suspend fun appearances(
            personId: Int,
            creditId: String,
            show: MediaItem,
        ): ShowLines {
            val credit = TmdbPeople.getInstance(context).credit(personId, creditId)
            val detail =
                AppContainer
                    .getInstance(context)
                    .getMediaRepository(show.providerId)
                    .getSeriesDetail(SeriesId(show.id))
                    .getOrThrow()
            val episodes =
                detail.episodes
                    .flatMap { (season, list) ->
                        list.mapNotNull { episode ->
                            (episode.seasonNumber ?: season.toIntOrNull())?.let { (it to episode.episodeNumber) to episode.id }
                        }
                    }.toMap()
            val seasons = episodes.keys.mapTo(mutableSetOf()) { it.first }
            return ShowLines(showAppearances(credit, episodes, seasons), seasons)
        }
    }

    /** One person's rows for a profile, its sources and a catalogue sync (as `ChartRows` keeps its rows). */
    private data class Key(
        val personId: Int,
        val crew: Boolean,
        val missing: ShowMissing,
        val profileId: String,
        val sources: List<Long>,
        val catalogueUpdates: Long,
    )

    companion object {
        /**
         * Where a show card goes once its appearances are known (EC-D2): a role of one episode or
         * one season opens it at once; a role TMDB names no line of opens the show; else the list.
         */
        fun showOpeningOf(
            show: MediaItem,
            found: ShowLines,
        ): ShowOpening =
            when (found.lines.size) {
                0 -> ShowOpening.Open(show, ShowTarget())
                1 -> ShowOpening.Open(show, showTarget(found.lines.single(), found.sourceSeasons))
                else -> ShowOpening.Lines(show, found.lines, found.sourceSeasons)
            }

        private const val TAG = "PersonViewModel"
        private const val MOVIE = "movie"
        private const val TV = "tv"

        // The rows kept in memory, so going back to a person's page costs no catalogue query.
        private val kept = mutableMapOf<Key, PersonRows>()
    }
}
