package org.njarasoa.fijerena.core.network.sports

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The sports answers read, one file per request under [dir] (`cacheDir/sports`; the sports teams
 * plan → Cache). Same pattern as `TmdbChartPages` and `TmdbPeople`, but the raw answer is kept and
 * parsed again on each read, so a parser change needs no new file format.
 *
 * A fresh file is used without a call. A call that fails, or whose answer does not parse, falls
 * back to the file, however old it is; with no file the failure goes to the caller.
 */
class SportsCache(
    private val dir: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Serializable
    private data class Stored(
        val fetchedAtMs: Long,
        val body: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    // One lock per request, so two screens asking the same thing make one call.
    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * The answer to the request [key], parsed with [parse]: the stored one while it is younger than
     * [maxAgeMs] of it, else [fetch]'s, stored. See the class kdoc for a failed call.
     */
    suspend fun <T> get(
        key: String,
        parse: (String) -> T,
        maxAgeMs: (T) -> Long,
        fetch: suspend () -> String,
    ): T =
        locks.getOrPut(key) { Mutex() }.withLock {
            val stored = read(key)
            val storedValue = stored?.let { parseOrNull(key, it.body, parse) }
            if (stored != null && storedValue != null && now() - stored.fetchedAtMs in 0 until maxAgeMs(storedValue)) {
                return@withLock storedValue
            }
            try {
                val body = fetch()
                val value = parse(body)
                write(key, Stored(now(), body))
                value
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (storedValue == null) throw e
                Log.w(TAG, "Sports $key: ${e.message}; the stored answer is used")
                storedValue
            }
        }

    /** Forgets every stored answer. */
    suspend fun clear() {
        withContext(Dispatchers.IO) { dir.listFiles()?.forEach { it.delete() } }
    }

    private fun <T> parseOrNull(
        key: String,
        body: String,
        parse: (String) -> T,
    ): T? =
        try {
            parse(body)
        } catch (e: Exception) {
            // cancellation-ok: parse is not a suspend call
            Log.w(TAG, "Sports $key: the stored answer does not parse", e)
            null
        }

    private fun file(key: String) = File(dir, key.replace(UNSAFE, "_") + ".json")

    private suspend fun read(key: String): Stored? =
        withContext(Dispatchers.IO) {
            try {
                file(key).takeIf { it.exists() }?.readText()?.let { json.decodeFromString<Stored>(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Sports $key unreadable; read again", e)
                null
            }
        }

    private suspend fun write(
        key: String,
        stored: Stored,
    ) = withContext(Dispatchers.IO) {
        try {
            dir.mkdirs()
            file(key).writeText(json.encodeToString(Stored.serializer(), stored))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The next read calls the source again.
            Log.w(TAG, "Sports $key not stored", e)
        }
    }

    companion object {
        private const val TAG = "SportsCache"
        private val UNSAFE = Regex("[^A-Za-z0-9._-]")

        const val HOUR_MS = 60 * 60 * 1000L
        const val DAY_MS = 24 * HOUR_MS

        /** A team schedule or a league's games: 6 hours. */
        const val SCHEDULE_MAX_AGE_MS = 6 * HOUR_MS

        /** As [SCHEDULE_MAX_AGE_MS] with a game in the next 2 days: 1 hour (TV is often named late). */
        const val SCHEDULE_SOON_MAX_AGE_MS = HOUR_MS

        /** UEFA's broadcasters of a week: 6 hours. */
        const val BROADCASTERS_MAX_AGE_MS = 6 * HOUR_MS

        /** League and team lists: 7 days. */
        const val LIST_MAX_AGE_MS = 7 * DAY_MS

        /**
         * How long [games] stay fresh at [nowMs]: [SCHEDULE_SOON_MAX_AGE_MS] when one that has not
         * ended starts in the next 2 days, else [SCHEDULE_MAX_AGE_MS].
         */
        fun scheduleMaxAgeMs(
            games: List<SportsGame>,
            nowMs: Long,
        ): Long {
            val nowSec = nowMs / 1000
            val soon =
                games.any { game ->
                    game.state != GameState.FINISHED &&
                        game.state != GameState.CANCELLED &&
                        game.startEpochSec - nowSec in -SOON_PAST_SEC..SOON_AHEAD_SEC
                }
            return if (soon) SCHEDULE_SOON_MAX_AGE_MS else SCHEDULE_MAX_AGE_MS
        }

        private const val SOON_AHEAD_SEC = 2 * 24 * 60 * 60L

        // A game that started up to 3 hours ago may still be on.
        private const val SOON_PAST_SEC = 3 * 60 * 60L
    }
}
