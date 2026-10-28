package org.njarasoa.fijerena.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.XtreamMediaProvider
import org.njarasoa.fijerena.core.network.profile.ProfileRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.components.initialOf
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.home.BrowseSource
import org.njarasoa.fijerena.core.ui.home.ProfileSections
import org.njarasoa.fijerena.ui.components.rail.HideTvNavRail
import org.njarasoa.fijerena.ui.components.rail.RailItem
import org.njarasoa.fijerena.ui.components.rail.TvNavRail
import org.njarasoa.fijerena.ui.components.rail.TvNavRailState

/**
 * The TV's sections (the tv nav rail plan → Sections keep their place): their
 * rail item, their root destination and the content type they browse. The roots are the phone's tab
 * routes: Navigation saves a section's back stack under its root, so each needs its own.
 */
internal enum class TvSection(
    val item: RailItem,
    val route: Screen,
    val contentType: String,
) {
    LIVE_TV(RailItem.LIVE_TV, Screen.LiveTvTab, ContentType.LIVE_TV),
    MOVIES(RailItem.MOVIES, Screen.MoviesTab, ContentType.MOVIES),
    TV_SHOWS(RailItem.TV_SHOWS, Screen.TvShowsTab, ContentType.TV_SHOWS),
    ;

    companion object {
        fun of(item: RailItem): TvSection? = entries.firstOrNull { it.item == item }

        fun of(contentType: String): TvSection? = entries.firstOrNull { it.contentType == contentType }
    }
}

/**
 * The rail's items, top to bottom: the avatar, Home, the sections [sections] names (the profile's
 * Xtream sources' content types, merged; none while unknown or when it has no Xtream source),
 * Search, Settings. The Browse entries go between the sections and Search (`TvNavRail`). A [child]
 * profile has only the avatar, Home and Search: it watches what is on its Home or what it finds,
 * never browses (docs/plans/20261019_kids-home-plan.md, K-D1, K-D7), and has no Settings.
 */
internal fun railItems(
    sections: Set<String>?,
    child: Boolean = false,
): List<RailItem> =
    if (child) {
        listOf(RailItem.PROFILE, RailItem.HOME, RailItem.SEARCH)
    } else {
        listOf(RailItem.PROFILE, RailItem.HOME) +
            TvSection.entries.filter { sections != null && it.contentType in sections }.map { it.item } +
            listOf(RailItem.SEARCH, RailItem.SETTINGS)
    }

/** What is on top of the back stack, as far as the rail's lit item cares. */
internal enum class TopScreen { HOME, SEARCH, SETTINGS, OTHER }

/**
 * The rail item lit at rest: Home on Home, Settings while one of its screens is on top, Search
 * while Search is; otherwise the section whose stack this is ([section]), or Home for what Home
 * opened outside the sections (a title from Continue Watching, Search's results).
 */
internal fun railCurrent(
    top: TopScreen,
    section: TvSection?,
): RailItem =
    when (top) {
        TopScreen.HOME -> RailItem.HOME
        TopScreen.SEARCH -> RailItem.SEARCH
        TopScreen.SETTINGS -> RailItem.SETTINGS
        TopScreen.OTHER -> section?.item ?: RailItem.HOME
    }

internal fun NavDestination.topScreen(): TopScreen =
    when {
        hasRoute<Screen.ContentTypeSelection>() -> TopScreen.HOME
        hasRoute<Screen.Search>() -> TopScreen.SEARCH
        isSettingsTree() -> TopScreen.SETTINGS
        else -> TopScreen.OTHER
    }

/**
 * Settings and the screens it opens: not a section's place, so a section pick drops them rather
 * than save them with the section.
 */
internal fun NavDestination.isSettingsTree(): Boolean =
    hasRoute<Screen.Settings>() ||
        hasRoute<Screen.SyncSettings>() ||
        hasRoute<Screen.AddProvider>() ||
        hasRoute<Screen.ProviderSelection>() ||
        hasRoute<Screen.EpgManagement>() ||
        hasRoute<Screen.DeviceInfo>() ||
        hasRoute<Screen.Licenses>() ||
        hasRoute<Screen.Terms>() ||
        hasRoute<Screen.LicenseDetail>() ||
        hasRoute<Screen.Diagnostics>()

/**
 * The section whose stack is on the back stack: its root is the one section root there (a pick
 * replaces the stack above Home). Asked per section with `getBackStackEntry`, which throws when the
 * route isn't on the stack — `currentBackStack` would say it in one read, but it is restricted API.
 */
internal fun NavController.currentSection(): TvSection? = TvSection.entries.firstOrNull { isOnBackStack(it.route) }

private fun NavController.isOnBackStack(route: Screen): Boolean =
    try {
        getBackStackEntry(route)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

/**
 * What the rail shows of the profile's sources (multi-source plan, Phase 6): the merged sections of
 * its Xtream sources and a Browse entry for each of its other sources (O1); null when the profile
 * has no source at all, where the rail stays hidden (Home offers Add Source).
 */
private suspend fun loadRailSource(context: Context): ProfileSections? = ProfileSections.load(context).takeUnless { it.hasNoSources }

/**
 * The rail over the nav host: its items from the profile's sources (the merged sections, then a
 * Browse entry per other source), the profile's initial, and the lit
 * item from the back stack. The sources and profile are re-read on every destination change — a
 * profile switch, a source turned on or off, added or renamed all end on another screen. Hidden
 * while the profile has no source, and without Home under the screen (Settings, Safe mode or Newer data as the
 * first screen): the rail's picks all lead from Home. Its own composable, so a destination change recomposes the rail, not the
 * NavHost.
 */
@Composable
internal fun TvSectionRail(
    rail: TvNavRailState,
    navController: NavController,
    onSelect: (RailItem) -> Unit,
    // A "Browse <source>" entry picked (O1): that source's sections, browsed on it alone.
    onBrowse: (BrowseSource) -> Unit,
) {
    val context = LocalContext.current
    val entry by navController.currentBackStackEntryAsState()
    var source by remember { mutableStateOf<ProfileSections?>(null) }
    var homeOnStack by remember { mutableStateOf(false) }
    var activeProfileId by remember { mutableStateOf<String?>(null) }
    var child by remember { mutableStateOf(false) }
    val profiles by remember { ProfileRepository(context.applicationContext).observeProfiles() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(entry?.id) {
        val destination = entry?.destination ?: return@LaunchedEffect
        rail.current = railCurrent(destination.topScreen(), navController.currentSection())
        homeOnStack = navController.isOnBackStack(Screen.ContentTypeSelection)
        source = loadRailSource(context)
        withContext(Dispatchers.IO) {
            val settings = AppSettings(context.applicationContext)
            activeProfileId = settings.activeProfileId
            child = settings.isChildProfile(settings.activeProfileId)
        }
    }

    if (source == null || !homeOnStack) HideTvNavRail()
    val activeProfile = profiles.firstOrNull { it.id == activeProfileId }
    val profileInitial = activeProfile?.name?.let(::initialOf).orEmpty()
    // The avatar's label and colour, as the Home header draws it ("A" alone said nothing).
    rail.profileName = activeProfile?.name
    rail.profileColorIndex = activeProfile?.colorIndex ?: 0
    TvNavRail(
        state = rail,
        items = railItems(source?.merged, child),
        profileInitial = profileInitial,
        onSelect = onSelect,
        browse = if (child) emptyList() else source?.browse.orEmpty(),
        onBrowse = onBrowse,
    )
}
