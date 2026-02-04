package org.njarasoa.fijerena.core.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository

sealed interface ProviderUiState {
    data object Loading : ProviderUiState
    data object NoProviders : ProviderUiState
    data class SingleProvider(val provider: ProviderEntity) : ProviderUiState
    data class MultipleProviders(val providers: List<ProviderEntity>) : ProviderUiState
    data class Error(val message: String) : ProviderUiState
}

class ProviderViewModel(
    private val providerRepository: ProviderRepository,
    private val accountManager: AccountManager,
    private val appSettings: AppSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProviderUiState>(ProviderUiState.Loading)
    val uiState: StateFlow<ProviderUiState> = _uiState.asStateFlow()

    private val _activeProvider = MutableStateFlow<ProviderEntity?>(null)
    val activeProvider: StateFlow<ProviderEntity?> = _activeProvider.asStateFlow()

    init {
        viewModelScope.launch {
            migrateIfNeeded()
            loadProviders()
        }
    }

    fun loadProviders() {
        viewModelScope.launch {
            try {
                val providers = providerRepository.getAllProvidersList()
                _activeProvider.value = providerRepository.getActiveProvider()

                _uiState.value = when {
                    providers.isEmpty() -> ProviderUiState.NoProviders
                    providers.size == 1 -> ProviderUiState.SingleProvider(providers.first())
                    else -> ProviderUiState.MultipleProviders(providers)
                }
            } catch (e: Exception) {
                _uiState.value = ProviderUiState.Error(e.message ?: "Failed to load providers")
            }
        }
    }

    /**
     * One-time migration from single-provider AccountManager to Room.
     * Only runs if no providers exist in Room but legacy credentials are stored.
     */
    suspend fun migrateIfNeeded() {
        val count = providerRepository.getProviderCount()
        if (count > 0) return // Already migrated

        val legacyCreds = accountManager.exportForMigration() ?: return
        val (url, username, password) = legacyCreds

        val name = appSettings.providerName
        providerRepository.addProvider(name, url, username, password)

        // Reload state after migration
        loadProviders()
    }

    fun addProvider(
        name: String,
        url: String,
        username: String,
        password: String,
        type: String = "XTREAM",
        config: String = "",
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                providerRepository.addProvider(name, url, username, password, type, config)
                loadProviders()
                onComplete()
            } catch (e: Exception) {
                _uiState.value = ProviderUiState.Error(e.message ?: "Failed to add provider")
            }
        }
    }

    fun selectProvider(id: Long) {
        viewModelScope.launch {
            try {
                providerRepository.setActiveProvider(id)
                _activeProvider.value = providerRepository.getProviderById(id)
                loadProviders()
            } catch (e: Exception) {
                _uiState.value = ProviderUiState.Error(e.message ?: "Failed to select provider")
            }
        }
    }

    fun deleteProvider(id: Long) {
        viewModelScope.launch {
            try {
                providerRepository.deleteProvider(id)
                // If we deleted the active provider, activate the first remaining one
                val remaining = providerRepository.getAllProvidersList()
                if (remaining.isNotEmpty() && remaining.none { it.isActive }) {
                    providerRepository.setActiveProvider(remaining.first().id)
                }
                loadProviders()
            } catch (e: Exception) {
                _uiState.value = ProviderUiState.Error(e.message ?: "Failed to delete provider")
            }
        }
    }

    fun updateProvider(
        id: Long,
        name: String,
        url: String,
        username: String,
        password: String,
        type: String? = null,
        config: String? = null,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                providerRepository.updateProvider(id, name, url, username, password, type, config)
                loadProviders()
                onComplete()
            } catch (e: Exception) {
                _uiState.value = ProviderUiState.Error(e.message ?: "Failed to update provider")
            }
        }
    }

    fun getPassword(providerId: Long): String? {
        return providerRepository.getPassword(providerId)
    }
}
