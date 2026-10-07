package org.njarasoa.fijerena.core.network.xtream

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.R
import org.njarasoa.fijerena.core.network.XtreamMediaProvider
import org.njarasoa.fijerena.core.network.friendlyErrorMessage
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.xmltv.EpgChannelMatcher
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Single per-provider Xtream sync shared by [ProviderSyncManager] (manual + periodic
 * foreground refresh) and [XtreamSyncWorker] (WorkManager). Adds transient-vs-permanent
 * classification and in-run retry with exponential backoff, mirroring
 * EpgFileManager.downloadSource().
 */
object ProviderSyncRunner {
    private const val TAG = "ProviderSyncRunner"
    private const val MAX_ATTEMPTS = 3

    /** Syncs running in this process per source id; a count, as the worker and a manual sync can overlap. */
    private val runningCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())

    /**
     * Ids of the sources whose catalogue sync is running now, whichever caller started it (manual,
     * TV's in-process refresh, [XtreamSyncWorker]). Home's source pill shows "Updating…" from it
     * (docs/plans/20261007_tv-home-overhaul-plan.md → Phase 1).
     */
    val running: Flow<Set<Long>> = runningCounts.map { it.keys }.distinctUntilChanged()

    sealed interface Outcome {
        /**
         * Sync completed. [delta] is the row-level change count for an Xtream provider, null for
         * any other provider type (they have no equivalent diff).
         */
        data class Success(
            val delta: SyncDelta? = null,
        ) : Outcome

        /** Retrying won't help (bad credentials, unsupported provider). Error is user-facing. */
        data class Permanent(
            val error: String,
        ) : Outcome

        /** Network blip — worth another WorkManager-backed retry later. Error is user-facing. */
        data class Transient(
            val error: String,
        ) : Outcome
    }

    /**
     * Sync one provider, retrying transient network failures in-run before giving up.
     * The returned error strings are already mapped to friendly text (plus raw detail when
     * Developer Mode is on), ready to persist via `updateSyncStats` / show in the UI.
     */
    suspend fun syncProvider(
        context: Context,
        provider: ProviderEntity,
        password: String,
    ): Outcome {
        runningCounts.update { it + (provider.id to (it[provider.id] ?: 0) + 1) }
        try {
            return syncWithRetries(context, provider, password)
        } finally {
            runningCounts.update { counts ->
                val left = (counts[provider.id] ?: 1) - 1
                if (left > 0) counts + (provider.id to left) else counts - provider.id
            }
        }
    }

    private suspend fun syncWithRetries(
        context: Context,
        provider: ProviderEntity,
        password: String,
    ): Outcome {
        var attempt = 1
        while (true) {
            try {
                // A Jellyfin provider opens its encrypted session prefs here (Keystore) — keep it off Main,
                // which is where ProviderSyncManager's scope runs this.
                val mediaProvider = withContext(Dispatchers.IO) { MediaProviderFactory.create(provider, context, password) }
                if (mediaProvider !is XtreamMediaProvider) return Outcome.Success()

                if (!mediaProvider.isConnected()) {
                    // A failed connect says why: a network failure (server down, no connection)
                    // is retried and reported as such below, not taken for a refused login.
                    mediaProvider.connect().exceptionOrNull()?.let { failure ->
                        throw (failure.cause as? Exception) ?: (failure as? Exception) ?: Exception(failure)
                    }
                }
                if (!mediaProvider.isConnected()) {
                    // connect() succeeded yet no session: nothing better to say than the login.
                    val devSuffix =
                        if (AppSettings(context).isDevMode) "\n\n[dev] connect() returned not-connected" else ""
                    return Outcome.Permanent(context.getString(R.string.error_unauthorized) + devSuffix)
                }

                val delta = mediaProvider.syncAll()

                // Proactively warm the EPG-match cache for the provider just synced — with every live
                // stream, as EpgBrowserViewModel builds it: getAllStreams leaves out hidden categories,
                // and a matcher without the real channel matched a separator row by name instead
                // (docs/plans/20261007_guide-watch-wrong-channel-plan.md, fix 2).
                val streams =
                    withContext(Dispatchers.IO) {
                        XtreamDatabase
                            .getInstance(context)
                            .streamDao()
                            .getAllStreamsIncludingExcluded(provider.id, XtreamStreamEntity.TYPE_LIVE)
                    }
                EpgChannelMatcher.warmCache(provider.id, streams)

                return Outcome.Success(delta)
            } catch (e: CancellationException) {
                // Worker stopped or timed out — not a sync failure. Recording it as one would
                // flag the provider as permanently broken in providers.db for what's really just
                // a pause.
                throw e
            } catch (e: Exception) {
                val transient = isTransient(e)
                if (transient && attempt < MAX_ATTEMPTS) {
                    val backoff = (5000L * (1 shl (attempt - 1))).coerceAtMost(60000L)
                    Log.w(TAG, "Sync failed for ${provider.name} (attempt $attempt), retrying in ${backoff}ms", e)
                    delay(backoff)
                    attempt++
                    continue
                }
                Log.e(TAG, "Sync failed for ${provider.name} (attempt $attempt, transient=$transient)", e)
                val message = failureMessage(context, e)
                return if (transient) Outcome.Transient(message) else Outcome.Permanent(message)
            }
        }
    }

    /** A catalogue failure says so up front: the reason alone ("Network error") could read as a connect problem. */
    private fun failureMessage(
        context: Context,
        e: Exception,
    ): String {
        val devMode = AppSettings(context).isDevMode
        return if (e is CatalogSyncException) {
            // Describe the failure that decided the classification, so the text and the retry agree.
            val cause = e.failures.firstOrNull { isTransient(it) } ?: e.failures.first()
            context.getString(R.string.error_catalog_sync_failed, friendlyErrorMessage(cause, context, devMode))
        } else {
            friendlyErrorMessage(e, context, devMode)
        }
    }

    /** A catalogue run with any transient task failure is transient: a retry can fix that task. */
    private fun isTransient(e: Throwable): Boolean =
        when (e) {
            is CatalogSyncException -> e.failures.any { isTransient(it) }
            is UnknownHostException, is SocketTimeoutException, is IOException -> true
            else -> false
        }
}

/** The user-facing error to persist, or null on success. */
fun ProviderSyncRunner.Outcome.errorOrNull(): String? =
    when (this) {
        is ProviderSyncRunner.Outcome.Success -> null
        is ProviderSyncRunner.Outcome.Permanent -> error
        is ProviderSyncRunner.Outcome.Transient -> error
    }
