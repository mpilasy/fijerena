package org.njarasoa.fijerena.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.njarasoa.fijerena.core.ui.navigation.SectionRoot
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

/**
 * The section-root button (D4, docs/plans/archive/20261003_sources-guide-profiles-plan.md → P6): back to
 * the section's first screen (Movies, Settings…), shown four or more screens above Home — the nav
 * host decides, see [org.njarasoa.fijerena.core.ui.navigation.sectionRootFor]. The last button of
 * a screen's header row, so it never moves the others; nothing when [sectionRoot] is null. Never
 * takes focus itself: it is reached like the other header buttons, Up from the content.
 */
@Composable
internal fun SectionRootButton(
    sectionRoot: SectionRoot?,
    modifier: Modifier = Modifier,
) {
    if (sectionRoot != null) {
        LabelledActionButton(
            onClick = sectionRoot.onClick,
            icon = CinemaIcons.VerticalAlignTop,
            label = sectionRoot.label,
            modifier = modifier,
        )
    }
}
