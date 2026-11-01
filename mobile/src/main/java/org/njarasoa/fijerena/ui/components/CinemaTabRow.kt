package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * The phone's tab row (docs/plans/20261016_design-system-plan.md): one tab per entry of [labels],
 * the one at [selectedIndex] in the theme's accent, the others in secondary text, set in the label
 * role. [transparent] lets the screen behind show through (a row inside a list or a sheet); without
 * it the row sits on the theme's surface, as Material's tab row does. [scrollable]: each tab as
 * wide as its label, the row scrolling only when they do not fit (four or more tabs, whose labels
 * equal widths would cut).
 */
@Composable
fun CinemaTabRow(
    selectedIndex: Int,
    labels: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    transparent: Boolean = false,
    scrollable: Boolean = false,
) {
    val c = CinemaTheme.colors
    val tabs: @Composable () -> Unit = {
        labels.forEachIndexed { index, label ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                text = { TabLabel(label) },
                selectedContentColor = c.accent,
                unselectedContentColor = c.textSecondary,
            )
        }
    }
    // A scrolling row's tab: its label with less space at its sides than Material's 16 dp, so four
    // tabs fit the phone's width in every Look and Feel.
    val labelTabs: @Composable () -> Unit = {
        labels.forEachIndexed { index, label ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                modifier = Modifier.height(MobileDimensions.minTouchTarget),
                selectedContentColor = c.accent,
                unselectedContentColor = c.textSecondary,
            ) {
                TabLabel(label, Modifier.padding(horizontal = CinemaSpacing.sm))
            }
        }
    }
    if (scrollable) {
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            modifier = modifier,
            containerColor = if (transparent) c.clear else c.surface,
            contentColor = c.accent,
            edgePadding = CinemaSpacing.xxs,
            // Each tab as wide as its label (Material gives each at least 90 dp): four tabs that fit
            // do not scroll, so the row does not move its first tab off the screen to centre the
            // selected one.
            minTabWidth = MobileDimensions.scrollableTabMinWidth,
            tabs = labelTabs,
        )
    } else {
        PrimaryTabRow(
            selectedTabIndex = selectedIndex,
            modifier = modifier,
            containerColor = if (transparent) c.clear else c.surface,
            contentColor = c.accent,
            tabs = tabs,
        )
    }
}

/** One tab of a [CinemaIconTabRow]: a label with an icon before it. */
data class CinemaIconTab(
    val label: String,
    val icon: ImageVector,
)

/**
 * A scrolling row of icon tabs on a clear background, no divider under it. [selectedIndex] -1
 * marks none (the selection is elsewhere, e.g. a chip below). Looks as [CinemaTabRow] otherwise.
 */
@Composable
fun CinemaIconTabRow(
    selectedIndex: Int,
    tabs: List<CinemaIconTab>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    edgePadding: Dp = CinemaSpacing.xxs,
) {
    val c = CinemaTheme.colors
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex.coerceAtLeast(0),
        modifier = modifier,
        containerColor = c.clear,
        contentColor = c.accent,
        edgePadding = edgePadding,
        indicator = {
            if (selectedIndex >= 0) {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(selectedIndex, matchContentSize = true),
                    width = Dp.Unspecified,
                    color = c.accent,
                )
            }
        },
        divider = {},
    ) {
        tabs.forEachIndexed { index, tab ->
            LeadingIconTab(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                text = { TabLabel(tab.label) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        modifier = Modifier.size(MobileDimensions.iconSmall),
                    )
                },
                selectedContentColor = c.accent,
                unselectedContentColor = c.textSecondary,
            )
        }
    }
}

@Composable
private fun TabLabel(
    label: String,
    modifier: Modifier = Modifier,
) {
    CinemaText(
        label,
        modifier = modifier,
        role = TextRole.Label,
        tone = TextTone.Inherit,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
