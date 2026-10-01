package org.njarasoa.fijerena.core.ui.sync

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.room.InvalidationTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
class SyncManager private constructor(
    private val app: Application,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = SyncAccountStore(app)
    private val engine = SyncEngine(app, store = store)
    private val api = SyncApi()
    private val startedActivities = AtomicInteger(0)

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

            override fun onActiveProfileDeleted() {
                scope.launch {
                    // Another device deleted the profile this one is using: move to another, then
                    // sync again so the deletion can land.
                    val active = AppSettings(app).activeProfileId
                    val other = SettingsDatabase.getInstance(app).profileDao().getAll().firstOrNull { it.id != active } ?: return@launch
                    Log.i(TAG, "Active profile deleted on another device; switching to ${other.id}")
                    AppContainer.getInstance(app).switchProfile(other.id)
                    requestSync(0)
                }
            }
        }

    fun start() {
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
        try {
            val outcome = engine.syncNow(listener) ?: return
            retryDelayMs = INITIAL_RETRY_MS
            if (outcome.pulled + outcome.pushed > 0) Log.i(TAG, "Synced: pulled ${outcome.pulled}, pushed ${outcome.pushed}, waiting ${outcome.deferred}")
        } catch (e: SyncApiException) {
            Log.w(TAG, "Sync failed: ${e.message}")
            if (e.status == 401) {
                closeSocket()
                return
            }
            if (foreground) {
                val wait = retryDelayMs
                retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_MS)
                requestSync(wait)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Sync pass crashed", e)
        }
    }

    private fun onForeground() {
        foreground = true
        if (!engine.isLinked) return
        openSocket()
        requestSync(0)
    }

    private fun onBackground() {
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

    private fun openSocket() {
        val link = store.link ?: return
        if (socket != null) return
        socket =
            api.openSocket(
                link.serverUrl,
                link.deviceToken,
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        socketJob =
                            scope.launch {
                                while (isActive) {
                                    delay(PING_INTERVAL_MS)
                                    webSocket.send("ping")
                                }
                            }
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        if (text == "pong") return
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
                },
            )
    }

    private fun onSocketGone(
        webSocket: WebSocket,
        code: Int,
    ) {
        if (socket !== webSocket) return
        socket = null
        socketJob?.cancel()
        // Revoked (4001) or refused (401): don't hammer the server.
        if (!foreground || code == REVOKED || code == 401) return
        scope.launch {
            delay(retryDelayMs)
            if (foreground && socket == null) {
                openSocket()
                requestSync(0) // anything announced while disconnected
            }
        }
    }

    private fun closeSocket() {
        socketJob?.cancel()
        socket?.close(1000, "background")
        socket = null
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
