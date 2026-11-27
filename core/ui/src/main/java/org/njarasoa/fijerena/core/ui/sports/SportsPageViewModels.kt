package org.njarasoa.fijerena.core.ui.sports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.sports.EpgGuideLookup
import org.njarasoa.fijerena.core.network.sports.FollowedSports
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.GameChannelMatcher
import org.njarasoa.fijerena.core.network.sports.GameState
import org.njarasoa.fijerena.core.network.sports.ReminderChannelFinder
import org.njarasoa.fijerena.core.network.sports.SportsChannels
import org.njarasoa.fijerena.core.network.sports.SportsData
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.network.sports.SportsLeague
import org.njarasoa.fijerena.core.network.sports.SportsSource
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import org.njarasoa.fijerena.core.player.diagnostics.Redact
import org.njarasoa.fijerena.core.ui.utils.launchGuarded
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
    /** Whether a failure shows its raw message too (a Nerd). */
    val nerd: Boolean

    suspend fun teamGames(team: SportsTeam): List<SportsGame>

    /** [team]'s results of the last 30 days, newest first; empty when there are none or they cannot be read. */
    suspend fun teamResults(team: SportsTeam): List<SportsGame> = emptyList()

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

    /** Makes the stored sports answers out of date, so the next reads call the sources (a pull to refresh). */
    fun expireGames() = Unit
}

/** A page's games: loading, failed (with Retry), or the games with the channels found so far. */
sealed interface GamesState {
    data object Loading : GamesState

    /** Every source failed. [raw]: the failure's own text, for a Nerd only. */
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
 * Reads the games ([loadGames]) into [state], then each game's channels as the [matcher] finds
 * them, [MATCH_PARALLEL] games at a time in the games' order, so the first rows fill first. A game
 * whose match takes longer than [matchTimeoutMs] finds no channel: its row says "Not found" and
 * never stays on "Looking for channels…". Making the [matcher] (the profile's channel list, read
 * once per process, and its index) has its own, longer limit, [channelsTimeoutMs]: on a slow device
 * the first read takes more than one game's limit. A failed read sets [GamesState.Failed] ([rawOf]: its raw
 * text for a Nerd); a matcher that cannot be made finds nothing. [onGames] gets the games before
 * their channels. Shared by the team and league pages and the Sports section.
 *
 * [quiet]: a read again of a page that shows its games. A failure keeps the games shown, and each
 * game keeps the channels found before until its new match ends.
 */
internal suspend fun readGamesAndChannels(
    state: MutableStateFlow<GamesState>,
    loadGames: suspend () -> List<SportsGame>,
    matcher: suspend () -> GameMatcher,
    rawOf: (Throwable) -> String?,
    onGames: (List<SportsGame>) -> Unit = {},
    matchTimeoutMs: Long = MATCH_TIMEOUT_MS,
    channelsTimeoutMs: Long = CHANNELS_TIMEOUT_MS,
    quiet: Boolean = false,
) {
    val shown = (state.value as? GamesState.Loaded).takeIf { quiet }
    val games =
        try {
            loadGames()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No network or a source error: an expected failure, not one for the crash log.
            android.util.Log.w(GAMES_TAG, "Games failed: ${e.javaClass.simpleName}")
            if (shown == null) state.value = GamesState.Failed(rawOf(e))
            null
        }
    if (games != null) {
        onGames(games)
        val keys = games.mapTo(HashSet()) { it.key }
        state.value = GamesState.Loaded(games, shown?.channels.orEmpty().filterKeys { it in keys })
        if (games.isNotEmpty()) matchChannels(state, games, matcher, matchTimeoutMs, channelsTimeoutMs)
    }
}

private suspend fun matchChannels(
    state: MutableStateFlow<GamesState>,
    games: List<SportsGame>,
    matcher: suspend () -> GameMatcher,
    matchTimeoutMs: Long,
    channelsTimeoutMs: Long,
) {
    val found =
        try {
            withTimeoutOrNull(channelsTimeoutMs) { matcher() } ?: GameMatcher { emptyList() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The channels or the guide could not be read: no game is found on them.
            android.util.Log.w(GAMES_TAG, "Channels failed: ${e.javaClass.simpleName}")
            GameMatcher { emptyList() }
        }
    // A few games at a time, in order: all at once, every game's guide reads queued behind the
    // others' and no row filled until the end.
    val slots = Semaphore(MATCH_PARALLEL)
    coroutineScope {
        games.forEach { game ->
            launch {
                val channels = slots.withPermit { withTimeoutOrNull(matchTimeoutMs) { matchOrEmpty(found, game) } }.orEmpty()
                state.update { current ->
                    (current as? GamesState.Loaded)?.let { it.copy(channels = it.channels + (game.key to channels)) } ?: current
                }
            }
        }
    }
}

/**
 * Matches [game] again and puts its channels in [state]: a game's sheet that opens looks again (the
 * row's match can have come before the guide index was ready, or have taken too long). A match that
 * fails or takes longer than [timeoutMs] keeps the channels found before.
 */
internal suspend fun matchGameAgain(
    state: MutableStateFlow<GamesState>,
    game: SportsGame,
    matcher: suspend () -> GameMatcher,
    timeoutMs: Long = CHANNELS_TIMEOUT_MS,
) {
    val channels = matchOrNull(game, matcher, timeoutMs) ?: return
    state.update { current ->
        val loaded = current as? GamesState.Loaded
        if (loaded == null ||
            loaded.games.none { it.key == game.key }
        ) {
            current
        } else {
            loaded.copy(channels = loaded.channels + (game.key to channels))
        }
    }
}

/** [game]'s channels; null when the matcher or the match fails, or they take longer than [timeoutMs]. */
internal suspend fun matchOrNull(
    game: SportsGame,
    matcher: suspend () -> GameMatcher,
    timeoutMs: Long = CHANNELS_TIMEOUT_MS,
): List<GameChannel>? =
    try {
        withTimeoutOrNull(timeoutMs) { matcher().match(game) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w(GAMES_TAG, "Match again failed: ${e.javaClass.simpleName}")
        null
    }

private const val GAMES_TAG = "SportsGamesViewModel"

/** The games matched at the same time. */
internal const val MATCH_PARALLEL = 3

/** The longest a game's match may take before its row says "Not found". */
internal const val MATCH_TIMEOUT_MS = 20_000L

/**
 * The longest making the matcher may take before every row says "Not found". The Chromecast HD
 * took more than 20 seconds to read a large source's channels after the app started.
 */
internal const val CHANNELS_TIMEOUT_MS = 120_000L

/**
 * [route] (a team as a route or a search names it) completed from its own entry in [games]: the
 * name the games give ("Bayern Munich", not search's "Bayern München"), so a page and its game
 * titles agree, and what the route lacks (its logo, its other names, the route's name among them),
 * so a team followed from it is stored whole. [route] itself when no game has the team.
 */
fun teamFromGames(
    route: SportsTeam,
    games: List<SportsGame>,
): SportsTeam {
    val found =
        games.firstNotNullOfOrNull { game ->
            listOfNotNull(game.home, game.away).firstOrNull { it.source == route.source && it.id == route.id }
        }
    return found?.let {
        route.copy(
            name = it.name,
            shortName = route.shortName ?: it.shortName,
            altNames = (route.altNames + route.name + it.allNames).filter { name -> name != it.name }.distinct(),
            league = route.league ?: it.league,
            leagueName = route.leagueName ?: it.leagueName,
            logoUrl = route.logoUrl ?: it.logoUrl,
        )
    } ?: route
}

/**
 * A page of games (the team page, the league page, the Sports section; the sports teams plan →
 * Screens): the games, then each game's channels as the matcher finds them, in the games' order, so
 * the first rows fill first. While the page is on screen ([keepFresh]) it reads the games again
 * when they are old or one is on, and hides the games that are over; [refresh] reads them now.
 */
abstract class SportsGamesViewModel(
    protected val data: SportsPageData,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _games = MutableStateFlow<GamesState>(GamesState.Loading)
    val games: StateFlow<GamesState> = _games.asStateFlow()

    private val _refreshing = MutableStateFlow(false)

    /** Whether a [refresh] (a pull) is reading the games. */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var job: Job? = null

    /** Whether a read of the games (the first load, a read again or a pull) is still running. For tests. */
    internal val reading: Boolean get() = job?.isActive == true

    // When the games shown were read.
    @Volatile private var readAtMs = 0L

    // The reads again started ([readAgain]).
    @Volatile private var reads = 0

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
                readGamesAndChannels(_games, ::readGames, data::matcher, ::rawOf, ::onGames)
            }
    }

    /**
     * Reads the games and their channels again now, not from the stored sports answers (a pull to
     * refresh). The games stay on screen while they are read, and when the read fails.
     */
    fun refresh() {
        if (_games.value !is GamesState.Loaded) return retry()
        data.expireGames()
        _refreshing.value = true
        readAgain(force = true)
    }

    /**
     * While the page is on screen: call it while the screen is resumed; it returns only when
     * cancelled. Every [CHECK_MS] it hides the games that started more than 3 hours ago and are not
     * on, and reads the games again ([readAgain]) when they were read [STALE_MS] ago or on an
     * earlier day, or [LIVE_REFRESH_MS] ago while one of them is on.
     */
    suspend fun keepFresh() {
        while (true) {
            val nowMs = clock()
            (_games.value as? GamesState.Loaded)?.let { loaded ->
                val nowSec = nowMs / 1000
                val left = loaded.games.filter { it.state == GameState.LIVE || it.startEpochSec >= nowSec - GAME_ON_SEC }
                if (left.size != loaded.games.size) _games.update { (it as? GamesState.Loaded)?.copy(games = left) ?: it }
                val age = nowMs - readAtMs
                val old = age >= STALE_MS || localDay(readAtMs) != localDay(nowMs)
                if (old || (age >= LIVE_REFRESH_MS && left.any { isOn(it, nowSec) })) readAgain(force = false)
            }
            delay(CHECK_MS)
        }
    }

    // A read again that keeps the games on screen; one at a time, unless [force]d (a pull).
    private fun readAgain(force: Boolean) {
        if (!force && job?.isActive == true) return
        job?.cancel()
        val read = ++reads
        job =
            viewModelScope.launchGuarded("${javaClass.simpleName}.readAgain", Dispatchers.IO) {
                try {
                    readGamesAndChannels(_games, ::readGames, data::matcher, ::rawOf, ::onGames, quiet = true)
                } finally {
                    // Not when a later read (a pull) took over: it ends the pull.
                    if (read == reads) _refreshing.value = false
                }
            }
    }

    private suspend fun readGames(): List<SportsGame> = loadGames().also { readAtMs = clock() }

    /** Matches [game] again (its sheet opens); see [matchGameAgain]. */
    fun matchAgain(game: SportsGame) {
        viewModelScope.launchGuarded("${javaClass.simpleName}.matchAgain", Dispatchers.IO) { matchGameAgain(_games, game, data::matcher) }
    }

    protected fun launchFollow(block: suspend () -> Unit) {
        viewModelScope.launchGuarded("${javaClass.simpleName}.follow", Dispatchers.IO) { block() }
    }

    private fun rawOf(e: Throwable): String? = if (data.nerd) Redact.text(e.message ?: e.javaClass.simpleName) else null

    private fun localDay(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    internal companion object {
        /** How often [keepFresh] looks at the games. */
        const val CHECK_MS = 30_000L

        /** The age at which the games are read again. */
        const val STALE_MS = 15 * 60_000L

        /** The age at which the games are read again while one is on (the sports cache keeps such a schedule as long). */
        const val LIVE_REFRESH_MS = 2 * 60_000L

        /** A game that started up to 3 hours ago, not ended, may still be on. */
        const val GAME_ON_SEC = 3 * 60 * 60L

        /** Whether [game] is on: ESPN says so, or it started less than 3 hours ago and has not ended. */
        fun isOn(
            game: SportsGame,
            nowSec: Long,
        ): Boolean =
            game.state == GameState.LIVE ||
                (game.state == GameState.SCHEDULED && game.startEpochSec <= nowSec && game.startEpochSec > nowSec - GAME_ON_SEC)
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
    clock: () -> Long = System::currentTimeMillis,
) : SportsGamesViewModel(data, clock) {
    private val _team = MutableStateFlow(team)
    val team: StateFlow<SportsTeam> = _team.asStateFlow()

    /** Whether the profile follows the team. */
    val followed: StateFlow<Boolean> =
        data
            .followed()
            .map { f -> f.teams.any { it.source == team.source && it.id == team.id } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _results = MutableStateFlow<List<SportsGame>>(emptyList())

    /** The team's results of the last 30 days, newest first; empty while they load, and when there are none. */
    val results: StateFlow<List<SportsGame>> = _results.asStateFlow()

    private var resultsJob: Job? = null

    init {
        load()
    }

    override suspend fun loadGames(): List<SportsGame> = data.teamGames(_team.value)

    override fun onGames(games: List<SportsGame>) {
        _team.value = teamFromGames(_team.value, games)
        loadResults()
    }

    // After each read of the coming games, so a game that has ended comes. The results shown stay
    // when none come (a failed read gives none).
    private fun loadResults() {
        if (resultsJob?.isActive == true) return
        resultsJob =
            viewModelScope.launchGuarded("SportsTeamViewModel.results", Dispatchers.IO) {
                data.teamResults(_team.value).takeIf { it.isNotEmpty() }?.let { _results.value = it }
            }
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

    override val nerd: Boolean get() = settings.isNerdMode

    override suspend fun teamGames(team: SportsTeam): List<SportsGame> = sports.teamGames(team)

    override suspend fun teamResults(team: SportsTeam): List<SportsGame> = sports.teamResults(team)

    override suspend fun leagueGames(league: SportsLeague): List<SportsGame> = sports.leagueGames(league)

    override suspend fun leagueTeams(league: SportsLeague): List<SportsTeam> = sports.leagueTeams(league)

    override fun followed(): Flow<FollowedSports.Followed> = follows.observe(profileId)

    override suspend fun follow(team: SportsTeam) = follows.follow(profileId, team)

    override suspend fun unfollow(team: SportsTeam) = follows.unfollow(profileId, team)

    override suspend fun follow(league: SportsLeague) = follows.follow(profileId, league)

    override suspend fun unfollow(league: SportsLeague) = follows.unfollow(profileId, league)

    override fun expireGames() = sports.expireAll()

    override suspend fun matcher(): GameMatcher =
        withContext(Dispatchers.IO) {
            val channels = ReminderChannelFinder.liveChannels(context)
            val matcher = GameChannelMatcher(EpgGuideLookup.load(context))
            // The index of the channels too, here: in the first game's match it used that game's limit.
            matcher.prepare(channels)
            GameMatcher { game -> SportsChannels.withIcons(context, matcher.match(game, channels)) }
        }
}
