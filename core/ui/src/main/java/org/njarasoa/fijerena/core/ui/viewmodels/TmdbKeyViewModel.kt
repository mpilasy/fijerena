package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.network.tmdb.TmdbKeyCheck
import org.njarasoa.fijerena.core.network.tmdb.TmdbKeyState
import org.njarasoa.fijerena.core.network.tmdb.TmdbKeyStore
import org.njarasoa.fijerena.core.ui.utils.launchGuarded

/** Settings' TMDB key: what is saved, and the result of the last Test. */
data class TmdbKeyUiState(
    val state: TmdbKeyState = TmdbKeyState.None,
    val savedKey: String = "",
    val testing: Boolean = false,
    val lastCheck: TmdbKeyCheck? = null,
)

/**
 * The TMDB key row and its editor on TV and phone (D4,
 * docs/plans/20261011_public-release-fdroid-readiness-plan.md). A key TMDB has refused shows here,
 * and only here, as [TmdbKeyState.Rejected].
 */
class TmdbKeyViewModel(
    context: Context,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val store = TmdbKeyStore(appContext)
    private val _uiState = MutableStateFlow(TmdbKeyUiState())
    val uiState: StateFlow<TmdbKeyUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Re-reads the store: a 401 elsewhere, or a key received from another device, changes it. */
    fun refresh() {
        viewModelScope.launchGuarded("TmdbKeyViewModel.refresh", Dispatchers.IO) {
            val state = store.state
            _uiState.update { it.copy(state = state, savedKey = store.key) }
        }
    }

    fun test(candidate: String) {
        _uiState.update { it.copy(testing = true, lastCheck = null) }
        viewModelScope.launchGuarded(
            "TmdbKeyViewModel.test",
            Dispatchers.IO,
            onError = { _uiState.update { it.copy(testing = false, lastCheck = TmdbKeyCheck.UNREACHABLE) } },
        ) {
            val check = TmdbApiService.getInstance(appContext).checkKey(candidate)
            _uiState.update { it.copy(testing = false, lastCheck = check) }
        }
    }

    /** Saves [key]; a blank one removes the key and turns TMDB off. */
    fun save(key: String) {
        viewModelScope.launchGuarded("TmdbKeyViewModel.save", Dispatchers.IO) {
            store.save(key)
            _uiState.update { it.copy(state = store.state, savedKey = store.key, lastCheck = null) }
        }
    }

    fun clearCheck() = _uiState.update { it.copy(lastCheck = null) }
}
