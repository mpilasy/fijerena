package org.njarasoa.fijerena.core.ui.viewmodels

import org.njarasoa.fijerena.core.player.domain.ContentType

// Search's Catalogue | People | Teams | Guide types, the same rules on TV and phone
// (the search guide tab plan, the people search plan, the sports teams plan).

/**
 * Search's types, in the order they show: the catalogue, TMDB's people, films and shows (PS-D1),
 * sports teams and leagues (SP-D2), or programme titles in the guide (D1).
 */
enum class SearchTab { CATALOGUE, PEOPLE, TEAMS, GUIDE }

/** The Guide tab shows when Search covers live channels and a source of the profile has some (O1). */
fun showsGuideTab(
    contentType: String,
    hasGuide: Boolean,
): Boolean = hasGuide && (contentType == SearchViewModel.CONTENT_TYPE_ALL || contentType == ContentType.LIVE_TV)

/**
 * The People type shows only with a TMDB key (PS-D1), never on a child profile (user,
 * 2026-10-31), and not over live channels alone: TMDB knows no channel.
 */
fun showsPeopleTab(
    contentType: String,
    hasTmdbKey: Boolean,
    child: Boolean = false,
): Boolean = hasTmdbKey && !child && contentType != ContentType.LIVE_TV

/**
 * The Teams type shows while Admin's Sports switch is on (SP-D9), never on a child profile (SP-D8),
 * and only where Search covers live channels (everything, or Live TV): games are watched on live
 * channels, so a search of films or shows alone has no use for it.
 */
fun showsTeamsTab(
    contentType: String,
    sportsOn: Boolean,
    child: Boolean = false,
): Boolean = sportsOn && !child && (contentType == SearchViewModel.CONTENT_TYPE_ALL || contentType == ContentType.LIVE_TV)

/**
 * The types the Search screen offers, in order; empty when only the catalogue is left (no type
 * selector then). [child]: a child profile, which never gets the People or Teams type.
 * [guideAllowed] is false where the Guide type never shows (a child profile on TV). [sportsOn]:
 * Admin's Sports switch.
 */
fun searchTabs(
    contentType: String,
    hasGuide: Boolean,
    hasTmdbKey: Boolean,
    child: Boolean = false,
    guideAllowed: Boolean = true,
    sportsOn: Boolean = false,
): List<SearchTab> {
    val tabs =
        listOfNotNull(
            SearchTab.CATALOGUE,
            SearchTab.PEOPLE.takeIf { showsPeopleTab(contentType, hasTmdbKey, child) },
            SearchTab.TEAMS.takeIf { showsTeamsTab(contentType, sportsOn, child) },
            SearchTab.GUIDE.takeIf { guideAllowed && showsGuideTab(contentType, hasGuide) },
        )
    return if (tabs.size > 1) tabs else emptyList()
}

/** The type shown: the one chosen while it is offered, else the catalogue. */
fun activeSearchTab(
    selected: SearchTab,
    tabs: List<SearchTab>,
): SearchTab = selected.takeIf { it in tabs } ?: SearchTab.CATALOGUE

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
