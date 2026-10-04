package org.njarasoa.fijerena.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import org.njarasoa.fijerena.core.ui.navigation.SectionRoot
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

/**
 * The section-root button (D4, docs/plans/20261003_sources-guide-profiles-plan.md → P6) as a
 * top-bar action, the bar's last: back to the section's first screen (Movies, Settings…), shown
 * four or more screens above Home — the nav host decides, see
 * [org.njarasoa.fijerena.core.ui.navigation.sectionRootFor]. Its content description is the
 * destination's name. Nothing when [sectionRoot] is null.
 */
@Composable
internal fun SectionRootAction(sectionRoot: SectionRoot?) {
    if (sectionRoot != null) {
        IconButton(onClick = sectionRoot.onClick) {
            Icon(CinemaIcons.VerticalAlignTop, sectionRoot.label)
        }
    }
}
