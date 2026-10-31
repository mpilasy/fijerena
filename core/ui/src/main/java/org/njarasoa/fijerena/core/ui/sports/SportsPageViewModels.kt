package org.njarasoa.fijerena.core.ui.sports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.sports.EpgGuideLookup
import org.njarasoa.fijerena.core.network.sports.FollowedSports
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.GameChannelMatcher
import org.njarasoa.fijerena.core.network.sports.ReminderChannelFinder
import org.njarasoa.fijerena.core.network.sports.SportsData
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.network.sports.SportsLeague
import org.njarasoa.fijerena.core.network.sports.SportsSource
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import org.njarasoa.fijerena.core.player.diagnostics.Redact
import org.njarasoa.fijerena.core.ui.utils.launchGuarded

/** Finds the ways to watch a game on the profile's channels (`GameChannelMatcher`), best first. */
fun interface GameMatcher {
    suspend fun match(game: SportsGame): List<GameChannel>
}

/** [game]'s channels; none when the match fails (a guide read error). */
internal suspend fun matchOrEmpty(
    matcher: GameMatcher,
    game: SportsGame,
): List<GameChannel> =
    try {
        matcher.match(game)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("GameMatcher", "Match failed: ${e.javaClass.simpleName}")
        emptyList()
    }

/** What the team and league pages read: the sports sources, the profile's follows and channels. A fake in the tests. */
interface SportsPageData {
    /** Whether a failure shows its raw message too (Admin's dev mode). */
    val devMode: Boolean

    suspend fun teamGames(team: SportsTeam): List<SportsGame>

    suspend fun leagueGames(league: SportsLeague): List<SportsGame>

    suspend fun leagueTeams(league: SportsLeague): List<SportsTeam>

    /** What the profile in use follows; emits again on every change. */
    fun followed(): Flow<FollowedSports.Followed>

    suspend fun follow(team: SportsTeam)

    suspend fun unfollow(team: SportsTeam)

    suspend fun follow(league: SportsLeague)

    suspend fun unfollow(league: SportsLeague)

    /** A matcher on the profile's live channels and guide, read once per page. */
    suspend fun matcher(): GameMatcher
}

/** A page's games: loading, failed (with Retry), or the games with the channels found so far. */
sealed interface GamesState {
    data object Loading : GamesState

    /** Every source failed. [raw]: the failure's own text, in dev mode only. */
    data class Failed(
        val raw: String?,
    ) : GamesState

    /**
     * The games, soonest first. [channels] by game [key]: a game not in it is still being
     * matched to the profile's channels.
     */
    data class Loaded(
        val games: List<SportsGame>,
        val channels: Map<String, List<GameChannel>> = emptyMap(),
    ) : GamesState {
        fun channelsOf(game: SportsGame): List<GameChannel>? = channels[game.key]
    }
}

/**
 * A page of games (the team page, the league page; the sports teams plan → Screens): the games
 * once, then each game's channels as the matcher finds them, in the games' order, so the first
 * rows fill first. The page is not kept up to date (SP-D10): Retry or a new visit reads again.
 */
abstract class SportsGamesViewModel(
    protected val data: SportsPageData,
) : ViewModel() {
    private val _games = MutableStateFlow<GamesState>(GamesState.Loading)
    val games: StateFlow<GamesState> = _games.asStateFlow()

    private var job: Job? = null

    protected abstract suspend fun loadGames(): List<SportsGame>

    /** Called with the games once they are read, before their channels are matched. */
    protected open fun onGames(games: List<SportsGame>) = Unit

    /** Reads the games again after a failure. */
    fun retry() = load()

    protected fun load() {
        job?.cancel()
        _games.value = GamesState.Loading
        job =
            viewModelScope.launchGuarded(
                "${javaClass.simpleName}.load",
                Dispatchers.IO,
                onError = { e -> _games.value = GamesState.Failed(rawOf(e)) },
            ) {
                val games =
                    try {
                        loadGames()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // No network or a source error: an expected failure, not one for the crash log.
                        android.util.Log.w(TAG, "Games failed: ${e.javaClass.simpleName}")
                        _games.value = GamesState.Failed(rawOf(e))
                        return@launchGuarded
                    }
                onGames(games)
                _games.value = GamesState.Loaded(games)
                if (games.isEmpty()) return@launchGuarded
                val matcher =
                    try {
                        data.matcher()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // The channels or the guide could not be read: no game is found on them.
                        android.util.Log.w(TAG, "Channels failed: ${e.javaClass.simpleName}")
                        GameMatcher { emptyList() }
                    }
                // Every game at once: each game's guide reads run beside the others'.
                coroutineScope {
                    games.forEach { game ->
                        launch {
                            val channels = matchOrEmpty(matcher, game)
                            _games.update { state ->
                                (state as? GamesState.Loaded)?.let { it.copy(channels = it.channels + (game.key to channels)) } ?: state
                            }
                        }
                    }
                }
            }
    }

    protected fun launchFollow(block: suspend () -> Unit) {
        viewModelScope.launchGuarded("${javaClass.simpleName}.follow", Dispatchers.IO) { block() }
    }

    private fun rawOf(e: Throwable): String? = if (data.devMode) Redact.text(e.message ?: e.javaClass.simpleName) else null

    private companion object {
        const val TAG = "SportsGamesViewModel"
    }
}

/**
 * The team page (`Screen.SportsTeam`): the team's coming games (up to 10) and Follow. [team] is
 * what the route knows; once the games come, the team's own entry in them gives its name (the one
 * the game titles use) and adds what the route lacks (its logo, its other names, the route's name
 * among them), so a team followed from here is stored whole.
 */
class SportsTeamViewModel(
    data: SportsPageData,
    team: SportsTeam,
) : SportsGamesViewModel(data) {
    private val _team = MutableStateFlow(team)
    val team: StateFlow<SportsTeam> = _team.asStateFlow()

    /** Whether the profile follows the team. */
    val followed: StateFlow<Boolean> =
        data
            .followed()
            .map { f -> f.teams.any { it.source == team.source && it.id == team.id } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        load()
    }

    override suspend fun loadGames(): List<SportsGame> = data.teamGames(_team.value)

    override fun onGames(games: List<SportsGame>) {
        val route = _team.value
        val found =
            games.firstNotNullOfOrNull { game ->
                listOfNotNull(game.home, game.away).firstOrNull { it.source == route.source && it.id == route.id }
            } ?: return
        // The name the games give ("Bayern Munich", not search's "Bayern München"), so the page and its game titles agree.
        _team.value =
            route.copy(
                name = found.name,
                shortName = route.shortName ?: found.shortName,
                altNames = (route.altNames + route.name + found.allNames).filter { it != found.name }.distinct(),
                league = route.league ?: found.league,
                leagueName = route.leagueName ?: found.leagueName,
                logoUrl = route.logoUrl ?: found.logoUrl,
            )
    }

    /** Follows the team, or stops following it. */
    fun toggleFollow() {
        val current = _team.value
        val on = followed.value
        launchFollow { if (on) data.unfollow(current) else data.follow(current) }
    }

    class Factory(
        context: Context,
        private val team: SportsTeam,
    ) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SportsTeamViewModel::class.java)) { "Unknown ViewModel class: ${modelClass.name}" }
            return SportsTeamViewModel(AppSportsPageData(appContext), team) as T
        }
    }
}

/**
 * The league page (`Screen.SportsLeague`): the league's games of the next 7 days, its teams
 * (each opening its team page) and Follow. A failed team list leaves the list out.
 */
class SportsLeagueViewModel(
    data: SportsPageData,
    val league: SportsLeague,
) : SportsGamesViewModel(data) {
    private val _teams = MutableStateFlow<List<SportsTeam>?>(null)

    /** The league's teams, null while they load. */
    val teams: StateFlow<List<SportsTeam>?> = _teams.asStateFlow()

    /** Whether the profile follows the league. */
    val followed: StateFlow<Boolean> =
        data
            .followed()
            .map { f -> f.leagues.any { it.source == league.source && it.id == league.id } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        load()
        viewModelScope.launchGuarded("SportsLeagueViewModel.teams", Dispatchers.IO, onError = { _teams.value = emptyList() }) {
            _teams.value =
                try {
                    data.leagueTeams(league).sortedBy { it.name.lowercase() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("SportsLeagueViewModel", "Teams failed: ${e.javaClass.simpleName}")
                    emptyList()
                }
        }
    }

    override suspend fun loadGames(): List<SportsGame> = data.leagueGames(league)

    /** Follows the league, or stops following it. */
    fun toggleFollow() {
        val on = followed.value
        launchFollow { if (on) data.unfollow(league) else data.follow(league) }
    }

    class Factory(
        context: Context,
        private val league: SportsLeague,
    ) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SportsLeagueViewModel::class.java)) { "Unknown ViewModel class: ${modelClass.name}" }
            return SportsLeagueViewModel(AppSportsPageData(appContext), league) as T
        }
    }
}

/**
 * A team or league as a route names it: its source, its id and its name, with what else the
 * route carries. The sport is the id's first part for ESPN ("soccer/359").
 */
fun sportsTeamOf(
    source: String,
    teamId: String,
    name: String,
    sport: String? = null,
    league: String? = null,
    logoUrl: String? = null,
): SportsTeam? {
    val src = SportsSource.byId(source) ?: return null
    return SportsTeam(
        source = src,
        id = teamId,
        name = name,
        sport = sport ?: sportOf(teamId),
        league = league,
        logoUrl = logoUrl,
    )
}

/** See [sportsTeamOf]. */
fun sportsLeagueOf(
    source: String,
    leagueId: String,
    name: String,
    sport: String? = null,
    logoUrl: String? = null,
): SportsLeague? {
    val src = SportsSource.byId(source) ?: return null
    return SportsLeague(src, leagueId, name, sport ?: sportOf(leagueId), logoUrl)
}

// The sport of an id with none given: ESPN's id starts with it.
private fun sportOf(id: String): String = id.substringBefore('/', "")

/**
 * The real [SportsPageData]: [SportsData], the profile's [FollowedSports], and a
 * [GameChannelMatcher] on the profile's live channels (`ReminderChannelFinder.liveChannels`, the
 * one loader the reminders use too) and its guide ([EpgGuideLookup]).
 */
class AppSportsPageData(
    private val context: Context,
) : SportsPageData {
    private val settings = AppSettings(context)
    private val sports get() = SportsData.getInstance(context)
    private val follows get() = FollowedSports.getInstance(context)
    private val profileId get() = settings.activeProfileId

    override val devMode: Boolean get() = settings.isDevMode

    override suspend fun teamGames(team: SportsTeam): List<SportsGame> = sports.teamGames(team)

    override suspend fun leagueGames(league: SportsLeague): List<SportsGame> = sports.leagueGames(league)

    override suspend fun leagueTeams(league: SportsLeague): List<SportsTeam> = sports.leagueTeams(league)

    override fun followed(): Flow<FollowedSports.Followed> = follows.observe(profileId)

    override suspend fun follow(team: SportsTeam) = follows.follow(profileId, team)

    override suspend fun unfollow(team: SportsTeam) = follows.unfollow(profileId, team)

    override suspend fun follow(league: SportsLeague) = follows.follow(profileId, league)

    override suspend fun unfollow(league: SportsLeague) = follows.unfollow(profileId, league)

    override suspend fun matcher(): GameMatcher =
        withContext(Dispatchers.IO) {
            val channels = ReminderChannelFinder.liveChannels(context)
            val matcher = GameChannelMatcher(EpgGuideLookup.load(context))
            GameMatcher { game -> matcher.match(game, channels) }
        }
}
