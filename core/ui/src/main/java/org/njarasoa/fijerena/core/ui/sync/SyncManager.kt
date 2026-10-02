package org.njarasoa.fijerena.core.ui.sync

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.room.InvalidationTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.sync.SyncAccountStore
import org.njarasoa.fijerena.core.network.sync.SyncApi
import org.njarasoa.fijerena.core.network.sync.SyncApiException
import org.njarasoa.fijerena.core.network.sync.SyncEngine
import org.njarasoa.fijerena.core.network.sync.SyncWire
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.player.diagnostics.AppScopes
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog
import org.njarasoa.fijerena.core.ui.di.AppContainer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs live sync while the app is in use — see `docs/plans/20260929_live-sync-plan.md` → Flow.
 *
 * - **Foreground**: a WebSocket to the server; its `{"head": n}` messages trigger a pull. A text
 *   `ping` every 30 s keeps reverse proxies from dropping the idle connection (the server answers
 *   without waking up).
 * - **Local changes**: any write to either `sync_version` table schedules a push a few seconds
 *   later, so a burst (playback progress, a batch of favourites) goes in one request.
 * - **Start and stop**: a full pass when the app comes to the foreground (catch-up) and when it
 *   leaves it (flush what is pending). No background work: a closed app catches up when opened.
 * - Failures retry with backoff while in the foreground. A refused token (revoked) stops retrying.
 */
class SyncManager internal constructor(
    private val app: Application,
    // Parameters, internal, only so SyncManagerSocketTest can run the socket lifecycle on a test
    // dispatcher against a fake socket factory ([SyncApi.openSocket]); the app uses the defaults.
    private val scope: CoroutineScope = AppScopes.create("SyncManager", Dispatchers.IO),
    private val store: SyncAccountStore = SyncAccountStore(app),
    private val engine: SyncEngine = SyncEngine(app, store = store),
    private val api: SyncApi = SyncApi(),
) {
    private val startedActivities = AtomicInteger(0)

    /** What the sync settings screen shows. */
    data class Status(
        val linked: Boolean = false,
        val serverUrl: String? = null,
        val syncing: Boolean = false,
        val lastSyncAt: Long = 0,
        val lastError: String? = null,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private fun refreshStatus(syncing: Boolean = false) {
        val link = store.link
        _status.value = Status(link != null, link?.serverUrl, syncing, store.lastSyncAt, store.lastError)
    }

    @Volatile private var foreground = false

    @Volatile private var socket: WebSocket? = null
    private var socketJob: Job? = null

    /** A pass waiting for its delay; replaced by each new request. Never the pass that is running. */
    private var scheduled: Job? = null
    private val passRunning = AtomicBoolean(false)

    /** Something asked for a pass while one was running: run another when it ends. */
    @Volatile private var passAgain = false
    private var retryDelayMs = INITIAL_RETRY_MS

    private val listener =
        object : SyncEngine.Listener {
            override fun onUserDataChanged(providerIds: Set<Long>) {
                scope.launch { AppContainer.getInstance(app).reloadAfterRemoteChange(providerIds) }
            }

            override fun onProvidersChanged(providerIds: Set<Long>) {
                // A password, URL, login or filter changed on another device: the next screen to
                // ask gets a repository built with it. See R-06 step 2 of
                // docs/plans/20261002_next-level-rock-solid-resilience-plan.md.
                scope.launch { AppContainer.getInstance(app).onProvidersChanged(providerIds) }
            }

            override fun onActiveProfileDeleted() {
                scope.launch {
                    // Another device deleted the profile this one is using: move to another, then
                    // sync again so the deletion can land.
                    val active = AppSettings(app).activeProfileId
                    val other =
                        SettingsDatabase
                            .getInstance(app)
                            .profileDao()
                            .getAll()
                            .firstOrNull { it.id != active } ?: return@launch
                    Log.i(TAG, "Active profile deleted on another device; switching to ${other.id}")
                    AppContainer.getInstance(app).switchProfileExternally(other.id)
                    requestSync(0)
                }
            }
        }

    fun start() {
        // The store is encrypted prefs: read it off the main thread.
        scope.launch { refreshStatus() }
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    if (startedActivities.incrementAndGet() == 1) onForeground()
                }

                override fun onActivityStopped(activity: Activity) {
                    if (startedActivities.decrementAndGet() == 0) onBackground()
                }

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
        scope.launch {
            val observer =
                object : InvalidationTracker.Observer(arrayOf("sync_version")) {
                    override fun onInvalidated(tables: Set<String>) = onLocalChange()
                }
            SettingsDatabase.getInstance(app).invalidationTracker.addObserver(observer)
            XtreamDatabase.getInstance(app).invalidationTracker.addObserver(observer)
        }
    }

    /** Linked or unlinked just now (settings, or the debug receiver): reconnect accordingly. */
    fun onLinkChanged() {
        scope.launch { refreshStatus() }
        closeSocket()
        if (foreground && engine.isLinked) {
            openSocket()
            requestSync(0)
        }
    }

    /**
     * A sync pass as soon as possible — or after [delayMs], merging with any request still waiting.
     * A pass already running is never cancelled (its own writes count as local changes); it runs
     * once more when it ends instead.
     */
    fun requestSync(delayMs: Long = 0) {
        if (!engine.isLinked) return
        synchronized(this) {
            scheduled?.cancel()
            scheduled =
                scope.launch {
                    delay(delayMs)
                    // Its own job, so that cancelling the next schedule can't reach it.
                    scope.launch { runPasses() }
                }
        }
    }

    /**
     * One pass now, waited for — a last push before unlinking. Best effort: a failure is only
     * logged, since nothing must stop the device leaving.
     */
    suspend fun flush() {
        try {
            engine.syncNow(listener)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Last sync pass failed: ${e.message}")
        }
    }

    private suspend fun runPasses() {
        if (!passRunning.compareAndSet(false, true)) {
            passAgain = true
            return
        }
        try {
            do {
                passAgain = false
                runSync()
            } while (passAgain)
        } finally {
            passRunning.set(false)
        }
    }

    private suspend fun runSync() {
        refreshStatus(syncing = true)
        try {
            val outcome = engine.syncNow(listener) ?: return
            retryDelayMs = INITIAL_RETRY_MS
            if (outcome.pulled + outcome.pushed >
                0
            ) {
                Log.i(TAG, "Synced: pulled ${outcome.pulled}, pushed ${outcome.pushed}, waiting ${outcome.deferred}")
            }
        } catch (e: SyncApiException) {
            Log.w(TAG, "Sync failed: ${e.message}")
            if (e.status == 401) {
                closeSocket()
                return
            }
            retryLater()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Used to only log: no error on the settings screen, no retry — sync silently stopped
            // until the app next came to the foreground. See
            // docs/plans/20261001_rock-solid-stability-resilience-plan.md → F-23.
            Log.e(TAG, "Sync pass crashed", e)
            CrashLog.record("sync pass", e)
            store.lastError = "${e.javaClass.simpleName}: ${e.message}"
            retryLater()
        } finally {
            refreshStatus()
        }
    }

    private fun retryLater() {
        if (foreground) {
            val wait = retryDelayMs
            retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_MS)
            requestSync(wait)
        }
    }

    internal fun onForeground() {
        foreground = true
        if (!engine.isLinked) return
        openSocket()
        requestSync(0)
    }

    internal fun onBackground() {
        foreground = false
        closeSocket()
        requestSync(0) // flush what is pending while the process is still alive
    }

    private fun onLocalChange() {
        if (!foreground || !engine.isLinked) return
        scope.launch {
            val pending =
                SettingsDatabase.getInstance(app).settingsSyncDao().hasPending() ||
                    XtreamDatabase.getInstance(app).syncVersionDao().hasPending()
            if (pending) requestSync(PUSH_DEBOUNCE_MS)
        }
    }

    /**
     * Socket lifecycle runs on several threads (main for foreground/background, OkHttp's for its
     * callbacks, IO for the reconnect delay): every read-modify-write of [socket], [socketJob] and
     * [socketRetryDelayMs] holds this lock, so two sockets can never be open at once.
     */
    private val socketLock = Any()

    /**
     * Reconnect delay, separate from [retryDelayMs]: a server whose HTTP API works but whose
     * WebSocket doesn't (a reverse proxy that doesn't upgrade) used to reconnect every 5 s forever,
     * with a full sync pass each time — the pass succeeded, so the shared delay kept resetting.
     * Doubles per failed attempt; resets only once a socket actually opens. See
     * docs/plans/20261001_rock-solid-stability-resilience-plan.md → F-12.
     */
    private var socketRetryDelayMs = INITIAL_RETRY_MS

    private fun openSocket() {
        val link = store.link ?: return
        synchronized(socketLock) {
            if (socket == null) {
                socket = api.openSocket(link.serverUrl, link.deviceToken, socketListener)
            }
        }
    }

    private val socketListener =
        object : WebSocketListener() {
            override fun onOpen(
                webSocket: WebSocket,
                response: Response,
            ) {
                synchronized(socketLock) {
                    if (socket === webSocket) {
                        socketRetryDelayMs = INITIAL_RETRY_MS
                        socketJob?.cancel()
                        // A text ping, not only OkHttp's protocol pings: the server answers it
                        // without waking up and keeps its time as this device's "last seen".
                        socketJob =
                            scope.launch {
                                while (isActive) {
                                    delay(PING_INTERVAL_MS)
                                    webSocket.send("ping")
                                }
                            }
                    }
                }
            }

            override fun onMessage(
                webSocket: WebSocket,
                text: String,
            ) {
                if (text == "pong") return
                // The server sends its head as soon as a socket opens, so this is also what
                // catches up on anything announced while disconnected.
                val head = SyncWire.parseHead(text) ?: return
                if (head > store.cursor) requestSync(0)
            }

            override fun onClosed(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) = onSocketGone(webSocket, code)

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?,
            ) {
                Log.w(TAG, "Sync socket failed: ${t.message}")
                onSocketGone(webSocket, response?.code ?: 0)
            }
        }

    private fun onSocketGone(
        webSocket: WebSocket,
        code: Int,
    ) {
        val wait =
            synchronized(socketLock) {
                if (socket !== webSocket) return
                socket = null
                socketJob?.cancel()
                socketJob = null
                socketRetryDelayMs.also { socketRetryDelayMs = (it * 2).coerceAtMost(MAX_RETRY_MS) }
            }
        // Revoked (4001) or refused (401): don't hammer the server.
        if (!foreground || code == REVOKED || code == 401) return
        scope.launch {
            delay(wait)
            if (foreground) openSocket()
        }
    }

    private fun closeSocket() {
        synchronized(socketLock) {
            socketJob?.cancel()
            socketJob = null
            socket?.close(1000, "background")
            socket = null
            socketRetryDelayMs = INITIAL_RETRY_MS
        }
    }

    companion object {
        private const val TAG = "SyncManager"
        private const val PUSH_DEBOUNCE_MS = 3_000L
        private const val PING_INTERVAL_MS = 30_000L
        private const val INITIAL_RETRY_MS = 5_000L
        private const val MAX_RETRY_MS = 5 * 60_000L
        private const val REVOKED = 4001

        @Volatile private var instance: SyncManager? = null

        fun getInstance(app: Application): SyncManager =
            instance ?: synchronized(this) {
                instance ?: SyncManager(app).also { instance = it }
            }
    }
}
