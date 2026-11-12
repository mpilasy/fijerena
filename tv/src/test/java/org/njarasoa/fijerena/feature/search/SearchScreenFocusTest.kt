package org.njarasoa.fijerena.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.viewmodels.EpgBrowserViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SearchFacetChoice
import org.njarasoa.fijerena.core.ui.viewmodels.SearchFacets
import org.njarasoa.fijerena.core.ui.viewmodels.SearchTab
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel.SearchResult
import org.njarasoa.fijerena.feature.epgbrowser.EpgBrowserContent
import org.njarasoa.fijerena.testing.FocusWalk
import org.njarasoa.fijerena.testing.down
import org.njarasoa.fijerena.testing.right
import org.njarasoa.fijerena.testing.up
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.rail.LocalTvNavRail
import org.njarasoa.fijerena.ui.components.rail.RailItem
import org.njarasoa.fijerena.ui.components.rail.TvNavRail
import org.njarasoa.fijerena.ui.components.rail.TvNavRailState
import org.njarasoa.fijerena.ui.theme.FirstVideoPlayerTheme
import org.njarasoa.fijerena.ui.theme.Spacing
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TV Search's catalogue and Guide tab with the navigation rail, laid out as on the screen (header,
 * the Catalogue / People / Teams / Guide tabs, the field, the recent searches or the results).
 * Right from the rail goes back to the control Left was pressed on; with none (it left the
 * screen), it lands on the field, never on the recent searches' Clear all (rule 7). Up and Down
 * follow the screen from top to bottom: the tabs, the field, the results.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w960dp-h540dp")
class SearchScreenFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private val walk = FocusWalk(rule)
    private lateinit var rail: TvNavRailState

    // SearchScreen's catalogue.
    private fun setScreen(
        query: String,
        results: List<SearchResult>,
        history: List<String>,
    ) = setFrame(SearchTab.CATALOGUE) { fieldModifier ->
        SearchContent(
            query = query,
            fieldText = query,
            onFieldTextChange = {},
            holdFocus = false,
            fieldModifier = fieldModifier,
            categoryResults = emptyList(),
            results = results,
            notInSources = emptyList(),
            notInSourcesError = null,
            onTmdbTitleClick = {},
            onTmdbTitleActions = {},
            wishlistKeys = emptySet(),
            sources = emptyList(),
            selectedSource = null,
            onSourceChange = {},
            facets = SearchFacets(),
            facetChoice = SearchFacetChoice(),
            onFacetChoiceChange = {},
            selectedTypeFilter = null,
            onTypeFilterChange = {},
            excludedCountByType = emptyMap(),
            isSearching = false,
            searchProgress = null,
            devStats = null,
            sourceErrors = null,
            contentType = "ALL",
            child = false,
            searchHistory = history,
            returnFocus = rememberNavReturnFocus(),
            mayOpenKeyboard = true,
            onSearchSubmit = {},
            onHistoryItemClick = {},
            onHistoryItemRemove = {},
            onClearHistory = {},
            onClearSearch = {},
            onResultClick = {},
            onResultLongPress = {},
            onCategoryClick = {},
            onCategoryLongPress = {},
        )
    }

    // SearchScreen's layout around a tab's content (given the field's modifier), with the rail over
    // the left margin as the nav host draws it.
    private fun setFrame(
        tab: SearchTab,
        content: @Composable (fieldModifier: Modifier) -> Unit,
    ) {
        rail = TvNavRailState().apply { current = RailItem.SEARCH }
        rule.setContent {
            FirstVideoPlayerTheme {
                CompositionLocalProvider(LocalTvNavRail provides rail) {
                    Box(Modifier.fillMaxSize()) {
                        val tabs = listOf(SearchTab.CATALOGUE, SearchTab.PEOPLE, SearchTab.TEAMS, SearchTab.GUIDE)
                        val tabFocus = remember { SearchTab.entries.associateWith { FocusRequester() } }
                        val fieldFocus = remember { FocusRequester() }
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical),
                        ) {
                            TvScreenHeader(title = "Search", subtitle = "All")
                            SearchTabRow(tabs, tab, {}, tabFocus, fieldFocus)
                            Column(Modifier.weight(1f).fillMaxWidth()) {
                                content(
                                    Modifier
                                        .focusRequester(fieldFocus)
                                        .focusProperties { up = tabFocus.getValue(tab) },
                                )
                            }
                        }
                        TvNavRail(
                            state = rail,
                            items =
                                listOf(
                                    RailItem.PROFILE,
                                    RailItem.HOME,
                                    RailItem.LIVE_TV,
                                    RailItem.MOVIES,
                                    RailItem.TV_SHOWS,
                                    RailItem.SPORTS,
                                    RailItem.SEARCH,
                                    RailItem.SETTINGS,
                                ),
                            profileFallbackName = "A",
                            onSelect = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Right from the rail with no control to go back to lands on the field, not on Clear all`() {
        setScreen(query = "", results = emptyList(), history = listOf("dune", "alien"))
        walk.awaitFocused(FIELD)
        leftToRail()
        walk.run(right(FIELD))
        // The control the rail would go back to has left the screen (a result of an earlier search,
        // another tab's field): the rail moves Right instead.
        rule.runOnIdle {
            rail.returnTo = FocusRequester()
            rail.focusRail()
        }
        assertEquals(RAIL_SEARCH, walk.focusedText())
        walk.run(right(FIELD))
    }

    @Test
    fun `Right from the rail goes back to the result Left was pressed on`() {
        setScreen(query = "film", results = films(), history = listOf("dune"))
        walk.awaitFocused(FILM_1)
        walk.run(down("F / Film 2"))
        leftToRail()
        walk.run(right("F / Film 2"))
    }

    @Test
    fun `Up and Down follow the screen - the results, the field, the tab, and back`() {
        setScreen(query = "film", results = films(), history = listOf("dune"))
        walk.awaitFocused(FILM_1)
        walk.run(
            up(HEADER),
            up("film"),
            up("Catalogue"),
            down("film"),
            down(HEADER),
            down(FILM_1),
        )
    }

    @Test
    fun `Guide tab - Right from the rail with no control to go back to lands on the field, not on Clear all`() {
        setFrame(SearchTab.GUIDE) { fieldModifier ->
            EpgBrowserContent(
                uiState = EpgBrowserViewModel.UiState.Idle,
                nowEpoch = 0L,
                indexState = EpgIndexState.NotIndexed,
                nerd = false,
                epgDbStats = null,
                epgSearchHistory = listOf("news", "match"),
                onSearch = {},
                query = "",
                onQueryChange = {},
                fieldModifier = fieldModifier,
            )
        }
        walk.awaitFocused(GUIDE_FIELD)
        rule.runOnIdle {
            rail.returnTo = FocusRequester()
            rail.focusRail()
        }
        assertEquals(RAIL_SEARCH, walk.focusedText())
        walk.run(right(GUIDE_FIELD))
    }

    // The rail item alone: the field's placeholder starts with "Search" too.
    private fun leftToRail() {
        walk.press(Key.DirectionLeft)
        assertEquals(RAIL_SEARCH, walk.focusedText())
    }

    private fun films() = (1..5).map { SearchResult("$it", "Film $it", "c", "Cat", ContentType.MOVIES) }

    private companion object {
        const val FIELD = "Search films, shows and channels"
        const val GUIDE_FIELD = "Enter programme title"
        const val RAIL_SEARCH = "Search"
        const val FILM_1 = "F / Film 1"
        const val HEADER = "Films / 5 results"
    }
}
