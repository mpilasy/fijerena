package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.tmdb.PeopleSearchResults
import org.njarasoa.fijerena.core.network.tmdb.PersonTitleSource
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.network.tmdb.TmdbRecommendation
import org.njarasoa.fijerena.core.network.tmdb.peopleSearchResults
import org.njarasoa.fijerena.core.player.diagnostics.Redact
import org.njarasoa.fijerena.core.ui.history.MergedLists
import org.njarasoa.fijerena.core.ui.utils.launchGuarded

/**
 * Search's People type (the people search plan): one TMDB `search/multi` call per query, in the app
 * language, through TMDB's rate limiter (`TmdbApiService`), then the actors, the films and the
 * shows ([peopleSearchResults]). The films and shows are matched to the profile's sources. A child
 * profile never gets the People type (`showsPeopleTab`). The screen waits for the user to stop typing before it
 * calls [search], as the catalogue search does.
 */
class PeopleSearchViewModel(
    private val data: Data,
) : ViewModel() {
    /** What the search reads: TMDB and the profile's catalogue. A fake in the tests. */
    interface Data {
        /** Whether a failure shows its raw message too (Expert mode). */
        val devMode: Boolean

        /** TMDB's `search/multi` results for [query]. A failure throws. */
        suspend fun search(query: String): List<TmdbRecommendation>

        /** The profile's sources, in the profile's order, each finding its titles for a TMDB list. */
        suspend fun sources(): List<PersonTitleSource>
    }

    sealed interface State {
        /** No query yet, or one too short to search. */
        data object Idle : State

        data class Loading(
            val query: String,
        ) : State

        /** TMDB could not be reached. [raw]: the failure's own text, in Expert mode only. */
        data class Failed(
            val query: String,
            val raw: String?,
        ) : State

        data class Loaded(
            val query: String,
            val results: PeopleSearchResults,
        ) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null

    /**
     * Searches TMDB for [query], at once (the screen has waited for typing to stop). The query on
     * screen or in flight is not asked again; a new one cancels the one in flight. Main only.
     */
    fun search(query: String) {
        val term = query.trim()
        val current = _state.value
        when {
            term.length < MIN_QUERY_LENGTH -> {
                job?.cancel()
                _state.value = State.Idle
            }

            (current is State.Loading && current.query == term) || (current is State.Loaded && current.query == term) -> {
                Unit
            }

            else -> {
                run(term)
            }
        }
    }

    /** Asks TMDB again for the query that failed. */
    fun retry() {
        (_state.value as? State.Failed)?.let { run(it.query) }
    }

    private fun run(term: String) {
        job?.cancel()
        _state.value = State.Loading(term)
        job =
            viewModelScope.launchGuarded(
                "PeopleSearchViewModel.search",
                Dispatchers.IO,
                onError = { e -> _state.value = State.Failed(term, rawOf(e)) },
            ) {
                _state.value =
                    try {
                        State.Loaded(term, peopleSearchResults(data.search(term), data.sources()))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // No network or a TMDB error: an expected failure, not one for the crash log.
                        android.util.Log.w(TAG, "People search failed: ${e.javaClass.simpleName}")
                        State.Failed(term, rawOf(e))
                    }
            }
    }

    private fun rawOf(e: Throwable): String? = if (data.devMode) Redact.text(e.message ?: e.javaClass.simpleName) else null

    class Factory(
        context: Context,
    ) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PeopleSearchViewModel::class.java)) { "Unknown ViewModel class: ${modelClass.name}" }
            return PeopleSearchViewModel(AppData(appContext)) as T
        }
    }

    /** The real [Data]: TMDB through [TmdbApiService], the catalogue through the profile's merged sources. */
    private class AppData(
        private val context: Context,
    ) : Data {
        private val settings = AppSettings(context)

        override val devMode: Boolean get() = settings.isDevMode

        override suspend fun search(query: String): List<TmdbRecommendation> =
            TmdbApiService.getInstance(context).searchMulti(query).results

        override suspend fun sources(): List<PersonTitleSource> =
            MergedLists.forSections(context)?.inProfileOrder.orEmpty().map { source ->
                { titles: List<TmdbRecommendation>, contentType: String ->
                    source.repo.findTmdbTitles(titles, contentType).map { it?.copy(providerId = source.providerId) }
                }
            }
    }

    companion object {
        /** As the catalogue search: two letters at least. */
        const val MIN_QUERY_LENGTH = 2
        private const val TAG = "PeopleSearchViewModel"
    }
}
