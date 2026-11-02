package org.njarasoa.fijerena.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.home.BrowseSource
import org.njarasoa.fijerena.core.ui.home.browseSectionAction
import org.njarasoa.fijerena.ui.components.TvActionMenu

/**
 * A "Browse <source>" entry's sections, when it has more than one (docs/plans/20261012_multi-source-search-history-plan.md
 * → O1): an action menu ([TvActionMenu]) with one row per section, focus on the first; OK opens it
 * on that source, Back or Cancel closes the menu.
 */
@Composable
internal fun BrowseSectionsDialog(
    source: BrowseSource,
    contentTypes: List<String>,
    onPick: (contentType: String) -> Unit,
    onDismiss: () -> Unit,
) {
    TvActionMenu(
        title = stringResource(R.string.browse_source, source.name),
        actions = contentTypes.map { contentType -> browseSectionAction(contentType) { onPick(contentType) } },
        onDismiss = onDismiss,
    )
}
