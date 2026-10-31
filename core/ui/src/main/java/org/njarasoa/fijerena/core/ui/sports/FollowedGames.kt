package org.njarasoa.fijerena.core.ui.sports

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.sports.FollowedSports
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.GameState
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.network.sports.TeamNames
import org.njarasoa.fijerena.core.ui.utils.launchGuarded

// The games of what the profile follows (the sports teams plan → Screens: the Sports section and
// Home's Upcoming Games). Only the followed teams and leagues are read (SP-D10).

/** The Sports section's games: the next 7 days. */
const val SECTION_WINDOW_SEC = 7 * 24 * 60 * 60L

/** Home's Upcoming Games: the next 48 hours. */
const val HOME_WINDOW_SEC = 48 * 60 * 60L

/** Home's Upcoming Games: 10 games at most. */
const val HOME_GAMES = 10

// A game that started up to 3 hours ago may still be on, as the sports data reads a schedule.
private const val GAME_ON_SEC = 3 * 60 * 60L

/** What is followed, by source and id: a renamed team or a new logo is not another choice. */
fun FollowedSports.Followed.keys(): Set<String> =
    teams.mapTo(HashSet()) { "team:${it.source.id}:${it.id}" } + leagues.map { "league:${it.source.id}:${it.id}" }

/**
 * The games of the followed teams and leagues ([lists], one per team or league), merged: the ones
 * not over that start before [nowSec] + [windowSec], soonest first, each once — a game of a
 * followed team in a followed league is in both lists, by the same key or the same teams and time
 * ([TeamNames.sameGame]) — [limit] at most.
 */
fun mergeFollowedGames(
    lists: List<List<SportsGame>>,
    nowSec: Long,
    windowSec: Long,
    limit: Int = Int.MAX_VALUE,
): List<SportsGame> {
    val kept = ArrayList<SportsGame>()
    lists
        .flatten()
        .filter { it.state != GameState.FINISHED && it.state != GameState.CANCELLED }
        .filter { it.startEpochSec >= nowSec - GAME_ON_SEC && it.startEpochSec <= nowSec + windowSec }
        .sortedBy { it.startEpochSec }
        .forEach { game -> if (kept.none { it.key == game.key || TeamNames.sameGame(it, game) }) kept += game }
    return kept.take(limit)
}

/**
 * Reads the games of every team and league in [followed], all at once (the sports data keeps each
 * schedule in its cache). A read that fails leaves its games out; when every read fails, the first
 * failure is thrown ("Couldn't load games").
 */
suspend fun readFollowedGames(
    data: SportsPageData,
    followed: FollowedSports.Followed,
): List<List<SportsGame>> =
    coroutineScope {
        val reads =
            followed.teams.map { team -> async { attempt { data.teamGames(team) } } } +
                followed.leagues.map { league -> async { attempt { data.leagueGames(league) } } }
        val results = reads.awaitAll()
        val failures = results.mapNotNull { it.exceptionOrNull() }
        if (results.isNotEmpty() && failures.size == results.size) throw failures.first()
        results.mapNotNull { it.getOrNull() }
    }

private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Games failed: ${e.javaClass.simpleName}")
        Result.failure(e)
    }

private const val TAG = "FollowedGames"

/**
 * The Sports section (TV rail item, phone tab; the sports teams plan → Sports section): what the
 * profile follows ([followed], null until read), and the games of all of it in the next 7 days by
 * day, each once, with their channels as the matcher finds them ([games]). A change to what is
 * followed reads the games again; Retry after a failure too.
 */
class SportsSectionViewModel(
    data: SportsPageData,
    private val now: () -> Long = System::currentTimeMillis,
) : SportsGamesViewModel(data) {
    private val _followed = MutableStateFlow<FollowedSports.Followed?>(null)
    val followed: StateFlow<FollowedSports.Followed?> = _followed.asStateFlow()

    init {
        viewModelScope.launchGuarded("SportsSectionViewModel.followed") {
            var readFor: Set<String>? = null
            data.followed().collect { followed ->
                _followed.value = followed
                val keys = followed.keys()
                if (keys != readFor) {
                    readFor = keys
                    if (!followed.isEmpty) load()
                }
            }
        }
    }

    override suspend fun loadGames(): List<SportsGame> {
        val followed = _followed.value ?: FollowedSports.Followed()
        return mergeFollowedGames(readFollowedGames(data, followed), now() / 1000, SECTION_WINDOW_SEC)
    }

    class Factory(
        context: Context,
    ) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SportsSectionViewModel::class.java)) { "Unknown ViewModel class: ${modelClass.name}" }
            return SportsSectionViewModel(AppSportsPageData(appContext)) as T
        }
    }
}

/** A game on Home's Upcoming Games row and the profile's channels for it, best first. */
data class UpcomingGame(
    val game: SportsGame,
    val channels: List<GameChannel>,
) {
    /** The channel the card shows: the best one, if any. */
    val bestChannel: GameChannel? get() = channels.firstOrNull()
}

/**
 * Home's Upcoming Games row (the sports teams plan → Home row): the next [HOME_GAMES] games of the
 * followed teams and leagues in the next 48 hours, each with its channels. Kept in memory per
 * profile and followed set, and read again at most every [REFRESH_MS] (Home asks on each return),
 * so going back to Home costs nothing. Nothing when [enabled] is false (the Admin switch is off, or
 * a child profile) or nothing is followed. A failed read is not kept: the next visit tries again.
 */
class UpcomingGamesLoader(
    private val data: SportsPageData,
    private val enabled: () -> Boolean,
    private val profileId: () -> String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Kept(
        val profileId: String,
        val keys: Set<String>,
        val atMs: Long,
        val games: List<UpcomingGame>,
    )

    private var kept: Kept? = null
    private val lock = Mutex()

    /** The row's games, soonest first; empty when the row is absent. */
    suspend fun load(): List<UpcomingGame> {
        val followed = if (enabled()) data.followed().first() else FollowedSports.Followed()
        return if (followed.isEmpty) emptyList() else lock.withLock { keptOrRead(followed) }
    }

    /** Forgets the row, so the next [load] reads it again. */
    suspend fun clear() = lock.withLock { kept = null }

    private suspend fun keptOrRead(followed: FollowedSports.Followed): List<UpcomingGame> {
        val id = profileId()
        val keys = followed.keys()
        val nowMs = now()
        val fresh = kept?.takeIf { it.profileId == id && it.keys == keys && nowMs - it.atMs < REFRESH_MS }
        return fresh?.games?.let { stillComing(it, nowMs / 1000) } ?: read(id, keys, followed, nowMs)
    }

    private suspend fun read(
        id: String,
        keys: Set<String>,
        followed: FollowedSports.Followed,
        nowMs: Long,
    ): List<UpcomingGame> {
        val games =
            try {
                mergeFollowedGames(readFollowedGames(data, followed), nowMs / 1000, HOME_WINDOW_SEC, HOME_GAMES)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Every read failed (no network, a source error): no row, and nothing kept.
                Log.w(TAG, "Upcoming games failed: ${e.javaClass.simpleName}")
                null
            }
        val row = games?.let { matched(it) }
        if (row != null) kept = Kept(id, keys, nowMs, row)
        return row.orEmpty()
    }

    private suspend fun matched(games: List<SportsGame>): List<UpcomingGame> {
        if (games.isEmpty()) return emptyList()
        val matcher =
            try {
                data.matcher()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The channels or the guide could not be read: the cards show no channel.
                Log.w(TAG, "Channels failed: ${e.javaClass.simpleName}")
                GameMatcher { emptyList() }
            }
        return games.map { game ->
            val channels =
                try {
                    matcher.match(game)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Match failed: ${e.javaClass.simpleName}")
                    emptyList()
                }
            UpcomingGame(game, channels)
        }
    }

    // A kept row without the games that have ended since it was read.
    private fun stillComing(
        games: List<UpcomingGame>,
        nowSec: Long,
    ): List<UpcomingGame> = games.filter { it.game.startEpochSec >= nowSec - GAME_ON_SEC }

    companion object {
        /** How long a read row is shown again before it is read again. */
        const val REFRESH_MS = 15 * 60 * 1000L
    }
}

/** The app's [UpcomingGamesLoader]: the sports data, the profile in use, Admin's Sports switch. */
object UpcomingGames {
    @Volatile
    private var instance: UpcomingGamesLoader? = null

    /** Whether the row may show at all on this device for the profile in use (SP-D8, SP-D9). */
    fun enabled(context: Context): Boolean {
        val settings = AppSettings(context.applicationContext)
        return settings.showSports && !settings.isChildProfile(settings.activeProfileId)
    }

    fun loader(context: Context): UpcomingGamesLoader =
        instance ?: synchronized(this) {
            instance ?: run {
                val app = context.applicationContext
                val settings = AppSettings(app)
                UpcomingGamesLoader(AppSportsPageData(app), enabled = { enabled(app) }, profileId = { settings.activeProfileId })
            }.also { instance = it }
        }

    suspend fun load(context: Context): List<UpcomingGame> = loader(context).load()
}
