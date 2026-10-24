package org.njarasoa.fijerena.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.textStyleOf
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.viewmodels.ProfileUi
import org.njarasoa.fijerena.ui.components.ProfileSettingsAvatar

/**
 * The phone's bottom bar tabs (docs/plans/20261019_phone-home-tab-plan.md), in bar order. [route] is
 * the tab's root destination; [contentType] the section it opens.
 */
enum class MobileTab(
    val route: Screen,
    val contentType: String,
) {
    // Not a section: the rows of every section, first whenever the bar shows. [contentType] matches
    // no section.
    HOME(Screen.HomeTab, "HOME"),
    LIVE_TV(Screen.LiveTvTab, ContentType.LIVE_TV),
    MOVIES(Screen.MoviesTab, ContentType.MOVIES),
    TV_SHOWS(Screen.TvShowsTab, ContentType.TV_SHOWS),

    // Not a section: Settings with what it opens (sources, guide sources, Live sync…), last on the
    // bar whenever the bar shows. [contentType] matches no section.
    SETTINGS(Screen.Settings, "SETTINGS"),
    ;

    /** A section of the source (Live TV, Movies, TV Shows), as opposed to Home and Settings. */
    val isSection: Boolean get() = this != HOME && this != SETTINGS

    companion object {
        /** The tab [destination] is the root of; null for every other screen, which hides the bar. */
        fun rootedAt(destination: NavDestination?): MobileTab? =
            when {
                destination == null -> null
                destination.hasRoute<Screen.HomeTab>() -> HOME
                destination.hasRoute<Screen.LiveTvTab>() -> LIVE_TV
                destination.hasRoute<Screen.MoviesTab>() -> MOVIES
                destination.hasRoute<Screen.TvShowsTab>() -> TV_SHOWS
                destination.hasRoute<Screen.Settings>() -> SETTINGS
                else -> null
            }
    }
}

/**
 * The tab whose stack is on the back stack: its root is the one tab entry there (one tab's stack at
 * a time). Asked per tab with `getBackStackEntry`, which throws when the route isn't on the stack —
 * `currentBackStack` would say it in one read, but it is restricted API.
 */
fun NavController.currentTab(): MobileTab? =
    MobileTab.entries.firstOrNull { tab ->
        try {
            getBackStackEntry(tab.route)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

/**
 * Screens inside a tab that still hide the bar: the player, and the two forms (Add / Edit Source,
 * a profile's page), which have their own Save. Settings and what else it opens keep it — Settings
 * is a tab.
 */
fun hidesBottomBar(destination: NavDestination?): Boolean =
    destination != null &&
        (
            destination.hasRoute<Screen.Player>() ||
                destination.hasRoute<Screen.AddProvider>() ||
                destination.hasRoute<Screen.ProfileEdit>()
        )

/**
 * The tabs the bar shows: Home, the profile's merged sections (its Xtream sources', multi-source
 * plan Phase 6), then Settings — none, so no bar, when it has no section (no Xtream source: the tab
 * roots show the sources panel). [supportedTypes] is null until the sources are read. A single
 * section still shows the bar, for Home and Settings.
 */
fun visibleTabs(supportedTypes: Collection<String>?): List<MobileTab> {
    val sections = MobileTab.entries.filter { it.isSection && supportedTypes != null && it.contentType in supportedTypes }
    return if (sections.isEmpty()) emptyList() else listOf(MobileTab.HOME) + sections + MobileTab.SETTINGS
}

/**
 * The tab the app opens on: [lastTab] (Home or the section the profile last had open) when its
 * sources have it, else Home. With the sections not known or none ([supportedTypes] null or
 * empty), [lastTab] when it is a section or Home.
 */
fun startTab(
    lastTab: String?,
    supportedTypes: Collection<String>?,
): MobileTab {
    val candidates =
        MobileTab.entries.filter {
            it == MobileTab.HOME || (it.isSection && (supportedTypes.isNullOrEmpty() || it.contentType in supportedTypes))
        }
    return candidates.firstOrNull { it.contentType == lastTab } ?: MobileTab.HOME
}

/** Narrowest screen, in dp, whose bottom bar names its tabs; narrower ones show icons only. */
const val TAB_LABELS_MIN_WIDTH_DP = 380

/** Whether the bottom bar names its tabs on a screen [widthDp] wide (a 360 dp compact phone doesn't). */
fun showsTabLabels(widthDp: Int): Boolean = widthDp >= TAB_LABELS_MIN_WIDTH_DP

/**
 * The phone's bottom bar, as [MobileDockStyle] says it looks: a floating rounded bar, [selected] in
 * a lighter pill in the accent. Each tab is its icon with its name under it, or the icon only on a
 * narrow screen ([showsTabLabels]) — the name then shows in a tooltip on long-press and is the
 * icon's content description.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileBottomBar(
    tabs: List<MobileTab>,
    selected: MobileTab,
    onSelect: (MobileTab) -> Unit,
    modifier: Modifier = Modifier,
    /** The profile in use, which the Settings slot shows (its avatar over its name) in place of Settings. */
    profile: ProfileUi? = null,
) {
    val showLabels = showsTabLabels(LocalConfiguration.current.screenWidthDp)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = MobileDockStyle.sideMargin,
                    end = MobileDockStyle.sideMargin,
                    bottom = MobileDockStyle.bottomMargin,
                ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MobileDockStyle.barShape)
                    .background(MobileDockStyle.barContainer)
                    .border(MobileDockStyle.barOutlineWidth, MobileDockStyle.barOutline, MobileDockStyle.barShape)
                    .padding(MobileDockStyle.barPadding)
                    .selectableGroup(),
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == selected
                val color = if (isSelected) MobileDockStyle.selectedContent else MobileDockStyle.content
                // The last slot is the profile's: its menu switches profile and opens Settings.
                val slotProfile = profile?.takeIf { tab == MobileTab.SETTINGS }
                val label = slotProfile?.name ?: tab.label()
                val icon: @Composable (description: String?) -> Unit = { description ->
                    if (slotProfile != null) {
                        ProfileSettingsAvatar(
                            name = slotProfile.name,
                            colorIndex = slotProfile.colorIndex,
                            size = MobileDockStyle.iconSize,
                            fontSize = textStyleOf(TextRole.Caption).fontSize,
                            modifier = Modifier.semantics { description?.let { contentDescription = it } },
                        )
                    } else {
                        Icon(tab.icon(), contentDescription = description, tint = color, modifier = Modifier.size(MobileDockStyle.iconSize))
                    }
                }
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(MobileDockStyle.tabShape)
                            .background(if (isSelected) MobileDockStyle.selectedTabContainer else CinemaTheme.colors.clear)
                            .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) })
                            .padding(vertical = MobileDockStyle.tabVerticalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MobileDockStyle.iconToLabel),
                ) {
                    if (showLabels) {
                        icon(null)
                        CompositionLocalProvider(LocalContentColor provides color) {
                            CinemaText(
                                text = label,
                                role = TextRole.Caption,
                                tone = TextTone.Inherit,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                            tooltip = {
                                PlainTooltip {
                                    CinemaText(
                                        label,
                                        role = TextRole.BodySmall,
                                        tone = TextTone.Inherit,
                                    )
                                }
                            },
                            state = rememberTooltipState(),
                        ) {
                            icon(label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileTab.icon(): ImageVector =
    when (this) {
        MobileTab.HOME -> CinemaIcons.Home
        MobileTab.LIVE_TV -> CinemaIcons.LiveTv
        MobileTab.MOVIES -> CinemaIcons.Movie
        MobileTab.TV_SHOWS -> CinemaIcons.Tv
        MobileTab.SETTINGS -> CinemaIcons.Settings
    }

@Composable
fun MobileTab.label(): String =
    when (this) {
        MobileTab.HOME -> stringResource(R.string.home_title)
        MobileTab.LIVE_TV -> stringResource(R.string.provider_live_tv_label)
        MobileTab.MOVIES -> stringResource(R.string.provider_movies_label)
        MobileTab.TV_SHOWS -> stringResource(R.string.provider_tv_shows_label)
        MobileTab.SETTINGS -> stringResource(R.string.settings_title)
    }
