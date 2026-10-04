package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.profile.ProfileRepository
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
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

/** The source this device uses, as a profile's page shows it: only its name heads the filters. */
data class SourceInUse(
    val id: Long,
    val name: String,
    val type: String,
)

/** Settings → Profiles. See `docs/plans/archive/20260929_live-sync-plan.md` → User profiles. */
class ProfilesViewModel(
    private val context: Context,
    private val repository: ProfileRepository,
    private val appSettings: AppSettings,
) : ViewModel() {
    private val activeProfileId = MutableStateFlow(appSettings.activeProfileId)

    val profiles: StateFlow<List<ProfileUi>> =
        combine(repository.observeProfiles(), activeProfileId) { rows, activeId ->
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
        if (id == activeProfileId.value) {
            onSwitched()
            return
        }
        _switchingTo.value = profiles.value.firstOrNull { it.id == id }?.name ?: ""
        viewModelScope.launch {
            try {
                AppContainer.getInstance(context).switchProfile(id)
                activeProfileId.value = id
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
        if (settings.devMode != stored.devMode) appSettings.setDevMode(id, settings.devMode)
        if (settings.autoplayNextEpisode != stored.autoplayNextEpisode) {
            appSettings.setAutoplayNextEpisode(id, settings.autoplayNextEpisode)
        }
        viewModelScope.launch { repository.updateProfile(id, name, colorIndex) }
    }

    // The source in use, for a profile page's Content filters; null until loaded, or with no source.
    private val _sourceInUse = MutableStateFlow<SourceInUse?>(null)
    val sourceInUse: StateFlow<SourceInUse?> = _sourceInUse.asStateFlow()

    fun loadSourceInUse() {
        viewModelScope.launchGuarded("ProfilesViewModel.loadSourceInUse") {
            _sourceInUse.value =
                AppContainer
                    .getInstance(context)
                    .providerRepository
                    .getActiveProvider()
                    ?.let { SourceInUse(it.id, it.name, it.type) }
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

    /** [id]'s own settings — not necessarily the profile this device uses. */
    fun settingsOf(id: String): ProfileSettings = ProfileSettings(appSettings.devMode(id), appSettings.autoplayNextEpisode(id))

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

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
