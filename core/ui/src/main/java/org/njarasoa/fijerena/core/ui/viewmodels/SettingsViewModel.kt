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
import org.njarasoa.fijerena.core.network.friendlyErrorMessage
import org.njarasoa.fijerena.core.network.profile.ProfileRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.utils.DeveloperModeTaps
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.utils.launchGuarded

data class SettingsUiState(
    /**
     * The profile in use's sources (id to name), in its order — Manage sources' value, and what the
     * phone's Edit Source row chooses from. There is no source in use (multi-source plan, Phase 6).
     */
    val profileSources: List<Pair<Long, String>> = emptyList(),
    /**
     * A source on the device has live channels, so it can have guide sources: the Guide Sources
     * row shows (every source on the device, not only the profile's: the one guide sources page plan).
     */
    val hasGuideSources: Boolean = false,
    /** Each of [profileSources] with its URL and subscription, in the same order. */
    val themeId: String = "deep_night",
    val uiStyleId: String = "modern",
    val isDevMode: Boolean = false,
    /** A profile is Expert, so the 7 taps on the version do nothing (PR-3). Yes until the list has loaded. */
    val expertExists: Boolean = true,
    /**
     * The profile in use is a child's: its Settings show only its own page and About, not the
     * groups that change the whole device (Playback, Display).
     */
    val isChildProfile: Boolean = false,
    val language: String = "en",
    val watchDelaySeconds: Int = AppSettings.DEFAULT_WATCH_DELAY_SECONDS,
    val showClock: Boolean = false,
    val showTmdbCharts: Boolean = true,
    val recentRowSize: Int = AppSettings.DEFAULT_WATCH_HISTORY_SIZE,
    /** TV's Text & grid size as picked; null: Auto. */
    val uiScale: Float? = null,
    val exportImportMessage: String? = null,
    val isPruningDatabase: Boolean = false,
    val databaseMaintenanceMessage: String? = null,
    val lastShrinkAtMs: Long = 0L,
    val lastShrinkDurationMs: Long = 0L,
    val lastShrinkRowsRemoved: Long = 0L,
    val lastShrinkBytesReclaimed: Long = 0L,
)

class SettingsViewModel(
    private val context: Context,
    private val appSettings: AppSettings,
    private val providerRepo: ProviderRepository,
    private val exportManager: SettingsExportManager,
    private val profileRepo: ProfileRepository,
) : ViewModel() {
    /** Every profile's id, null until the list has loaded. */
    private var profileIds: List<String>? = null

    private val _uiState =
        MutableStateFlow(
            SettingsUiState(
                themeId = appSettings.themeId,
                uiStyleId = appSettings.uiStyleId,
                isDevMode = appSettings.isDevMode,
                isChildProfile = appSettings.isChildProfile(appSettings.activeProfileId),
                language = appSettings.language,
                watchDelaySeconds = appSettings.watchDelaySeconds,
                showClock = appSettings.showClock,
                showTmdbCharts = appSettings.showTmdbCharts,
                recentRowSize = appSettings.recentRowSize,
                uiScale = appSettings.uiScaleChoice,
                lastShrinkAtMs = appSettings.lastShrinkAtMs,
                lastShrinkDurationMs = appSettings.lastShrinkDurationMs,
                lastShrinkRowsRemoved = appSettings.lastShrinkRowsRemoved,
                lastShrinkBytesReclaimed = appSettings.lastShrinkBytesReclaimed,
            ),
        )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Read again whenever the profile's sources change (one turned on or off on its page, added
        // or deleted), so Manage sources and Edit Source follow without reopening Settings.
        viewModelScope.launchGuarded("SettingsViewModel.followProfileSources") {
            providerRepo.profileSourceSet(appSettings.activeProfileId).collect { refreshProviderInfo() }
        }
        // Expert mode as it changes, not only on resume: on a new install the first profile gets it
        // in the background at start (ProfileRepository.ensureAnExpert), maybe after Settings opened.
        viewModelScope.launchGuarded("SettingsViewModel.followDevMode") {
            appSettings.observeDevMode().collect { refreshDevMode() }
        }
        viewModelScope.launchGuarded("SettingsViewModel.followProfiles") {
            profileRepo.observeProfiles().collect { rows ->
                profileIds = rows.map { it.id }
                refreshDevMode()
            }
        }
        // The profile's colour theme and Look and Feel, also when another device changes them.
        viewModelScope.launchGuarded("SettingsViewModel.followLook") {
            appSettings.observeLook().collect { (themeId, uiStyleId) ->
                _uiState.value = _uiState.value.copy(themeId = themeId, uiStyleId = uiStyleId)
            }
        }
        // Sync EPG indexer state to ensure status card is accurate
        viewModelScope.launch {
            org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
                .getInstance(context)
                .initialize()
        }
    }

    fun refreshProviderInfo() {
        viewModelScope.launchGuarded("SettingsViewModel.refreshProviderInfo") {
            // One read for both: a second one delays the state, and focus can land before it.
            val all = providerRepo.getAllProvidersList()
            val entities = ProviderRepository.resolveProfileSources(all, appSettings.profileSourceKeys(appSettings.activeProfileId))
            val hasGuideSources =
                all.any {
                    org.njarasoa.fijerena.core.network.MediaProviderFactory
                        .hasLiveTv(it)
                }
            _uiState.value =
                _uiState.value.copy(
                    profileSources = entities.map { it.id to it.name },
                    hasGuideSources = hasGuideSources,
                )
        }
    }

    /** Re-reads the active profile's developer mode and child flag, which its profile page may have changed. */
    fun refreshDevMode() {
        _uiState.value =
            _uiState.value.copy(
                isDevMode = appSettings.isDevMode,
                isChildProfile = appSettings.isChildProfile(appSettings.activeProfileId),
                expertExists = expertExists(),
            )
    }

    private fun expertExists(): Boolean = DeveloperModeTaps.expertExists(profileIds, appSettings::devMode)

    /**
     * The 7th tap on the version row: Expert mode on for the profile in use, only while no profile
     * is Expert (the profile rules plan, PR-3). Returns whether it is on now.
     */
    fun enableDevMode(): Boolean {
        // A child profile is never Expert (docs/plans/20261019_tv-household-ux-plan.md).
        val allowed = !appSettings.isChildProfile(appSettings.activeProfileId) && !expertExists()
        if (allowed) appSettings.isDevMode = true
        refreshDevMode()
        return allowed
    }

    // Language, the Recent delay and Recent's size change the whole device: an Expert's only (the
    // profile rules plan). The screens show them to an Expert only, behind its PIN.

    fun updateLanguage(language: String) {
        if (!appSettings.isDevMode) return
        org.njarasoa.fijerena.core.ui.utils.LocaleManager
            .updateLocale(context, language)
        _uiState.value = _uiState.value.copy(language = language)
    }

    fun updateWatchDelay(seconds: Int) {
        if (!appSettings.isDevMode) return
        appSettings.watchDelaySeconds = seconds
        _uiState.value = _uiState.value.copy(watchDelaySeconds = seconds)
    }

    fun updateShowClock(show: Boolean) {
        if (!appSettings.isDevMode) return
        appSettings.showClock = show
        _uiState.value = _uiState.value.copy(showClock = show)
    }

    fun updateShowTmdbCharts(show: Boolean) {
        if (!appSettings.isDevMode) return
        appSettings.showTmdbCharts = show
        _uiState.value = _uiState.value.copy(showTmdbCharts = show)
    }

    fun updateRecentRowSize(size: Int) {
        if (!appSettings.isDevMode) return
        appSettings.recentRowSize = size
        _uiState.value = _uiState.value.copy(recentRowSize = appSettings.recentRowSize)
    }

    fun updateUiScale(scale: Float?) {
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

            val message = result.toSummary(context)

            if (result.isSuccess) {
                // Refresh global settings from AppSettings (they were updated inside exportManager.importFromJson)
                if (options.importGlobalSettings) {
                    _uiState.value =
                        _uiState.value.copy(
                            themeId = appSettings.themeId,
                            uiStyleId = appSettings.uiStyleId,
                            isDevMode = appSettings.isDevMode,
                            language = appSettings.language,
                            showClock = appSettings.showClock,
                            watchDelaySeconds = appSettings.watchDelaySeconds,
                            recentRowSize = appSettings.recentRowSize,
                            uiScale = appSettings.uiScaleChoice,
                        )
                }

                refreshProviderInfo()
                _uiState.value = _uiState.value.copy(exportImportMessage = message)
            } else {
                _uiState.value = _uiState.value.copy(exportImportMessage = message)
            }
        }
    }

    fun setExportImportMessage(message: String?) {
        _uiState.value = _uiState.value.copy(exportImportMessage = message)
    }

    fun clearDatabaseMaintenanceMessage() {
        _uiState.value = _uiState.value.copy(databaseMaintenanceMessage = null)
    }

    /** Settings → "Shrink Database" — see [ProviderRepository.pruneOrphanedCatalogData]. */
    fun pruneDatabase() {
        // A failed shrink (full disk, locked database) ends the spinner with the reason instead of crashing.
        viewModelScope.launchGuarded(
            "SettingsViewModel.pruneDatabase",
            onError = { e ->
                _uiState.value =
                    _uiState.value.copy(
                        isPruningDatabase = false,
                        databaseMaintenanceMessage = friendlyErrorMessage(e, context, appSettings.isDevMode),
                    )
            },
        ) {
            _uiState.value = _uiState.value.copy(isPruningDatabase = true, databaseMaintenanceMessage = null)
            val result = providerRepo.pruneOrphanedCatalogData(userRequested = true)
            val message =
                if (result.rowsRemoved == 0L) {
                    context.getString(R.string.settings_shrink_database_nothing_to_clean)
                } else {
                    context.getString(
                        R.string.settings_shrink_database_result_format,
                        result.rowsRemoved,
                        NumberUtils.formatBytes(result.bytesReclaimed),
                    )
                }
            _uiState.value =
                _uiState.value.copy(
                    isPruningDatabase = false,
                    databaseMaintenanceMessage = message,
                    lastShrinkAtMs = appSettings.lastShrinkAtMs,
                    lastShrinkDurationMs = appSettings.lastShrinkDurationMs,
                    lastShrinkRowsRemoved = appSettings.lastShrinkRowsRemoved,
                    lastShrinkBytesReclaimed = appSettings.lastShrinkBytesReclaimed,
                )
        }
    }
}
