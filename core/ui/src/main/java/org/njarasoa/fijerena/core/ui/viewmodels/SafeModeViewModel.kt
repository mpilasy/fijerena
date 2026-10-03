package org.njarasoa.fijerena.core.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog

/**
 * The safe-mode screen's "Clear caches" (see `SafeMode`): the EPG index, each source's catalogue
 * cache and the poster cache — everything the app downloads again on its own. Sources, profiles,
 * favourites and watch history are never touched. Each step runs even if an earlier one failed,
 * since the broken one may be exactly what safe mode is working around. See
 * docs/plans/20261002_next-level-rock-solid-resilience-plan.md → R-10.
 */
class SafeModeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val context = application

    enum class ClearState { IDLE, CLEARING, DONE, FAILED }

    private val _clearState = MutableStateFlow(ClearState.IDLE)
    val clearState: StateFlow<ClearState> = _clearState.asStateFlow()

    fun clearCaches() {
        if (_clearState.value != ClearState.CLEARING) {
            _clearState.value = ClearState.CLEARING
            viewModelScope.launch {
                val results =
                    withContext(Dispatchers.IO) {
                        listOf(
                            step("EPG index") {
                                EpgIndexer.getInstance(context).clearAll()
                                // Stats and validators of the rows just destroyed; kept, they let
                                // the next refresh skip on a 304 / hash match against an empty
                                // guide (G-11). Bookkeeping columns only — not a synced change.
                                SettingsDatabase.getInstance(context).epgSourceDao().resetAllIngestionState()
                            },
                            step("catalogue") {
                                val providerRepo = ProviderRepository(context)
                                // Catalogue rows only (XtreamStatsManager.clearCache): favourites
                                // and watch history live in their own tables.
                                providerRepo.getAllProvidersList().forEach { providerRepo.clearAllCacheForProvider(it.id) }
                            },
                            step("images") {
                                val imageLoader = SingletonImageLoader.get(context)
                                imageLoader.memoryCache?.clear()
                                imageLoader.diskCache?.clear()
                            },
                        )
                    }
                _clearState.value = if (results.all { it }) ClearState.DONE else ClearState.FAILED
            }
        }
    }

    /** Runs one clearing step; false, recorded in [CrashLog], if it threw. */
    private suspend fun step(
        name: String,
        block: suspend () -> Unit,
    ): Boolean =
        try {
            block()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("SafeModeViewModel", "Clearing $name failed", e)
            CrashLog.record("safe mode: clear $name", e)
            false
        }
}
