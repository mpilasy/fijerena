package org.njarasoa.fijerena.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.colorOf
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.components.input.CurrentIndicatorEdge
import org.njarasoa.fijerena.ui.components.input.TvInputDefaults
import org.njarasoa.fijerena.ui.components.input.currentIndicator
import org.njarasoa.fijerena.ui.components.modifiers.tabChipFill
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * One row of section-tab pills (Cast / Details / Similar / ...) for TV detail screens, with the
 * same focus/selection shape as [EpisodeSelectionScreen][org.njarasoa.fijerena.feature.episode.EpisodeSelectionScreen]'s
 * season tabs (the tv detail hero ui plan, Phase 4).
 *
 * D-pad left/right moves focus between tabs, and each tab selects when it gets focus, so moving
 * alone switches the visible section.
 *
 * [entryFocusRequester], if given, is attached to the selected tab: a caller uses it to send D-pad
 * Down from above into this row (on the selected tab, not where geometry search prefers) and Back
 * from inside the open section back to this row instead of out of the screen.
 *
 * Right on the last tab goes to [endFocusRequester] when given (a control at the end of the row),
 * else stays: with geometric search it left the row (with only a Details tab it jumped to Play).
 *
 * [firstTabModifier] goes on the first tab only: a screen whose tab row starts at its left edge
 * passes `Modifier.leftToRail()` there, so Left from the first tab opens the TV navigation rail.
 */
@Composable
fun TvSectionTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    entryFocusRequester: FocusRequester? = null,
    endFocusRequester: FocusRequester? = null,
    firstTabModifier: Modifier = Modifier,
    /**
     * An icon per tab (null: the label as text). A tab with an icon shows the icon and its label
     * while selected or focused, the icon alone otherwise (the row must fit a list's pane); [tabs]
     * stays its accessible name.
     */
    icons: List<ImageVector?> = emptyList(),
) {
    val scale = LocalUiScale.current
    // One FocusRequester per tab, wired to its left/right neighbour below: default geometric search is
    // not reliable for this row shape on real hardware (see SeasonTabs in EpisodeSelectionScreen.kt).
    val focusRequesters = remember(tabs) { tabs.map { FocusRequester() } }

    Row(
        modifier =
            modifier
                .padding(vertical = Spacing.sm.scaled(scale))
                .focusGroup()
                .focusProperties {
                    onEnter = { focusRequesters.getOrNull(selectedIndex)?.requestFocus() }
                },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // When the row is too narrow, the longest label gives way (one line, "…"), not the tabs after it: a
        // long category tab left Favourites a column one letter wide in the Live TV panel. Weighted children
        // are measured after the others.
        val longest = tabs.indices.maxByOrNull { tabs[it].length } ?: -1
        tabs.forEachIndexed { index, label ->
            val giveWay = if (index == longest) Modifier.weight(1f, fill = false) else Modifier
            SectionTab(
                label = label,
                icon = icons.getOrNull(index),
                isSelected = index == selectedIndex,
                onSelected = { onTabSelected(index) },
                focusRequester = focusRequesters[index],
                previousTabFocusRequester = focusRequesters.getOrNull(index - 1),
                nextTabFocusRequester = focusRequesters.getOrNull(index + 1) ?: endFocusRequester ?: FocusRequester.Cancel,
                // Second requester on the same node, only on the selected tab; it moves with the selection.
                entryFocusRequester = if (index == selectedIndex) entryFocusRequester else null,
                modifier = giveWay.then(if (index == 0) firstTabModifier else Modifier),
            )
        }
    }
}

@Composable
private fun SectionTab(
    label: String,
    icon: ImageVector?,
    isSelected: Boolean,
    onSelected: () -> Unit,
    focusRequester: FocusRequester,
    previousTabFocusRequester: FocusRequester?,
    nextTabFocusRequester: FocusRequester,
    entryFocusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val scale = LocalUiScale.current
    var isFocused by remember { mutableStateOf(false) }
    val focusScale by animateFloatAsState(
        targetValue = if (isFocused) TvFocusTokens.focusedScaleSubtle else TvFocusTokens.defaultScale,
        animationSpec = tween(durationMillis = CinemaAnimation.focusDurationMs),
        label = "section_tab_focus_scale",
    )

    // Focus-follow-select, as in SeasonTab. Called from a LaunchedEffect, not from onFocusChanged, so the
    // state write happens after Compose's focus transfer, not in the middle of it.
    LaunchedEffect(isFocused) {
        if (isFocused) onSelected()
    }

    // P5: the selected tab keeps the resting look and is marked by a bottom accent bar plus
    // accent text; only focus fills the tab (tabChipFill, the one tab look).
    val textTone =
        when {
            isFocused -> TextTone.OnFocus
            isSelected -> TextTone.AccentSoft
            else -> TextTone.Secondary
        }

    Box(
        modifier =
            modifier
                .focusRequester(focusRequester)
                .then(entryFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .focusProperties {
                    previousTabFocusRequester?.let { left = it }
                    right = nextTabFocusRequester
                }.graphicsLayer {
                    scaleX = focusScale
                    scaleY = focusScale
                }.currentIndicator(
                    active = isSelected,
                    shape = CinemaTheme.shapes.chip,
                    edge = CurrentIndicatorEdge.Bottom,
                ).tabChipFill(isFocused)
                // No separate .focusable(): .clickable() below already creates a focus target.
                .onFocusChanged { isFocused = it.isFocused }
                .clickable { onSelected() }
                .padding(horizontal = Spacing.md.scaled(scale), vertical = Spacing.sm.scaled(scale)),
        contentAlignment = Alignment.Center,
    ) {
        val text: @Composable () -> Unit = {
            CinemaText(
                text = label,
                role = TvInputDefaults.tabLabelRole,
                tone = textTone,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (icon == null) {
            text()
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale)),
            ) {
                TvIcon(
                    imageVector = icon,
                    contentDescription = if (isFocused || isSelected) null else label,
                    tone = textTone,
                    modifier = Modifier.size(TvDimensions.iconMedium.scaled(scale)),
                )
                // The selected tab names itself too, so the row says which list shows.
                if (isFocused || isSelected) text()
            }
        }
    }
}
