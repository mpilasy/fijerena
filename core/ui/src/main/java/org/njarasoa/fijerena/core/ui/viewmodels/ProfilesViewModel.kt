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
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.theme.CinemaProfileColors

data class ProfileUi(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val isActive: Boolean,
)

/** Settings → Profiles. See `docs/plans/20260929_live-sync-plan.md` → User profiles. */
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
        viewModelScope.launch {
            if (id != activeProfileId.value) {
                AppContainer.getInstance(context).switchProfile(id)
                activeProfileId.value = id
            }
            onSwitched()
        }
    }

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
    ) {
        viewModelScope.launch { repository.updateProfile(id, name, colorIndex) }
    }

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
