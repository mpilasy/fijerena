package org.njarasoa.fijerena.core.network.wanted

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.network.xtream.db.WantedTitleEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.player.diagnostics.AppScopes
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog
import org.njarasoa.fijerena.core.player.domain.ContentType

/**
 * The Wanted list of the profile in use, which of its titles the profile's sources have, and the
 * profile's favourites that their source no longer lists — held in memory and worked out again by
 * [refresh]. Availability and gone favourites are never stored: each device works them out from
 * its own catalogue. A child profile gets an empty [State]: the feature is hidden there. See
 * the wanted titles plan.
 */
class WantedTitles internal constructor(
    private val context: Context,
) {
    /** A Wanted title's identity: `MOVIES` or `TV_SHOWS`, and its TMDB id. */
    data class Key(
        val contentType: String,
        val tmdbId: String,
    )

    data class State(
        /** The profile's list, newest first. */
        val titles: List<WantedTitleEntity> = emptyList(),
        /** Where each available title is; a title missing here is not available. */
        val available: Map<Key, List<CatalogueHit>> = emptyMap(),
        val goneFavorites: List<GoneFavorite> = emptyList(),
        /** Each Xtream source's name by id while the profile has more than one: "Available · panel-a". */
        val sourceNames: Map<Long, String> = emptyMap(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val mutex = Mutex()

    // Outlives the screen that asks: Home marks its banner told as it leaves.
    private val scope = AppScopes.create("WantedTitles", Dispatchers.IO)
    private val db get() = XtreamDatabase.getInstance(context)

    /**
     * Works out [state] again for the profile in use: after a catalogue sync, at start, on a
     * profile switch, and when live sync changes the list or favourites. Never throws: a failure
     * is logged and recorded in [CrashLog], and [state] stays as it was.
     */
    suspend fun refresh() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    _state.value = compute()
                    // Then the release data of what no source has, at most weekly, and the state again.
                    if (refreshReleases(_state.value)) _state.value = compute()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Wanted titles and gone favourites not worked out", e)
                    CrashLog.record("WantedTitles.refresh", e)
                }
            }
        }
    }

    private suspend fun compute(): State {
        val settings = AppSettings(context)
        val profileId = settings.activeProfileId
        return if (settings.isChildProfile(profileId)) State() else compute(profileId)
    }

    private suspend fun compute(profileId: String): State {
        val xtreamSources =
            ProviderRepository(context)
                .profileSourcesList(profileId)
                .filter { it.type == XTREAM }
        val sources = xtreamSources.map { it.id }
        val titles = db.wantedTitleDao().getAll(profileId)
        val lookup = CatalogueLookup(db)
        val available =
            titles
                .mapNotNull { title ->
                    lookup
                        .find(sources, title.contentType, title.tmdbId, listOfNotNull(title.title, title.originalTitle), title.year)
                        .takeIf { it.isNotEmpty() }
                        ?.let { Key(title.contentType, title.tmdbId) to it }
                }.toMap()
        return State(
            titles,
            available,
            FavoriteCatalogueCheck.run(db, profileId, sources),
            if (xtreamSources.size > 1) xtreamSources.associate { it.id to it.name } else emptyMap(),
        )
    }

    /** Adds a title to the profile in use's list (live sync sends it), or refreshes its details. */
    suspend fun add(title: WantedTitleEntity) {
        db.wantedTitleDao().upsertClearingTombstone(title)
        refresh()
    }

    /**
     * Reads from TMDB the release of each title no source has, when it was never read or is a week
     * old (wishlist UX plan, Phase 3). Returns whether any row changed. A failure leaves that title
     * for next time.
     */
    private suspend fun refreshReleases(state: State): Boolean {
        val tmdb = TmdbApiService.getInstance(context)
        val now = System.currentTimeMillis()
        val stale =
            state.titles.filter { title ->
                Key(title.contentType, title.tmdbId) !in state.available &&
                    (title.releaseCheckedAt == null || now - title.releaseCheckedAt > RELEASE_REFRESH_MS)
            }
        val region =
            java.util.Locale
                .getDefault()
                .country
        var changed = false
        if (tmdb.hasApiKey()) {
            for (title in stale) {
                val id = title.tmdbId.toIntOrNull() ?: continue
                val info =
                    try {
                        if (title.contentType == ContentType.MOVIES) {
                            ReleaseStatus.ofMovie(tmdb.getMovieReleaseDates(id), region)
                        } else {
                            ReleaseStatus.ofShow(tmdb.getTvDetails(id))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "TMDB release of ${title.contentType} ${title.tmdbId}: ${e.message}")
                        null
                    } ?: continue
                db.wantedTitleDao().setRelease(
                    title.profileId,
                    title.contentType,
                    title.tmdbId,
                    info.releaseDate,
                    info.digitalDate,
                    info.inCinemas,
                    info.showStatus,
                    now,
                )
                changed = true
            }
        }
        return changed
    }

    /**
     * Adds a film or show from its page (wishlist UX plan, Phase 1): TMDB's title, original title,
     * year and poster when TMDB answers, else [fallbackTitle] and [fallbackYear].
     */
    suspend fun addFromTmdb(
        contentType: String,
        tmdbId: String,
        fallbackTitle: String,
        fallbackYear: Int?,
    ) {
        val tmdb = TmdbApiService.getInstance(context)
        val id = tmdbId.toIntOrNull()
        val details =
            if (id == null || !tmdb.hasApiKey()) {
                null
            } else {
                try {
                    if (contentType == ContentType.MOVIES) tmdb.getMovieDetails(id) else tmdb.getTvDetails(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "TMDB details for $contentType $tmdbId: ${e.message}")
                    null
                }
            }
        val title =
            details?.let { TmdbTitle.of(it.copy(mediaType = if (contentType == ContentType.MOVIES) "movie" else "tv")) }
                ?: TmdbTitle(contentType, tmdbId, fallbackTitle, null, fallbackYear, null, null)
        add(title.toWanted(AppSettings(context).activeProfileId, System.currentTimeMillis()))
    }

    /** The "remove it now you've watched it?" prompt for [key] was answered: never asked again, on any device. */
    suspend fun markAsked(key: Key) {
        db.wantedTitleDao().markAsked(AppSettings(context).activeProfileId, key.contentType, key.tmdbId, System.currentTimeMillis())
        refresh()
    }

    /** Removes a title from the profile in use's list, recording the removal for live sync. */
    suspend fun remove(key: Key) {
        db.wantedTitleDao().deleteRecordingTombstone(AppSettings(context).activeProfileId, key.contentType, key.tmdbId)
        refresh()
    }

    /** The user opened [key] while a source had it: its badge goes. */
    suspend fun markSeen(key: Key) {
        db.wantedTitleDao().markSeen(AppSettings(context).activeProfileId, key.contentType, key.tmdbId, System.currentTimeMillis())
        refresh()
    }

    /** A banner told the user that [keys] are available: it doesn't tell them again, on any device. */
    suspend fun markNotified(keys: Collection<Key>) {
        val profileId = AppSettings(context).activeProfileId
        val now = System.currentTimeMillis()
        val dao = db.wantedTitleDao()
        db.withTransaction { keys.forEach { dao.markNotified(profileId, it.contentType, it.tmdbId, now) } }
        refresh()
    }

    /** [markNotified] in the background, for a screen that is leaving. */
    fun markNotifiedLater(keys: Collection<Key>) {
        if (keys.isNotEmpty()) scope.launch { markNotified(keys) }
    }

    companion object {
        private const val TAG = "WantedTitles"
        private const val XTREAM = "XTREAM"
        private const val RELEASE_REFRESH_MS = 7L * 24 * 60 * 60 * 1000

        @Volatile
        private var instance: WantedTitles? = null

        fun getInstance(context: Context): WantedTitles =
            instance ?: synchronized(this) {
                instance ?: WantedTitles(context.applicationContext).also { instance = it }
            }
    }
}
