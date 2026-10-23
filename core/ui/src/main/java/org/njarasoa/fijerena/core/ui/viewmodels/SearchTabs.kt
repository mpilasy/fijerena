package org.njarasoa.fijerena.core.ui.viewmodels

import org.njarasoa.fijerena.core.player.domain.ContentType

// Search's Catalogue | Guide tabs, the same rules on TV and phone
// (the search guide tab plan).

/** Search's two tabs (D1): the catalogue, or programme titles in the guide. */
enum class SearchTab { CATALOGUE, GUIDE }

/** The Guide tab shows when Search covers live channels and a source of the profile has some (O1). */
fun showsGuideTab(
    contentType: String,
    hasGuide: Boolean,
): Boolean = hasGuide && (contentType == SearchViewModel.CONTENT_TYPE_ALL || contentType == ContentType.LIVE_TV)

/**
 * The query to run on the tab just switched to: the field's text, when that tab last ran another
 * query ([lastRunQuery], null when none); null when there is nothing to run (D3).
 */
fun queryToRunOnTabSwitch(
    text: String,
    lastRunQuery: String?,
): String? = text.takeIf { it.isNotBlank() && it.trim() != lastRunQuery?.trim() }

/** The query the guide's view model last ran (or holds until its index is ready), if any. */
fun EpgBrowserViewModel.UiState.lastRunQuery(): String? =
    when (this) {
        is EpgBrowserViewModel.UiState.Results -> query
        is EpgBrowserViewModel.UiState.IndexBusy -> query
        else -> null
    }
