package org.njarasoa.fijerena.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary

/**
 * One row of section-tab pills (Cast / Details / Similar / ...), TV detail screens' equivalent of
 * [EpisodeSelectionScreen][org.njarasoa.fijerena.feature.episode.EpisodeSelectionScreen]'s season
 * tabs — same focus/selection shape, generalised to plain labels instead of `SeasonInfo`. See
 * docs/plans/tv-detail-hero-ui-plan.md Phase 4.
 *
 * D-pad left/right moves focus between tabs via Compose's default focus search inside this Row —
 * each tab selects as soon as it receives focus, so movement alone switches the visible section.
 *
 * [entryFocusRequester], if given, is attached to whichever tab is currently selected — the same
 * requester a caller uses both to send D-pad Down from above into this row (landing on the
 * selected tab, not whichever one default geometry search prefers) and, later, to send Back from
 * inside the open section back to this row rather than out of the screen.
 */
@Composable
fun TvSectionTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    entryFocusRequester: FocusRequester? = null,
) {
    val scale = LocalUiScale.current
    // One FocusRequester per tab, explicitly wired to its left/right neighbor below — Compose's
    // default geometry-based focus search proved unreliable for this exact row shape on real
    // hardware (see SeasonTabs in EpisodeSelectionScreen.kt for the original finding); explicit
    // wiring makes it deterministic here too.
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
        tabs.forEachIndexed { index, label ->
            SectionTab(
                label = label,
                isSelected = index == selectedIndex,
                onSelected = { onTabSelected(index) },
                focusRequester = focusRequesters[index],
                previousTabFocusRequester = focusRequesters.getOrNull(index - 1),
                nextTabFocusRequester = focusRequesters.getOrNull(index + 1),
                // Second requester on the same node, alongside `focusRequester` above — only the
                // selected tab gets it, and it moves with selection as `selectedIndex` changes.
                entryFocusRequester = if (index == selectedIndex) entryFocusRequester else null,
            )
        }
    }
}

@Composable
private fun SectionTab(
    label: String,
    isSelected: Boolean,
    onSelected: () -> Unit,
    focusRequester: FocusRequester,
    previousTabFocusRequester: FocusRequester?,
    nextTabFocusRequester: FocusRequester?,
    entryFocusRequester: FocusRequester?,
) {
    val scale = LocalUiScale.current
    var isFocused by remember { mutableStateOf(false) }
    val focusScale by animateFloatAsState(
        targetValue = if (isFocused) TvFocusTokens.focusedScaleSubtle else TvFocusTokens.defaultScale,
        animationSpec = tween(durationMillis = CinemaAnimation.focusDurationMs),
        label = "section_tab_focus_scale",
    )

    // Focus-follow-select, not a separate OK press — same rationale as SeasonTab: a tab row this
    // immediate reads better than requiring an extra confirm. Deferred to LaunchedEffect rather
    // than called straight from onFocusChanged, so the state write happens after Compose's own
    // focus-transfer transaction settles, not in the middle of it.
    LaunchedEffect(isFocused) {
        if (isFocused) onSelected()
    }

    val containerColor =
        when {
            isFocused && isSelected -> TvFocusTokens.focusedSelectedContainer
            isFocused -> TvFocusTokens.focusedContainer
            isSelected -> TvFocusTokens.selectedContainer
            else -> Color.Transparent
        }

    val textColor =
        when {
            isFocused && isSelected -> CinemaAccentLight
            isFocused -> CinemaTextPrimary
            isSelected -> CinemaAccent
            else -> CinemaTextSecondary
        }

    Box(
        modifier =
            Modifier
                .focusRequester(focusRequester)
                .then(entryFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .focusProperties {
                    previousTabFocusRequester?.let { left = it }
                    nextTabFocusRequester?.let { right = it }
                }
                .graphicsLayer {
                    scaleX = focusScale
                    scaleY = focusScale
                }
                .background(
                    color = containerColor,
                    shape = RoundedCornerShape(CornerRadius.medium),
                )
                .then(
                    if (isFocused) {
                        Modifier.border(
                            width = TvFocusTokens.focusBorderWidth,
                            color = CinemaAccentLight,
                            shape = RoundedCornerShape(CornerRadius.medium),
                        )
                    } else if (isSelected) {
                        Modifier.border(
                            width = TvFocusTokens.borderThin,
                            color = CinemaAccent.copy(alpha = CinemaAlpha.glassBorder),
                            shape = RoundedCornerShape(CornerRadius.medium),
                        )
                    } else {
                        Modifier
                    },
                )
                // No separate .focusable(): .clickable() below already creates a focus target.
                .onFocusChanged { isFocused = it.isFocused }
                .clickable { onSelected() }
                .padding(horizontal = Spacing.md.scaled(scale), vertical = Spacing.sm.scaled(scale)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale),
                ),
            color = textColor,
        )
    }
}
