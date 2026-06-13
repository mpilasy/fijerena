package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.SettingsExportManager
import org.njarasoa.fijerena.core.network.provider.ProviderRepository

data class SettingsUiState(
    val providerName: String = "No provider",
    val currentUrl: String = "",
    val currentUsername: String = "",
    val activeProviderId: Long? = null,
    val providerType: String = "",
    val subscriptionExpiry: String? = null,
    val subscriptionStatus: String? = null,
    val subscriptionMaxCons: String? = null,
    val subscriptionIsTrial: Boolean = false,
    val themeId: String = "deep_night",
    val isDevMode: Boolean = false,
    val language: String = "en",
    val watchDelaySeconds: Int = AppSettings.DEFAULT_WATCH_DELAY_SECONDS,
    val uiScale: Float = AppSettings.DEFAULT_UI_SCALE,
    val exportImportMessage: String? = null,
    val epgRefreshTrigger: Int = 0,
)

class SettingsViewModel(
    private val context: Context,
    private val appSettings: AppSettings,
    private val providerRepo: ProviderRepository,
    private val exportManager: SettingsExportManager,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            SettingsUiState(
                themeId = appSettings.themeId,
                isDevMode = appSettings.isDevMode,
                language = appSettings.language,
                watchDelaySeconds = appSettings.watchDelaySeconds,
                uiScale = appSettings.uiScale,
            ),
        )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshProviderInfo()
        // Sync EPG indexer state to ensure status card is accurate
        viewModelScope.launch {
            org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
                .getInstance(context)
                .initialize()
        }
    }

    fun refreshProviderInfo() {
        viewModelScope.launch {
            val activeProvider = providerRepo.getActiveProvider()

            // Base provider info
            var newState =
                _uiState.value.copy(
                    providerName = activeProvider?.name ?: "No provider",
                    currentUrl = activeProvider?.url ?: "",
                    currentUsername = activeProvider?.username ?: "",
                    activeProviderId = activeProvider?.id,
                    providerType = activeProvider?.type ?: "",
                    // Reset subscription info before re-fetching
                    subscriptionExpiry = null,
                    subscriptionStatus = null,
                    subscriptionMaxCons = null,
                    subscriptionIsTrial = false,
                )

            // Xtream-specific subscription info
            if (activeProvider?.type == "XTREAM") {
                val accountManager = AccountManager(context.applicationContext)
                accountManager.getAuthResponse()?.userInfo?.let { info ->
                    newState =
                        newState.copy(
                            subscriptionStatus = info.status,
                            subscriptionMaxCons = info.maxConnections,
                            subscriptionIsTrial = info.isTrial == "1",
                            subscriptionExpiry = formatExpiryDate(info.expDate),
                        )
                }
            }

            _uiState.value = newState
        }
    }

    fun updateTheme(themeId: String) {
        appSettings.themeId = themeId
        _uiState.value = _uiState.value.copy(themeId = themeId)
    }

    fun updateDevMode(enabled: Boolean) {
        appSettings.isDevMode = enabled
        _uiState.value = _uiState.value.copy(isDevMode = enabled)
    }

    fun updateLanguage(language: String) {
        org.njarasoa.fijerena.core.ui.utils.LocaleManager.updateLocale(context, language)
        _uiState.value = _uiState.value.copy(language = language)
    }

    fun updateWatchDelay(seconds: Int) {
        appSettings.watchDelaySeconds = seconds
        _uiState.value = _uiState.value.copy(watchDelaySeconds = seconds)
    }

    fun updateUiScale(scale: Float) {
        // uiScale is typically handled via a global callback in the screens,
        // but we keep it here for state consistency.
        _uiState.value = _uiState.value.copy(uiScale = scale)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(exportImportMessage = null)
    }

    fun doImport(
        parsed: SettingsExportManager.ParsedImport,
        resolution: SettingsExportManager.ConflictResolution,
        options: SettingsExportManager.ImportOptions,
    ) {
        viewModelScope.launch {
            val result = exportManager.importFromParsed(parsed, resolution, options)

            val message = result.toSummary()

            if (result.isSuccess) {
                // Refresh global settings from AppSettings (they were updated inside exportManager.importFromJson)
                if (options.importGlobalSettings) {
                    _uiState.value =
                        _uiState.value.copy(
                            themeId = appSettings.themeId,
                            isDevMode = appSettings.isDevMode,
                            language = appSettings.language,
                            watchDelaySeconds = appSettings.watchDelaySeconds,
                            uiScale = appSettings.uiScale,
                        )
                }

                // Trigger UI refresh for providers and EPG
                refreshProviderInfo()
                _uiState.value =
                    _uiState.value.copy(
                        exportImportMessage = message,
                        epgRefreshTrigger = _uiState.value.epgRefreshTrigger + 1,
                    )
            } else {
                _uiState.value = _uiState.value.copy(exportImportMessage = message)
            }
        }
    }

    fun setExportImportMessage(message: String?) {
        _uiState.value = _uiState.value.copy(exportImportMessage = message)
    }

    private fun formatExpiryDate(expDate: String?): String? =
        when {
            expDate.isNullOrEmpty() -> null
            expDate.equals("Unlimited", ignoreCase = true) -> "Unlimited"
            else -> {
                val epoch = expDate.toLongOrNull()
                if (epoch != null) {
                    try {
                        val date =
                            java.time.Instant
                                .ofEpochSecond(epoch)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                        date.format(
                            java.time.format.DateTimeFormatter
                                .ofPattern("MMM d, yyyy"),
                        )
                    } catch (_: Exception) {
                        expDate
                    }
                } else {
                    expDate
                }
            }
        }
}
