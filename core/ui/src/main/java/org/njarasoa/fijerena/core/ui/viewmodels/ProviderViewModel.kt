package org.njarasoa.fijerena.core.ui.viewmodels
import android.content.Context
import androidx.core.net.toUri
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
import org.njarasoa.fijerena.core.network.XtreamMediaProvider
import org.njarasoa.fijerena.core.network.friendlyErrorMessage
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.player.api.XtreamApiService
import org.njarasoa.fijerena.core.ui.di.AppContainer

data class ParsedUrlCredentials(
    val baseUrl: String,
    val username: String?,
    val password: String?,
    val streamOutputFormat: String? = null,
    val playlistType: String? = null,
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
        val uri = input.toUri()
        if (uri.queryParameterNames.isNullOrEmpty()) return null

        val username =
            (
                uri.getQueryParameter("username")
                    ?: uri.getQueryParameter("user")
            )?.takeIf { it.isNotEmpty() }
        val password =
            (
                uri.getQueryParameter("password")
                    ?: uri.getQueryParameter("pass")
            )?.takeIf { it.isNotEmpty() }

        if (username == null && password == null) return null

        val streamOutputFormat = uri.getQueryParameter("output")?.takeIf { it.isNotEmpty() }
        val playlistType = uri.getQueryParameter("type")?.takeIf { it.isNotEmpty() }

        val builder = uri.buildUpon().clearQuery().fragment(null)

        // Strip known API endpoint paths (e.g., /get.php, /player_api.php)
        val path = uri.path
        if (path != null && path.endsWith(".php")) {
            val lastSlash = path.lastIndexOf('/')
            val strippedPath = if (lastSlash > 0) path.substring(0, lastSlash) else ""
            builder.path(strippedPath)
        }

        val baseUrl = builder.build().toString().trimEnd('/')

        return ParsedUrlCredentials(baseUrl, username, password, streamOutputFormat, playlistType)
    } catch (_: Exception) {
        return null
    }
}

sealed interface ProviderUiState {
    data object Loading : ProviderUiState

    data object NoProviders : ProviderUiState

    data class SingleProvider(
        val provider: ProviderEntity,
    ) : ProviderUiState

    data class MultipleProviders(
        val providers: List<ProviderEntity>,
    ) : ProviderUiState

    data class Error(
        val message: String,
    ) : ProviderUiState
}

sealed interface SaveState {
    data object Idle : SaveState

    data object Validating : SaveState

    data class ValidationFailed(
        val errorMessage: String,
    ) : SaveState

    data object Saving : SaveState
}

sealed interface SyncState {
    data object Idle : SyncState

    data object Syncing : SyncState

    data object Success : SyncState

    data class Error(
        val message: String,
    ) : SyncState
}

class ProviderViewModel(
    private val providerRepository: ProviderRepository,
    private val accountManager: AccountManager,
    private val appSettings: AppSettings,
    private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ProviderUiState>(ProviderUiState.Loading)
    val uiState: StateFlow<ProviderUiState> = _uiState.asStateFlow()

    private val _activeProvider = MutableStateFlow<ProviderEntity?>(null)
    val activeProvider: StateFlow<ProviderEntity?> = _activeProvider.asStateFlow()

    private val _providers = MutableStateFlow<List<ProviderEntity>>(emptyList())
    val providers: StateFlow<List<ProviderEntity>> = _providers.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private var syncingProviderId: Long? = null

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
            providerRepository.getAllProviders().collect { providersList ->
                _providers.value = providersList
                _activeProvider.value = providersList.find { it.isActive }
                
                // If we are currently in "Syncing" state, check if OUR provider just finished syncing
                if (_syncState.value == SyncState.Syncing && syncingProviderId != null) {
                    val syncedProvider = providersList.find { it.id == syncingProviderId }
                    if (syncedProvider != null) {
                        // Check if sync completed very recently (within last 30 seconds)
                        val now = System.currentTimeMillis()
                        val completionTime = syncedProvider.lastSyncedAtMs
                        if (completionTime > 0 && (now - completionTime) < 30_000) {
                            if (syncedProvider.lastSyncError != null) {
                                _syncState.value = SyncState.Error(syncedProvider.lastSyncError!!)
                            } else {
                                _syncState.value = SyncState.Success
                            }
                            // Reset tracking once we've signaled success
                            syncingProviderId = null
                        }
                    }
                }

                _uiState.value =
                    when {
                        providersList.isEmpty() -> ProviderUiState.NoProviders
                        providersList.size == 1 -> ProviderUiState.SingleProvider(providersList.first())
                        else -> ProviderUiState.MultipleProviders(providersList)
                    }
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
        onComplete: () -> Unit = {},
    ) {
        viewModelScope.launch {
            try {
                providerRepository.addProvider(name, url, username, password, type, config)
                loadProviders()
                onComplete()
            } catch (e: Exception) {
                _uiState.value = ProviderUiState.Error(e.message ?: context.getString(R.string.provider_error_add_failed))
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
                _uiState.value = ProviderUiState.Error(e.message ?: context.getString(R.string.provider_error_select_failed))
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
                _uiState.value = ProviderUiState.Error(e.message ?: context.getString(R.string.provider_error_delete_failed))
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
        onComplete: () -> Unit = {},
    ) {
        viewModelScope.launch {
            try {
                providerRepository.updateProvider(id, name, url, username, password, type, config)
                loadProviders()
                onComplete()
            } catch (e: Exception) {
                _uiState.value = ProviderUiState.Error(e.message ?: context.getString(R.string.provider_error_update_failed))
            }
        }
    }

    fun getPassword(providerId: Long): String? = providerRepository.getPassword(providerId)

    fun validateAndSave(
        id: Long?,
        name: String,
        url: String,
        username: String,
        password: String,
        type: String,
        config: String,
        onComplete: () -> Unit,
        initialSettings: ProviderSettings = ProviderSettings.DEFAULT,
    ) {
        viewModelScope.launch {
            if (type == "LOCAL") {
                performSave(id, name, url, username, password, type, config, onComplete, initialSettings)
                return@launch
            }
            _saveState.value = SaveState.Validating
            val result = testConnection(type, url, username, password, config)
            if (result.isSuccess) {
                performSave(id, name, url, username, password, type, config, onComplete, initialSettings)
            } else {
                _saveState.value =
                    SaveState.ValidationFailed(
                        result.exceptionOrNull()?.message ?: context.getString(R.string.provider_error_connection_failed),
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
        onComplete: () -> Unit,
        initialSettings: ProviderSettings = ProviderSettings.DEFAULT,
    ) {
        viewModelScope.launch {
            performSave(id, name, url, username, password, type, config, onComplete, initialSettings)
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
        onComplete: () -> Unit,
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
                _uiState.value = ProviderUiState.Error(e.message ?: context.getString(R.string.provider_error_save_failed))
            }
        }
    }

    fun syncProvider(providerId: Long) {
        // Track which provider we are waiting for
        syncingProviderId = providerId
        // Delegate to ProviderSyncManager so it persists outside the ViewModel scope
        org.njarasoa.fijerena.core.network.xtream.ProviderSyncManager.getInstance(context).startManualSync(providerId)
        
        // UI feedback - it will transition back to Success/Error when the stats update in DB
        _syncState.value = SyncState.Syncing
    }

    private suspend fun performSave(
        id: Long?,
        name: String,
        url: String,
        username: String,
        password: String,
        type: String,
        config: String,
        onComplete: () -> Unit,
        initialSettings: ProviderSettings = ProviderSettings.DEFAULT,
    ) {
        _saveState.value = SaveState.Saving
        try {
            if (id != null) {
                providerRepository.updateProvider(id, name, url, username, password, type, config)
                // Credentials may have changed — evict the cached MediaRepository so the
                // next getMediaRepository() call rebuilds it instead of reusing one built
                // from the old URL/username/password.
                AppContainer.getInstance(context).evictMediaRepository(id)
            } else {
                providerRepository.addProvider(name, url, username, password, type, config, initialSettings)
            }
            loadProviders()
            _saveState.value = SaveState.Idle
            onComplete()
        } catch (e: Exception) {
            _saveState.value = SaveState.Idle
            _uiState.value = ProviderUiState.Error(e.message ?: context.getString(R.string.provider_error_save_failed))
        }
    }

    private suspend fun testConnection(
        type: String,
        url: String,
        username: String,
        password: String,
        config: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            when (type) {
                "XTREAM" -> {
                    try {
                        val service = XtreamApiService(url, username, password)
                        val response = service.authenticate()
                        if (response.userInfo.auth != 1) {
                            Result.failure(Exception(context.getString(R.string.provider_error_invalid_credentials)))
                        } else if (response.userInfo.status != "Active") {
                            Result.failure(Exception(context.getString(R.string.provider_error_account_inactive_format, response.userInfo.status)))
                        } else {
                            Result.success(Unit)
                        }
                    } catch (e: Exception) {
                        Result.failure(Exception(friendlyErrorMessage(e, context, appSettings.isDevMode), e))
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
                                Result.failure(Exception(context.getString(R.string.provider_error_http_status_format, statusCode)))
                            } else {
                                val header =
                                    connection.inputStream.bufferedReader().use { reader ->
                                        val buf = CharArray(256)
                                        val read = reader.read(buf)
                                        if (read > 0) String(buf, 0, read) else ""
                                    }
                                if (header.trimStart().startsWith("#EXTM3U")) {
                                    Result.success(Unit)
                                } else {
                                    Result.failure(Exception(context.getString(R.string.provider_error_invalid_m3u)))
                                }
                            }
                        } finally {
                            connection.disconnect()
                        }
                    } catch (e: Exception) {
                        Result.failure(Exception(friendlyErrorMessage(e, context, appSettings.isDevMode), e))
                    }
                }
                "JELLYFIN", "SMB" -> {
                    val tempEntity =
                        ProviderEntity(
                            id = 0L,
                            name = "validation",
                            url = url,
                            username = username,
                            type = type,
                            config = config,
                        )
                    try {
                        val provider = MediaProviderFactory.create(tempEntity, context, password)
                        val result = provider.connect()
                        try {
                            provider.disconnect()
                        } catch (e: Exception) {
                            android.util.Log.e("ProviderViewModel", "Error disconnecting provider", e)
                        }
                        result
                    } catch (e: Exception) {
                        Result.failure(Exception(friendlyErrorMessage(e, context, appSettings.isDevMode), e))
                    }
                }
                else -> Result.success(Unit)
            }
        }
}
