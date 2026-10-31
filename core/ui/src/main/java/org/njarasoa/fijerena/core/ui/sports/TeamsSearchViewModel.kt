package org.njarasoa.fijerena.core.ui.sports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.sports.FollowKind
import org.njarasoa.fijerena.core.network.sports.FollowedSports
import org.njarasoa.fijerena.core.network.sports.SportsData
import org.njarasoa.fijerena.core.network.sports.SportsLeague
import org.njarasoa.fijerena.core.network.sports.SportsSearchResult
import org.njarasoa.fijerena.core.network.sports.SportsSource
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import org.njarasoa.fijerena.core.player.diagnostics.Redact
import org.njarasoa.fijerena.core.ui.utils.launchGuarded

/** A followed team's or league's key, to mark it in a list. */
fun followKey(
    kind: FollowKind,
    source: SportsSource,
    id: String,
): String = "${kind.id}:${source.id}:$id"

val SportsTeam.followKey: String get() = followKey(FollowKind.TEAM, source, id)

val SportsLeague.followKey: String get() = followKey(FollowKind.LEAGUE, source, id)

/** The keys of everything [followed] holds. */
fun followKeys(followed: FollowedSports.Followed): Set<String> =
    (followed.teams.map { it.followKey } + followed.leagues.map { it.followKey }).toSet()

/**
 * Search's Teams type (the sports teams plan → Screens): one sports search per query
 * ([SportsData.search]: ESPN's teams and leagues, then the other sources' that ESPN does not have),
 * shown as two groups, Teams then Leagues, each row marked when the profile follows it. A child
 * profile never gets the Teams type, nor does any profile while Admin's Sports switch is off
 * (`showsTeamsTab`). The screen waits for the user to stop typing before it calls [search], as the
 * People type does.
 */
class TeamsSearchViewModel(
    private val data: Data,
) : ViewModel() {
    /** What the search reads: the sports sources and the profile's followed teams. A fake in the tests. */
    interface Data {
        /** Whether a failure shows its raw message too (Admin's dev mode). */
        val devMode: Boolean

        /** The teams and leagues for [query]. A failure of every source throws. */
        suspend fun search(query: String): SportsSearchResult

        /** What the profile in use follows; emits again on every change. */
        fun followed(): Flow<FollowedSports.Followed>
    }

    sealed interface State {
        /** No query yet, or one too short to search. */
        data object Idle : State

        data class Loading(
            val query: String,
        ) : State

        /** No source answered. [raw]: the failure's own text, in dev mode only. */
        data class Failed(
            val query: String,
            val raw: String?,
        ) : State

        data class Loaded(
            val query: String,
            val teams: List<SportsTeam>,
            val leagues: List<SportsLeague>,
        ) : State {
            val isEmpty: Boolean get() = teams.isEmpty() && leagues.isEmpty()
        }
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** The [followKey]s of what the profile follows, for the rows' marks. */
    val followed: StateFlow<Set<String>> =
        data.followed().map(::followKeys).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

    private var job: Job? = null

    /**
     * Searches for [query], at once (the screen has waited for typing to stop). The query on screen
     * or in flight is not asked again; a new one cancels the one in flight. Main only.
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

    /** Searches again for the query that failed. */
    fun retry() {
        (_state.value as? State.Failed)?.let { run(it.query) }
    }

    private fun run(term: String) {
        job?.cancel()
        _state.value = State.Loading(term)
        job =
            viewModelScope.launchGuarded(
                "TeamsSearchViewModel.search",
                Dispatchers.IO,
                onError = { e -> _state.value = State.Failed(term, rawOf(e)) },
            ) {
                _state.value =
                    try {
                        val found = data.search(term)
                        State.Loaded(term, found.teams, found.leagues)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // No network or a source error: an expected failure, not one for the crash log.
                        android.util.Log.w(TAG, "Teams search failed: ${e.javaClass.simpleName}")
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
            require(modelClass.isAssignableFrom(TeamsSearchViewModel::class.java)) { "Unknown ViewModel class: ${modelClass.name}" }
            return TeamsSearchViewModel(AppData(appContext)) as T
        }
    }

    /** The real [Data]: [SportsData] and the profile's [FollowedSports]. */
    private class AppData(
        private val context: Context,
    ) : Data {
        private val settings = AppSettings(context)

        override val devMode: Boolean get() = settings.isDevMode

        override suspend fun search(query: String): SportsSearchResult = SportsData.getInstance(context).search(query)

        override fun followed(): Flow<FollowedSports.Followed> = FollowedSports.getInstance(context).observe(settings.activeProfileId)
    }

    companion object {
        /** As the other search types: two letters at least. */
        const val MIN_QUERY_LENGTH = 2
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val TAG = "TeamsSearchViewModel"
    }
}
