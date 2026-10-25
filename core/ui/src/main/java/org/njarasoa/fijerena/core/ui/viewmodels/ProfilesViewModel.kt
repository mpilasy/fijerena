package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
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
import org.njarasoa.fijerena.core.network.UiThemes
import org.njarasoa.fijerena.core.network.profile.ProfileFavorite
import org.njarasoa.fijerena.core.network.profile.ProfileFavorites
import org.njarasoa.fijerena.core.network.profile.ProfilePins
import org.njarasoa.fijerena.core.network.profile.ProfileRepository
import org.njarasoa.fijerena.core.network.profile.SwitchLock
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.PinRequest
import org.njarasoa.fijerena.core.ui.components.PinSession
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.theme.CinemaProfileColors
import org.njarasoa.fijerena.core.ui.theme.paletteById
import org.njarasoa.fijerena.core.ui.theme.styleById
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
    /** A child profile (docs/plans/20261019_tv-household-ux-plan.md): adult categories hidden, never Expert. */
    val child: Boolean = false,
) {
    /**
     * The page shows the Name and PIN rows. A child's own page (seen without Expert mode,
     * [adminView] false) does not: a child does not rename itself or set, change or remove its
     * PIN. An Expert on the child's page still edits both.
     */
    fun showsNameAndPin(adminView: Boolean): Boolean = adminView || !child
}

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
    /** Content filters reach it (an Xtream source); the pane says so of the others. */
    val filterable: Boolean = true,
)

/** Settings → Profiles. See the live sync plan → User profiles. */
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
     *
     * The child lock ([ProfileRepository.needsAdminPinToSwitch]): a switch away from a child
     * profile does not happen until an Expert's PIN is typed. It sets [leavingChild] instead, for
     * the screen to show the prompt; the prompt calls this again with [pinTyped] true.
     */
    fun switchTo(
        id: String,
        onSwitched: () -> Unit,
        pinTyped: Boolean = false,
    ) {
        // One switch at a time: a second pick while one runs would only queue behind it.
        if (switchJob?.isActive == true) return
        _leavingChild.value = null
        if (id == appSettings.activeProfileId) {
            onSwitched()
            return
        }
        switchJob =
            viewModelScope.launch {
                when (val lock = if (pinTyped) SwitchLock.Free else repository.needsAdminPinToSwitch(id)) {
                    is SwitchLock.AskPin -> {
                        _leavingChild.value =
                            PinRequest.LeaveChild(lock.pinHolders, id, context.getString(R.string.pin_enter_admin))
                    }

                    // Logged where the profiles were read; the device stays on the child.
                    SwitchLock.Refused -> {
                        Unit
                    }

                    SwitchLock.Free -> {
                        _switchingTo.value = profiles.value.firstOrNull { it.id == id }?.name ?: ""
                        try {
                            AppContainer.getInstance(context).switchProfile(id)
                            onSwitched()
                        } finally {
                            _switchingTo.value = null
                        }
                    }
                }
            }
    }

    private var switchJob: Job? = null

    // A switch away from a child profile waiting for an Expert's PIN, for the screen to ask it.
    private val _leavingChild = MutableStateFlow<PinRequest.LeaveChild?>(null)
    val leavingChild: StateFlow<PinRequest.LeaveChild?> = _leavingChild.asStateFlow()

    /** Cancel on the child lock's prompt: the device stays on the child profile. */
    fun cancelLeavingChild() {
        _leavingChild.value = null
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

    /** The profiles' optional PINs (the profile pin plan). */
    val pins = ProfilePins(appSettings)

    /**
     * The Expert profiles (admin, for now) with a PIN, oldest first: while there is one, becoming
     * Expert (the 7 taps) or adding a profile ([addProfilePinHolders]) asks for one of their PINs.
     */
    fun adminPinHolders(): List<String> = profiles.value.map { it.id }.filter { appSettings.devMode(it) && pins.hasPin(it) }

    /**
     * There is a child profile and no Expert has a PIN: Settings asks the admin to set one
     * (docs/plans/20261019_tv-household-ux-plan.md, Phase 3), since without it anyone can open
     * Settings, change the filters or become Expert with the 7 taps.
     */
    fun needsAdminPin(): Boolean = profiles.value.any { appSettings.isChildProfile(it.id) } && adminPinHolders().isEmpty()

    /**
     * [id]'s PIN is the last Expert PIN and a child profile exists: removing it stops the child
     * lock, on every linked device (the PIN syncs). Remove PIN's confirm says so. From the listed
     * profiles, which a profile page has read.
     */
    fun isLastAdminPin(id: String): Boolean = adminPinHolders() == listOf(id) && profiles.value.any { appSettings.isChildProfile(it.id) }

    /** [id] has a PIN not typed in this session: picking it asks for it first. */
    fun needsPin(id: String): Boolean = pins.hasPin(id) && !PinSession.isUnlocked(id)

    /**
     * The PINs Add profile asks for (one of them), the same in Settings and on TV's picker; empty
     * for no prompt. See [addProfilePins].
     */
    fun addProfilePinHolders(): List<String> {
        val inUse = appSettings.activeProfileId
        return addProfilePins(appSettings.devMode(inUse), !needsPin(inUse), adminPinHolders())
    }

    /** [onAdded] gets the new profile's id, on the main thread. */
    fun addProfile(
        name: String,
        colorIndex: Int,
        child: Boolean = false,
        onAdded: (String) -> Unit = {},
    ) {
        // A kid adds nothing (ProfileRules).
        val fields = ProfileRules.add(actor(), ProfileFields(name, colorIndex, ProfileSettings(false, true, child))) ?: return
        viewModelScope.launch { onAdded(repository.addProfile(fields.name, fields.colorIndex, fields.settings.child)) }
    }

    /** The profile in use, for [ProfileRules]. */
    private fun actor(): ProfileActor {
        val id = appSettings.activeProfileId
        return ProfileActor(id, appSettings.isChildProfile(id), appSettings.devMode(id))
    }

    fun updateProfile(
        id: String,
        name: String,
        colorIndex: Int,
        requested: ProfileSettings,
    ) {
        // Only what changed, so an untouched switch doesn't queue a sync record.
        val stored = settingsOf(id)
        // Who may change what (ProfileRules): a field the profile in use may not change keeps its
        // stored value. A profile that isn't listed is not changed.
        val listed = profiles.value.firstOrNull { it.id == id } ?: return
        val allowed =
            ProfileRules.update(
                actor(),
                id,
                ProfileFields(listed.name, listed.colorIndex, stored),
                ProfileFields(name, colorIndex, requested),
            ) ?: return
        val settings = allowed.settings
        // The last Expert keeps it and can't become a child (its page disables both switches; this
        // guards the rest). A child is never Expert: ProfileRepository.setChildProfile turns it off.
        val lastExpert = isLastExpert(id)
        val child = settings.child && !(stored.devMode && lastExpert)
        if (child != stored.child) {
            viewModelScope.launchGuarded("ProfilesViewModel.setChildProfile") { repository.setChildProfile(id, child) }
        }
        val devMode = settings.devMode || (stored.devMode && lastExpert)
        if (!child && devMode != stored.devMode) appSettings.setDevMode(id, devMode)
        if (settings.autoplayNextEpisode != stored.autoplayNextEpisode) {
            appSettings.setAutoplayNextEpisode(id, settings.autoplayNextEpisode)
        }
        viewModelScope.launch { repository.updateProfile(id, allowed.name, allowed.colorIndex) }
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
        if (!ProfileRules.changesSetup(actor())) return
        viewModelScope.launchGuarded("ProfilesViewModel.saveCategoryFilters") {
            AppContainer.getInstance(context).providerRepository.setCategoryFilters(providerId, profileId, filters)
        }
    }

    // A child profile's Favourites pane (docs/plans/20261019_kids-home-plan.md, Phase 3): what its
    // Home shows, read and changed from the admin's Settings.
    private val profileFavorites by lazy { ProfileFavorites(context) }

    /** [profileId]'s favourite films, shows and channels, newest first. */
    suspend fun favoritesOf(profileId: String): List<ProfileFavorite> = profileFavorites.list(profileId)

    /**
     * Titles matching [query] on [profileId]'s sources that [profileId] may see (its content
     * filters, a child's adult rules), each marked when already a favourite. Films, then shows,
     * then channels; at most [FAVORITE_SEARCH_LIMIT] per section and source.
     */
    suspend fun searchFavoritesFor(
        profileId: String,
        query: String,
    ): List<ProfileFavorite> {
        if (query.isBlank()) return emptyList()
        val current = profileFavorites.list(profileId).mapTo(HashSet()) { it.key }
        val container = AppContainer.getInstance(context)
        val found = mutableListOf<ProfileFavorite>()
        for (contentType in listOf(ContentType.MOVIES, ContentType.TV_SHOWS, ContentType.LIVE_TV)) {
            for (providerId in profileFavorites.sourceIds(profileId)) {
                val results = container.getStoredDataRepository(providerId).search(query, contentType, includeExcluded = true)
                val items = results?.getOrNull().orEmpty()
                if (items.isEmpty()) continue
                val visible = profileFavorites.visibility(profileId, providerId, contentType)
                items
                    .filter { visible(it.categoryId, it.name) }
                    .take(FAVORITE_SEARCH_LIMIT)
                    .mapTo(found) {
                        val favorite = ProfileFavorite(providerId, it.id, contentType, it.name, it.categoryId, it.thumbnailUrl)
                        favorite.copy(isFavorite = favorite.key in current)
                    }
            }
        }
        return found
    }

    /** Adds [item] to [profileId]'s favourites ([on]) or takes it out. */
    fun setFavoriteFor(
        profileId: String,
        item: ProfileFavorite,
        on: Boolean,
    ) {
        if (!ProfileRules.changesSetup(actor())) return
        viewModelScope.launchGuarded("ProfilesViewModel.setFavoriteFor") {
            if (on) profileFavorites.add(profileId, item) else profileFavorites.remove(profileId, item)
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
        if (!ProfileRules.changesSetup(actor())) return
        toggledSourceKeys(appSettings.profileSourceKeys(profileId).orEmpty(), rows, providerKey, on)
            ?.let { appSettings.setProfileSourceKeys(profileId, it) }
    }

    /** [id]'s own settings — not necessarily the profile this device uses. */

    /** The profile in use has Expert mode (admin, for now): it may change sources and content filters. */
    val viewerIsAdmin: Boolean get() = appSettings.isDevMode

    fun settingsOf(id: String): ProfileSettings =
        ProfileSettings(appSettings.devMode(id), appSettings.autoplayNextEpisode(id), appSettings.isChildProfile(id))

    /** [id]'s colour theme and Look and Feel; set at once, like its sources (the profile in use repaints). */

    /** [id]'s colour theme as applied: a retired or unknown one reads as the one shown instead. */
    fun themeOf(id: String): String = paletteById(appSettings.themeId(id)).id

    /** [id]'s Look as applied: a retired or unknown one reads as the one shown instead. */
    fun uiStyleOf(id: String): String = styleById(appSettings.uiStyleId(id)).id

    fun setTheme(
        id: String,
        themeId: String,
    ) {
        if (ProfileRules.changesLook(actor(), id)) appSettings.setThemeId(id, themeId)
    }

    fun setUiStyle(
        id: String,
        styleId: String,
    ) {
        if (ProfileRules.changesLook(actor(), id)) appSettings.setUiStyleId(id, styleId)
    }

    /** Whether [id]'s Home and lists show the focused title's poster behind them. */
    fun posterBackdropOf(id: String): Boolean = appSettings.posterBackdrop(id)

    fun setPosterBackdrop(
        id: String,
        on: Boolean,
    ) {
        if (ProfileRules.changesLook(actor(), id)) appSettings.setPosterBackdrop(id, on)
    }

    fun uiThemeOf(id: String): String = appSettings.uiThemeId(id).takeIf { UiThemes.packaged(it) != null } ?: UiThemes.CUSTOM

    /** Picks [id]'s UI theme; a packaged one also sets its colour theme and Look and Feel. */
    fun setUiTheme(
        id: String,
        uiThemeId: String,
    ) {
        if (ProfileRules.changesLook(actor(), id)) appSettings.setUiThemeId(id, uiThemeId)
    }

    /** [id] uses a packaged UI theme: its colour theme and Look and Feel can't be changed one by one. */
    fun lookLockedOf(id: String): Boolean = UiThemes.packaged(appSettings.uiThemeId(id)) != null

    fun deleteProfile(id: String) {
        // Only an Expert deletes a profile (ProfileRules).
        if (!ProfileRules.delete(actor())) return
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

        // A Favourites pane search: per section and source, enough to find a title without a long list.
        private const val FAVORITE_SEARCH_LIMIT = 20

        /**
         * The rule of Add profile's PIN (the settings flow review, E7): nothing when the profile in
         * use is an Expert ([inUseExpert]) whose PIN is typed in this session or that has no PIN
         * ([inUseOpen]); otherwise any Expert's PIN, one of [adminPinHolders] (none to ask while no
         * Expert has a PIN). One prompt at most.
         */
        fun addProfilePins(
            inUseExpert: Boolean,
            inUseOpen: Boolean,
            adminPinHolders: List<String>,
        ): List<String> = if (inUseExpert && inUseOpen) emptyList() else adminPinHolders

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
                ProfileSourceUi(p.providerKey, p.name, isOn, isOn && onCount > 1, filterable = p.type == "XTREAM")
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
