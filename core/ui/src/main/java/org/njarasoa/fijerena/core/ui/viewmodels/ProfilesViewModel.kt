package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.profile.ProfilePins
import org.njarasoa.fijerena.core.network.profile.ProfileRepository
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.theme.CinemaProfileColors
import org.njarasoa.fijerena.core.ui.utils.launchGuarded

data class ProfileUi(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val isActive: Boolean,
)

/** A profile's own settings, edited on its profile page for whichever profile it is. */
data class ProfileSettings(
    val devMode: Boolean,
    val autoplayNextEpisode: Boolean,
)

/** One of a profile's sources whose content filters its page edits; its name heads the filters. */
data class FilterSource(
    val id: Long,
    val name: String,
)

/**
 * One source on a profile's Sources pane: whether the profile has it ([isOn]), and whether its
 * switch may turn it off — not the profile's last source.
 */
data class ProfileSourceUi(
    val providerKey: String,
    val name: String,
    val isOn: Boolean,
    val canTurnOff: Boolean,
)

/** Settings → Profiles. See `docs/plans/archive/20260929_live-sync-plan.md` → User profiles. */
class ProfilesViewModel(
    private val context: Context,
    private val repository: ProfileRepository,
    private val appSettings: AppSettings,
) : ViewModel() {
    val profiles: StateFlow<List<ProfileUi>> =
        // The stored profile in use, so a switch live sync makes (the profile in use deleted on
        // another device) shows as well as [switchTo]'s.
        combine(repository.observeProfiles(), appSettings.observeActiveProfileId()) { rows, activeId ->
            rows.map { ProfileUi(it.id, it.name, it.colorIndex, it.id == activeId) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** The profile this device uses, or null until the list has loaded. */
    val activeProfile: StateFlow<ProfileUi?> =
        profiles
            .map { list -> list.firstOrNull { it.isActive } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * Makes [id] this device's profile, then calls [onSwitched] — on the main thread — so the
     * caller can rebuild its screens. Picking the profile already in use still calls it: from the
     * launch picker that is the normal way in.
     */
    fun switchTo(
        id: String,
        onSwitched: () -> Unit,
    ) {
        // One switch at a time: a second pick while one runs would only queue behind it.
        if (_switchingTo.value != null) return
        if (id == appSettings.activeProfileId) {
            onSwitched()
            return
        }
        _switchingTo.value = profiles.value.firstOrNull { it.id == id }?.name ?: ""
        viewModelScope.launch {
            try {
                AppContainer.getInstance(context).switchProfile(id)
                onSwitched()
            } finally {
                _switchingTo.value = null
            }
        }
    }

    // The name of the profile being switched to while a switch runs, for the picker to say so.
    private val _switchingTo = MutableStateFlow<String?>(null)
    val switchingTo: StateFlow<String?> = _switchingTo.asStateFlow()

    // Why the last delete was refused, for the screen to show; null once dismissed.
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * Whether [id] is the only profile with Expert mode (the admin, for now): it can't turn it off,
     * so there is always one (see [ProfileRepository.ensureAnExpert], the backstop). From the
     * listed profiles, which a profile page has read; true while they aren't, to be safe.
     */
    fun isLastExpert(id: String): Boolean = profiles.value.none { it.id != id && appSettings.devMode(it.id) }

    /** The profiles' optional PINs (docs/plans/20261017_profile-pin-plan.md). */
    val pins = ProfilePins(appSettings)

    fun addProfile(
        name: String,
        colorIndex: Int,
    ) {
        viewModelScope.launch { repository.addProfile(name, colorIndex) }
    }

    fun updateProfile(
        id: String,
        name: String,
        colorIndex: Int,
        settings: ProfileSettings,
    ) {
        // Only what changed, so an untouched switch doesn't queue a sync record.
        val stored = settingsOf(id)
        // The last Expert keeps it (its page disables the switch; this guards the rest).
        val devMode = settings.devMode || isLastExpert(id)
        if (devMode != stored.devMode) appSettings.setDevMode(id, devMode)
        if (settings.autoplayNextEpisode != stored.autoplayNextEpisode) {
            appSettings.setAutoplayNextEpisode(id, settings.autoplayNextEpisode)
        }
        viewModelScope.launch { repository.updateProfile(id, name, colorIndex) }
    }

    /**
     * The sources whose content filters [profileId]'s page edits: its Xtream sources, in its order
     * (filters only hide Xtream categories). With two or more the page asks which first (O4).
     * Re-emits when a source or the profile's sources change.
     */
    fun filterSources(profileId: String): Flow<List<FilterSource>> {
        val providers = AppContainer.getInstance(context).providerRepository
        return combine(providers.getAllProviders(), appSettings.observeProfileSourceKeys(profileId)) { all, keys ->
            filterSourcesOf(ProviderRepository.resolveProfileSources(all, keys))
        }
    }

    /** [profileId]'s content filters on [providerId] — any profile, not only the one in use. */
    fun loadCategoryFilters(
        providerId: Long,
        profileId: String,
        onLoaded: (CategoryFilters) -> Unit,
    ) {
        viewModelScope.launchGuarded("ProfilesViewModel.loadCategoryFilters") {
            onLoaded(AppContainer.getInstance(context).providerRepository.getCategoryFilters(providerId, profileId))
        }
    }

    /** Saves [profileId]'s content filters; they apply now only if it is the profile in use. */
    fun saveCategoryFilters(
        providerId: Long,
        profileId: String,
        filters: CategoryFilters,
    ) {
        viewModelScope.launchGuarded("ProfilesViewModel.saveCategoryFilters") {
            AppContainer.getInstance(context).providerRepository.setCategoryFilters(providerId, profileId, filters)
        }
    }

    /**
     * Every source on this device, each with whether [profileId] has it — for its profile page's
     * Sources pane. Re-emits when a source or the profile's sources change, here or from sync.
     */
    fun profileSources(profileId: String): Flow<List<ProfileSourceUi>> {
        val providers = AppContainer.getInstance(context).providerRepository
        return combine(providers.getAllProviders(), appSettings.observeProfileSourceKeys(profileId)) { all, keys ->
            profileSourceRows(all, keys)
        }
    }

    /** Turns [providerKey] on or off for [profileId]; refused when [rows] say it can't go off. */
    fun setProfileSource(
        profileId: String,
        rows: List<ProfileSourceUi>,
        providerKey: String,
        on: Boolean,
    ) {
        toggledSourceKeys(appSettings.profileSourceKeys(profileId).orEmpty(), rows, providerKey, on)
            ?.let { appSettings.setProfileSourceKeys(profileId, it) }
    }

    /** [id]'s own settings — not necessarily the profile this device uses. */

    /** The profile in use has Expert mode (admin, for now): it may change sources and content filters. */
    val viewerIsAdmin: Boolean get() = appSettings.isDevMode

    fun settingsOf(id: String): ProfileSettings = ProfileSettings(appSettings.devMode(id), appSettings.autoplayNextEpisode(id))

    /** [id]'s colour theme and Look and Feel; set at once, like its sources (the profile in use repaints). */
    fun themeOf(id: String): String = appSettings.themeId(id)

    fun uiStyleOf(id: String): String = appSettings.uiStyleId(id)

    fun setTheme(
        id: String,
        themeId: String,
    ) = appSettings.setThemeId(id, themeId)

    fun setUiStyle(
        id: String,
        styleId: String,
    ) = appSettings.setUiStyleId(id, styleId)

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            _message.value =
                when (repository.deleteProfile(id)) {
                    ProfileRepository.DeleteBlocked.ACTIVE -> context.getString(R.string.profile_delete_blocked_active)
                    ProfileRepository.DeleteBlocked.LAST -> context.getString(R.string.profile_delete_blocked_last)
                    ProfileRepository.DeleteBlocked.NONE -> null
                }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /** The colour a new profile starts with: the first one nobody is using yet. */
    fun nextFreeColorIndex(): Int {
        val used = profiles.value.map { it.colorIndex }.toSet()
        val size = CinemaProfileColors.palette.size
        return (0 until size).firstOrNull { it !in used } ?: profiles.value.size % size
    }

    internal companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /**
         * [providers] as a profile's Sources pane shows them, for a profile with [keys] (its
         * `providerKey`s; null for none stored).
         */
        fun profileSourceRows(
            providers: List<ProviderEntity>,
            keys: Set<String>?,
        ): List<ProfileSourceUi> {
            val onCount = providers.count { keys?.contains(it.providerKey) == true }
            return providers.map { p ->
                val isOn = keys?.contains(p.providerKey) == true
                ProfileSourceUi(p.providerKey, p.name, isOn, isOn && onCount > 1)
            }
        }

        /** The content-filter sources among a profile's [sources]: its Xtream ones. */
        fun filterSourcesOf(sources: List<ProviderEntity>): List<FilterSource> =
            sources.filter { it.type == "XTREAM" }.map { FilterSource(it.id, it.name) }

        /**
         * The keys to store after turning [providerKey] on or off, or null to change nothing (a
         * source that isn't listed, or one that can't go off). Starts from [stored], not from the
         * rows: a key whose source hasn't reached this device yet must not be dropped.
         */
        fun toggledSourceKeys(
            stored: Set<String>,
            rows: List<ProfileSourceUi>,
            providerKey: String,
            on: Boolean,
        ): Set<String>? {
            val row = rows.firstOrNull { it.providerKey == providerKey }
            val onKeys = stored + rows.filter { it.isOn }.map { it.providerKey }
            return when {
                row == null -> null
                on -> onKeys + providerKey
                row.canTurnOff -> onKeys - providerKey
                else -> null
            }
        }
    }
}
