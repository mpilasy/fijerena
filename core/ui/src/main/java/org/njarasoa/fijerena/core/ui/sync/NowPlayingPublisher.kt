package org.njarasoa.fijerena.core.ui.sync

import android.app.Application
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.sync.SyncAccountStore
import org.njarasoa.fijerena.core.network.sync.SyncKey
import org.njarasoa.fijerena.core.network.sync.SyncKind
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.network.sync.VolatileRecords
import org.njarasoa.fijerena.core.player.diagnostics.AppScopes
import org.njarasoa.fijerena.core.player.model.NowPlayingSnapshot
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService

/**
 * Tells the sync group what this device is playing, when its owner turned that on — see
 * `docs/plans/20261001_live-sync-now-playing-plan.md` → Publishing. Sends on a change of item or
 * of playing / paused / stopped (debounced, so zapping through channels sends one record), a
 * heartbeat while something is on, and `stopped` once it ends or sharing is turned off. Each send
 * queues one [SyncKind.NOW_PLAYING] record in [VolatileRecords] and asks for a sync pass.
 */
class NowPlayingPublisher private constructor(
    private val app: Application,
) {
    private val scope = AppScopes.create("NowPlayingPublisher", Dispatchers.IO)
    private val settings = AppSettings(app)
    private val store = SyncAccountStore(app)
    private val sharing = MutableStateFlow(settings.shareNowPlaying)

    /** The device-local "Share what's playing" switch. */
    val isSharing: StateFlow<Boolean> = sharing.asStateFlow()

    fun setSharing(enabled: Boolean) {
        settings.shareNowPlaying = enabled
        sharing.value = enabled
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun start() {
        val shared =
            combine(sharing, StreamingPlaybackService.nowPlaying) { on, playing -> playing.takeIf { on } }
                .stateIn(scope, SharingStarted.Eagerly, null)
        scope.launch { sends(shared).collect { send(it) } }
    }

    /**
     * Leaving the group: queues a last `stopped` (when sharing) for the caller's final pass, so
     * other devices drop this one's line at once rather than when it goes stale.
     */
    suspend fun stopBeforeLeaving() {
        if (sharing.value) queue(null)
    }

    private suspend fun send(snapshot: NowPlayingSnapshot?) {
        if (queue(snapshot)) SyncManager.getInstance(app).requestSync(0)
    }

    /** False when not linked: there is no device id to key the record by, and nobody to tell. */
    private suspend fun queue(snapshot: NowPlayingSnapshot?): Boolean {
        val link = store.link
        if (link != null) {
            val record = SyncPayloads.NowPlaying.of(snapshot, profileName(), System.currentTimeMillis())
            VolatileRecords.outbox.put(SyncKey(SyncKind.SHARED, "", SyncKind.NOW_PLAYING, link.deviceId), SyncPayloads.encode(record))
        }
        return link != null
    }

    private suspend fun profileName(): String {
        val active = settings.activeProfileId
        return SettingsDatabase.getInstance(app).profileDao().getAll().firstOrNull { it.id == active }?.name.orEmpty()
    }

    companion object {
        const val DEBOUNCE_MS = 2_000L
        const val HEARTBEAT_MS = 60_000L

        @Volatile private var instance: NowPlayingPublisher? = null

        fun getInstance(app: Application): NowPlayingPublisher =
            instance ?: synchronized(this) {
                instance ?: NowPlayingPublisher(app).also { instance = it }
            }

        /**
         * What to send, and when, for [playing] (null: nothing, or not shared): the latest state
         * [DEBOUNCE_MS] after the shown item or its playing / paused state last changed — position
         * changes alone don't count — then again every [HEARTBEAT_MS] while something is on, so a
         * receiver can tell a device still playing from one switched off at the wall. Null (stopped)
         * only after something was sent: a device that never shared has nothing to clear.
         */
        @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
        internal fun sends(playing: StateFlow<NowPlayingSnapshot?>): Flow<NowPlayingSnapshot?> {
            val schedule =
                playing
                    .map { it?.shown() }
                    .distinctUntilChanged()
                    .debounce(DEBOUNCE_MS)
                    .transformLatest { shown ->
                        emit(playing.value)
                        while (shown != null) {
                            delay(HEARTBEAT_MS)
                            emit(playing.value)
                        }
                    }
            return flow {
                var sentSomething = false
                schedule.collect { snapshot ->
                    if (snapshot != null || sentSomething) emit(snapshot)
                    sentSomething = snapshot != null
                }
            }
        }
    }
}
