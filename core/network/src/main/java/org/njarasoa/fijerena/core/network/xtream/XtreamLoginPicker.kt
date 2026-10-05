package org.njarasoa.fijerena.core.network.xtream

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SourceLogins
import org.njarasoa.fijerena.core.player.api.XtreamStreamUrl
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.source.AlternateLogin
import java.util.concurrent.ConcurrentHashMap

/**
 * Which of an Xtream source's logins a playback uses (shared logins, Phase 2), and another one
 * when the panel refuses it (Phase 3, as the player's [AlternateLogin]). A source with only its
 * main login costs nothing: no panel request, the URL as built. With extra logins, every login's
 * `player_api.php` is asked in parallel (3 s each) and the first free one is taken, preferring the
 * one this device is playing on now (a channel change replaces its own stream) and then the one it
 * used last. Nothing free or nothing answers: the main login, as before. Process-wide state, like
 * the player's. See docs/plans/20261005_shared-logins-plan.md.
 */
object XtreamLoginPicker : AlternateLogin {
    private const val CHECK_TIMEOUT_MS = 3_000L

    /** How long a refused login is skipped on this device: longer than bears' ~5 min lock (460). */
    const val BUSY_MS = 6 * 60_000L

    /** Picks remembered by URL, for [loginCount], [describe] and [next]; only the latest few. */
    private const val PICKS_KEPT = 16

    /** What the panel said about one login; [status] null when it didn't answer in time. */
    data class Candidate(
        val login: ProviderRepository.Login,
        val status: XtreamLoginCheck.Status?,
    )

    private data class Pick(
        val providerId: Long,
        val index: Int,
        val count: Int,
    )

    @Volatile
    private var appContext: Context? = null
    private val lastUsed = ConcurrentHashMap<Long, String>()
    private val busyUntil = ConcurrentHashMap<Pair<Long, String>, Long>()
    private val picks = java.util.Collections.synchronizedMap(LinkedHashMap<String, Pick>())

    /** Called once at app start: from then on the player can switch logins on a refusal. */
    fun install(context: Context) {
        appContext = context.applicationContext
        AlternateLogin.installed = this
    }

    /**
     * The first free login among [candidates]: the one this device plays on now ([mine]; free when
     * the panel counts no more than its own stream), then [lastUsed], then list order (main first).
     * A login is free when the panel says it is active and has a connection left; unknown counts
     * count as free. Null when none is.
     */
    fun choose(
        candidates: List<Candidate>,
        mine: String?,
        lastUsed: String?,
    ): ProviderRepository.Login? {
        val ordered =
            candidates.sortedBy {
                if (it.login.username == mine) {
                    0
                } else if (it.login.username == lastUsed) {
                    1
                } else {
                    2
                }
            }
        return ordered
            .firstOrNull { candidate ->
                val status = candidate.status
                val active = status?.activeCons
                val max = status?.maxConnections
                val allowance = if (candidate.login.username == mine) 1 else 0
                status != null && status.active && (active == null || max == null || active < max + allowance)
            }?.login
    }

    /** [url] (built with the main login of source [providerId]) on the login this playback should use. */
    suspend fun forPlayback(
        providerId: Long,
        url: String,
    ): String {
        val repository = appContext?.let { ProviderRepository(it) }
        val entity = repository?.getProviderById(providerId)?.takeIf { it.type == "XTREAM" }
        val logins = entity?.let { withContext(Dispatchers.IO) { repository.getSourceLogins(it) } }
        val chosen =
            if (entity == null || logins == null || logins.extras.isEmpty()) {
                null
            } else {
                choose(statuses(entity, logins), playingUsername(), lastUsed[entity.id])
            }
        val login = chosen ?: logins?.main
        val result = if (entity != null && login != null) swap(entity, logins, url, login) else url
        return result
    }

    override suspend fun next(refusedUri: String): String? {
        val context = appContext
        val pick = picks[refusedUri]
        val refused = XtreamStreamUrl.username(refusedUri)
        var result: String? = null
        if (context != null && pick != null && refused != null && pick.count > 1) {
            busyUntil[pick.providerId to refused] = System.currentTimeMillis() + BUSY_MS
            val repository = ProviderRepository(context)
            val entity = repository.getProviderById(pick.providerId)
            val logins = entity?.let { withContext(Dispatchers.IO) { repository.getSourceLogins(it) } }
            val chosen = if (entity != null && logins != null) choose(statuses(entity, logins), null, null) else null
            if (entity != null && chosen != null && chosen.username != refused) {
                result = swap(entity, logins, refusedUri, chosen)
            }
        }
        return result
    }

    override fun loginCount(uri: String): Int = picks[uri]?.count ?: 1

    /** "Login 2 of 3" for developer mode: [uri]'s login's place among its source's, or null. */
    fun describe(uri: String): Pair<Int, Int>? = picks[uri]?.takeIf { it.count > 1 }?.let { it.index + 1 to it.count }

    /** Every login with a password that isn't marked busy, with what the panel says, in parallel. */
    private suspend fun statuses(
        entity: ProviderEntity,
        logins: SourceLogins,
    ): List<Candidate> {
        val now = System.currentTimeMillis()
        val usable = logins.all.filter { it.password.isNotEmpty() && (busyUntil[entity.id to it.username] ?: 0L) <= now }
        return coroutineScope {
            usable
                .map { login ->
                    async {
                        val status = withTimeoutOrNull(CHECK_TIMEOUT_MS) { XtreamLoginCheck.status(entity.url, login).getOrNull() }
                        Candidate(login, status)
                    }
                }.awaitAll()
        }
    }

    /** [url] on [login], remembered as this device's pick for its source. */
    private fun swap(
        entity: ProviderEntity,
        logins: SourceLogins?,
        url: String,
        login: ProviderRepository.Login,
    ): String {
        val swapped = XtreamStreamUrl.withLogin(url, login.username, login.password) ?: url
        val all = logins?.all.orEmpty()
        lastUsed[entity.id] = login.username
        synchronized(picks) {
            picks[swapped] = Pick(entity.id, all.indexOfFirst { it.username == login.username }.coerceAtLeast(0), all.size.coerceAtLeast(1))
            while (picks.size > PICKS_KEPT) picks.remove(picks.keys.first())
        }
        return swapped
    }

    /** The login of the stream this device is playing now, if any. */
    private fun playingUsername(): String? =
        StreamingPlaybackService.nowPlaying.value?.let {
            StreamingPlaybackService
                .getInstance()
                ?.currentMetadata
                ?.value
                ?.streamUrl
                ?.let(XtreamStreamUrl::username)
        }
}
