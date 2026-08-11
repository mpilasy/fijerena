package org.njarasoa.fijerena.core.network.xtream

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.XtreamMediaProvider
import org.njarasoa.fijerena.core.network.R
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
 * EpgFileManager.downloadSource(). Previously this block was copy-pasted three times with
 * no retry at all.
 */
object ProviderSyncRunner {
    private const val TAG = "ProviderSyncRunner"
    private const val MAX_ATTEMPTS = 3

    sealed interface Outcome {
        /** Sync completed. */
        object Success : Outcome

        /** Retrying won't help (bad credentials, unsupported provider). Error is user-facing. */
        data class Permanent(val error: String) : Outcome

        /** Network blip — worth another WorkManager-backed retry later. Error is user-facing. */
        data class Transient(val error: String) : Outcome
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
        var attempt = 1
        while (true) {
            try {
                val mediaProvider = MediaProviderFactory.create(provider, context, password)
                if (mediaProvider !is XtreamMediaProvider) return Outcome.Success

                if (!mediaProvider.isConnected()) {
                    mediaProvider.connect()
                }
                if (!mediaProvider.isConnected()) {
                    // No exception thrown — connect() simply refused. Almost always credentials.
                    val devSuffix =
                        if (AppSettings(context).isDevMode) "\n\n[dev] connect() returned not-connected" else ""
                    return Outcome.Permanent(context.getString(R.string.error_unauthorized) + devSuffix)
                }

                mediaProvider.syncAll()

                // Proactively warm the EPG-match cache for the provider just synced.
                val streams =
                    withContext(Dispatchers.IO) {
                        XtreamDatabase
                            .getInstance(context)
                            .streamDao()
                            .getAllStreams(provider.id, XtreamStreamEntity.TYPE_LIVE)
                    }
                EpgChannelMatcher.warmCache(provider.id, streams)

                return Outcome.Success
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
                val message = friendlyErrorMessage(e, context, AppSettings(context).isDevMode)
                return if (transient) Outcome.Transient(message) else Outcome.Permanent(message)
            }
        }
    }

    private fun isTransient(e: Throwable): Boolean =
        when (e) {
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
