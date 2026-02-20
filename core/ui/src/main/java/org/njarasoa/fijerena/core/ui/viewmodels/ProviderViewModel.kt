package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.api.XtreamApiService

data class ParsedUrlCredentials(
    val baseUrl: String,
    val username: String?,
    val password: String?
)

/**
 * Parses a URL that may contain username/password query parameters.
 * Extracts credentials from recognized parameter names and returns
 * the base URL stripped of query parameters (and known API paths like .php endpoints).
 * Returns null if no credentials were found in the URL.
 */
fun parseUrlCredentials(input: String): ParsedUrlCredentials? {
    if ('?' !in input) return null
    try {
        val uri = Uri.parse(input)
        if (uri.queryParameterNames.isNullOrEmpty()) return null

        val username = (uri.getQueryParameter("username")
            ?: uri.getQueryParameter("user"))?.takeIf { it.isNotEmpty() }
        val password = (uri.getQueryParameter("password")
            ?: uri.getQueryParameter("pass"))?.takeIf { it.isNotEmpty() }

        if (username == null && password == null) return null

        val builder = uri.buildUpon().clearQuery().fragment(null)

        // Strip known API endpoint paths (e.g., /get.php, /player_api.php)
        val path = uri.path
        if (path != null && path.endsWith(".php")) {
            val lastSlash = path.lastIndexOf('/')
            val strippedPath = if (lastSlash > 0) path.substring(0, lastSlash) else ""
            builder.path(strippedPath)
        }

        val baseUrl = builder.build().toString().trimEnd('/')

        return ParsedUrlCredentials(baseUrl, username, password)
    } catch (_: Exception) {
        return null
    }
}

sealed interface ProviderUiState {
    data object Loading : ProviderUiState
    data object NoProviders : ProviderUiState
    data class SingleProvider(val provider: ProviderEntity) : ProviderUiState
    data class MultipleProviders(val providers: List<ProviderEntity>) : ProviderUiState
    data class Error(val message: String) : ProviderUiState
}

sealed interface SaveState {
    data object Idle : SaveState
    data object Validating : SaveState
    data class ValidationFailed(val errorMessage: String) : SaveState
    data object Saving : SaveState
}

class ProviderViewModel(
    private val providerRepository: ProviderRepository,
    private val accountManager: AccountManager,
    private val appSettings: AppSettings,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProviderUiState>(ProviderUiState.Loading)
    val uiState: StateFlow<ProviderUiState> = _uiState.asStateFlow()

    private val _activeProvider = MutableStateFlow<ProviderEntity?>(null)
    val activeProvider: StateFlow<ProviderEntity?> = _activeProvider.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }

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

    fun validateAndSave(
        id: Long?,
        name: String,
        url: String,
        username: String,
        password: String,
        type: String,
        config: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            if (type == "LOCAL") {
                performSave(id, name, url, username, password, type, config, onComplete)
                return@launch
            }
            _saveState.value = SaveState.Validating
            val result = testConnection(type, url, username, password, config)
            if (result.isSuccess) {
                performSave(id, name, url, username, password, type, config, onComplete)
            } else {
                _saveState.value = SaveState.ValidationFailed(
                    result.exceptionOrNull()?.message ?: "Connection failed"
                )
            }
        }
    }

    fun forceSave(
        id: Long?,
        name: String,
        url: String,
        username: String,
        password: String,
        type: String,
        config: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            performSave(id, name, url, username, password, type, config, onComplete)
        }
    }

    /**
     * Save a Jellyfin provider authenticated via Quick Connect.
     * Stores the access token directly so no password-based re-auth is ever needed.
     */
    fun quickConnectSave(
        name: String,
        url: String,
        username: String,
        token: String,
        userId: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            _saveState.value = SaveState.Saving
            try {
                val id = providerRepository.addProvider(name, url, username, "", "JELLYFIN", "")
                providerRepository.saveJellyfinSession(id, token, userId)
                loadProviders()
                _saveState.value = SaveState.Idle
                onComplete()
            } catch (e: Exception) {
                _saveState.value = SaveState.Idle
                _uiState.value = ProviderUiState.Error(e.message ?: "Failed to save provider")
            }
        }
    }

    private suspend fun performSave(
        id: Long?,
        name: String,
        url: String,
        username: String,
        password: String,
        type: String,
        config: String,
        onComplete: () -> Unit
    ) {
        _saveState.value = SaveState.Saving
        try {
            if (id != null) {
                providerRepository.updateProvider(id, name, url, username, password, type, config)
            } else {
                providerRepository.addProvider(name, url, username, password, type, config)
            }
            loadProviders()
            _saveState.value = SaveState.Idle
            onComplete()
        } catch (e: Exception) {
            _saveState.value = SaveState.Idle
            _uiState.value = ProviderUiState.Error(e.message ?: "Failed to save provider")
        }
    }

    private suspend fun testConnection(
        type: String,
        url: String,
        username: String,
        password: String,
        config: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        when (type) {
            "XTREAM" -> {
                try {
                    val service = XtreamApiService(url, username, password)
                    val response = service.authenticate()
                    if (response.userInfo.auth != 1) {
                        Result.failure(Exception("Invalid credentials"))
                    } else if (response.userInfo.status != "Active") {
                        Result.failure(Exception("Account is not active: ${response.userInfo.status}"))
                    } else {
                        Result.success(Unit)
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            "REMOTE_M3U" -> {
                try {
                    val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 15_000
                    connection.instanceFollowRedirects = true
                    connection.setRequestProperty("Accept-Encoding", "identity")
                    try {
                        val statusCode = connection.responseCode
                        if (statusCode !in 200..299) {
                            Result.failure(Exception("Server returned HTTP $statusCode"))
                        } else {
                            val header = connection.inputStream.bufferedReader().use { reader ->
                                val buf = CharArray(256)
                                val read = reader.read(buf)
                                if (read > 0) String(buf, 0, read) else ""
                            }
                            if (header.trimStart().startsWith("#EXTM3U")) {
                                Result.success(Unit)
                            } else {
                                Result.failure(Exception("Not a valid M3U file: missing #EXTM3U header"))
                            }
                        }
                    } finally {
                        connection.disconnect()
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            "JELLYFIN", "SMB" -> {
                val tempEntity = ProviderEntity(
                    id = 0L,
                    name = "validation",
                    url = url,
                    username = username,
                    type = type,
                    config = config
                )
                try {
                    val provider = MediaProviderFactory.create(tempEntity, context, password)
                    val result = provider.connect()
                    try { provider.disconnect() } catch (_: Exception) {}
                    result
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            else -> Result.success(Unit)
        }
    }
}
