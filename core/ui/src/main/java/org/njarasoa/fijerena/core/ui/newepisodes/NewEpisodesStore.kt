package org.njarasoa.fijerena.core.ui.newepisodes

import android.content.Context
import android.util.Log
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.player.diagnostics.AppScopes
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.sourceKey
import org.njarasoa.fijerena.core.ui.history.MergedLists

/** The New Episodes of the profile in use, as the screens read them. */
data class NewEpisodesState(
    val shows: List<NewEpisodeShow> = emptyList(),
    /** The newest find that a banner already told this profile about, on this device. */
    private val toldAt: Long = 0L,
) {
    private val byKey: Map<String, NewEpisodeShow> = shows.associateBy { it.key }

    /** The shows the banner tells about: found since the last banner. */
    val announced: List<NewEpisodeShow> = shows.filter { it.foundAt > toldAt }

    /** Whether the card of [item] (a show) has the New badge. */
    fun isNew(item: MediaItem): Boolean = item.sourceKey in byKey

    /** The new episodes of the show [item], empty for any other. */
    fun episodeIds(item: MediaItem): Set<String> = byKey[item.sourceKey]?.episodeIds.orEmpty().toSet()

    /** The new episodes of a show by its source and id, for a screen without a [MediaItem]. */
    fun episodeIds(
        providerId: Long,
        seriesId: String,
    ): Set<String> =
        shows
            .firstOrNull { it.item.providerId == providerId && it.item.id == seriesId }
            ?.episodeIds
            .orEmpty()
            .toSet()
}

/** The state for the screens; provided at the root of each app (`NewEpisodesStore.state`). */
val LocalNewEpisodes = compositionLocalOf { NewEpisodesState() }

/**
 * The New Episodes of the profile in use, held in memory and worked out again by [refresh]: after
 * a change in `new_episode`, `watch_state` or `favorite_state` ([followChanges]) and on a profile
 * switch. Never stored: each device works it out from its own catalogue. See the new episode
 * alerts plan.
 */
class NewEpisodesStore internal constructor(
    private val context: Context,
) {
    private val _state = MutableStateFlow(NewEpisodesState())
    val state: StateFlow<NewEpisodesState> = _state.asStateFlow()

    private val mutex = Mutex()

    // Outlives the screen that asks: Home marks its banner told as it leaves.
    private val scope = AppScopes.create("NewEpisodesStore", Dispatchers.IO)

    /** Works out [state] again. Never throws: a failure is logged and [state] stays as it was. */
    suspend fun refresh() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val settings = AppSettings(context)
                    val shows = MergedLists.forSections(context)?.let { NewEpisodes.forProfile(it) }.orEmpty()
                    _state.value = NewEpisodesState(shows, settings.newEpisodesToldAt(settings.activeProfileId))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "New episodes not worked out", e)
                    CrashLog.record("NewEpisodesStore.refresh", e)
                }
            }
        }
    }

    /**
     * Runs as long as the process: [refresh] after each change of the tables the state reads (a
     * play, a watched mark or a favourite on this device or from live sync, a find of the check).
     */
    @OptIn(FlowPreview::class)
    suspend fun followChanges() {
        XtreamDatabase
            .getInstance(context)
            .invalidationTracker
            .createFlow("new_episode", "watch_state", "favorite_state", "xtream_categories")
            .debounce(REFRESH_DEBOUNCE_MS)
            .collect { refresh() }
    }

    /** A banner told the profile about [announced]: it does not tell them again, on this device. */
    suspend fun markTold(announced: List<NewEpisodeShow>) {
        val newest = announced.maxOfOrNull { it.foundAt } ?: return
        val settings = AppSettings(context)
        settings.setNewEpisodesToldAt(settings.activeProfileId, newest)
        refresh()
    }

    /** [markTold] in the background, for a screen that is leaving. */
    fun markToldLater(announced: List<NewEpisodeShow>) {
        if (announced.isNotEmpty()) scope.launch { markTold(announced) }
    }

    companion object {
        private const val TAG = "NewEpisodesStore"
        private const val REFRESH_DEBOUNCE_MS = 500L

        @Volatile
        private var instance: NewEpisodesStore? = null

        fun getInstance(context: Context): NewEpisodesStore =
            instance ?: synchronized(this) {
                instance ?: NewEpisodesStore(context.applicationContext).also { instance = it }
            }
    }
}
