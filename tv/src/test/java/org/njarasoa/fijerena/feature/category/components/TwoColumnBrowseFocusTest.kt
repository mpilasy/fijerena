package org.njarasoa.fijerena.feature.category.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.ui.components.ImmutableCategoryList
import org.njarasoa.fijerena.core.ui.components.ImmutableMediaList
import org.njarasoa.fijerena.core.ui.components.ImmutableNowPlaying
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModelFactory
import org.njarasoa.fijerena.testing.FocusWalk
import org.njarasoa.fijerena.testing.SettingsFixture
import org.njarasoa.fijerena.testing.back
import org.njarasoa.fijerena.testing.center
import org.njarasoa.fijerena.testing.down
import org.njarasoa.fijerena.testing.left
import org.njarasoa.fijerena.testing.right
import org.njarasoa.fijerena.testing.up
import org.robolectric.RobolectricTestRunner

/**
 * The two-pane browse (`TwoColumnLayout`: categories, then the selected category's rows) as
 * `scripts/focus-walks/live-tv-browse.txt` and `live-tv-back.txt` walk it: which row a list opens
 * on, Left and Right between the panes, OK on a category, and where focus lands on Back from the
 * Live TV preview or a film's details. See docs/plans/20260914_codebase-robustness-plan.md, Phase 5.
 * The tab row over the items (the picked category, Recent, Favourites) follows the D-pad map of
 * docs/plans/20261027_tv-browse-tabs-plan.md. A category's tab and its row share its name: a step
 * after landing on one tells them apart.
 *
 * The test holds the selection and the rows, as `TvCategoryGridScreen` does with its view model's
 * state, and stands a focusable screen in for the preview or the details, under the same saveable
 * state the screen keeps for the browse layout. The layout's own `CategoryViewModel` is built on a
 * profile with no sources: it lists nothing and reaches no network, and its per-row reads (stars,
 * catch-up days) stay empty.
 */
@RunWith(RobolectricTestRunner::class)
class TwoColumnBrowseFocusTest {
    // The v2 rule: a standard (queued) dispatcher, as on a device: see SettingsFocusTest.
    @get:Rule
    val rule = createComposeRule()

    private val walk = FocusWalk(rule)

    private val viewModels = ViewModelStore()

    @After
    fun tearDown() {
        viewModels.clear()
        SettingsFixture.reset()
    }

    @Test
    fun `a category with channels opens on its first channel`() {
        open(ContentType.LIVE_TV, NEWS)
        walk.awaitFocused(MORNING_NEWS)
    }

    @Test
    fun `an empty Recent opens on its button, which goes to the categories`() {
        open(ContentType.LIVE_TV, CategoryViewModel.RECENT_CATEGORY_ID)
        // The empty state's one action takes focus (household UX plan, K2).
        walk.awaitFocused(BROWSE_CATEGORIES)
        walk.run(
            // Up reaches the tabs: Recent, no category picked yet.
            up(RECENT),
            down(BROWSE_CATEGORIES),
            // OK lands on the first category, as none was picked.
            center(NEWS_NAME),
            down(SPORT_NAME),
        )
    }

    @Test
    fun `Up from the first channel reaches the tabs, then the header, and Down comes back`() {
        open(ContentType.LIVE_TV, NEWS)
        walk.awaitFocused(MORNING_NEWS)
        walk.run(
            // The category's tab, the selected one.
            up(NEWS_NAME),
            up(REFRESH),
            // Nothing is above the header.
            up(REFRESH),
            down(NEWS_NAME),
            down(MORNING_NEWS),
        )
    }

    @Test
    fun `focus on a tab opens its list and stays on the tab`() {
        open(ContentType.LIVE_TV, NEWS)
        walk.awaitFocused(MORNING_NEWS)
        walk.run(
            up(NEWS_NAME),
            // An empty Recent: focus stays on its tab, not on the empty state's button.
            right(RECENT),
            // Favourites has rows: none of them takes focus.
            right(FAVOURITES),
            // The last tab.
            right(FAVOURITES),
            down(CARTOON_TIME),
            up(FAVOURITES),
            left(RECENT),
            down(BROWSE_CATEGORIES),
            up(RECENT),
            left(NEWS_NAME),
            // Left on the first tab: the picked category in its pane, whose Right enters the list.
            left(NEWS_NAME),
            right(MORNING_NEWS),
        )
    }

    @Test
    fun `Left from a virtual list lands on the picked category`() {
        open(ContentType.LIVE_TV, NEWS)
        walk.awaitFocused(MORNING_NEWS)
        walk.run(
            up(NEWS_NAME),
            right(RECENT),
            right(FAVOURITES),
            down(CARTOON_TIME),
            left(NEWS_NAME),
            // The row, not the tab: Down moves in the categories.
            down(SPORT_NAME),
            // Right comes back to the row the list had focus on.
            right(CARTOON_TIME),
        )
    }

    @Test
    fun `Down from the header enters the tabs, then the row the list remembers`() {
        open(ContentType.LIVE_TV, NEWS)
        walk.awaitFocused(MORNING_NEWS)
        walk.run(
            down(WORLD_REPORT),
            left(NEWS_NAME),
            // News is the first category: Up leaves its pane for the header.
            up(REFRESH),
            down(NEWS_NAME),
            down(WORLD_REPORT),
        )
    }

    @Test
    fun `Left from a channel lands on the category being browsed, not the one passed last`() {
        open(ContentType.LIVE_TV, NEWS)
        walk.awaitFocused(MORNING_NEWS)
        walk.run(
            down(WORLD_REPORT),
            left(NEWS_NAME),
            // Down in the categories only moves; nothing is picked.
            down(SPORT_NAME),
            down(KIDS_NAME),
            // Right comes back to the row the list had focus on.
            right(WORLD_REPORT),
            left(NEWS_NAME),
        )
    }

    @Test
    fun `OK on a category keeps focus there, Right enters its first channel`() {
        open(ContentType.LIVE_TV, NEWS)
        walk.awaitFocused(MORNING_NEWS)
        walk.run(
            down(WORLD_REPORT),
            left(NEWS_NAME),
            down(SPORT_NAME),
            // The list switches under the categories; focus stays on the category picked.
            center(SPORT_NAME),
            // The row remembered from News is not in Sport: the first row.
            right(MATCH_DAY),
            // The category's tab takes the name of the one picked.
            up(SPORT_NAME),
            right(RECENT),
            left(SPORT_NAME),
            down(MATCH_DAY),
            left(SPORT_NAME),
        )
    }

    @Test
    fun `Back from the preview lands on the channel it was playing`() {
        // The preview zapped from World Report to Evening Bulletin, the next channel of the list.
        open(ContentType.LIVE_TV, NEWS, playingOnBack = { EVENING_BULLETIN_ID })
        walk.awaitFocused(MORNING_NEWS)
        walk.run(
            down(WORLD_REPORT),
            center(OPENED),
            // Not World Report, the row that opened the preview and the one the pane remembers.
            back(EVENING_BULLETIN),
        )
    }

    @Test
    fun `Back from the preview on a channel this list lacks lands on the category`() {
        // The preview zapped to a channel of another list (Recent, Favourites).
        open(ContentType.LIVE_TV, NEWS, playingOnBack = { MATCH_DAY_ID })
        walk.awaitFocused(MORNING_NEWS)
        walk.run(
            down(WORLD_REPORT),
            center(OPENED),
            back(NEWS_NAME),
            // The list still enters on the row it had focus on.
            right(WORLD_REPORT),
        )
    }

    @Test
    fun `Back from a film's details lands on the film`() {
        open(ContentType.MOVIES, DRAMA)
        walk.awaitFocused(AMELIE)
        walk.run(
            down(METROPOLIS),
            center(OPENED),
            back(METROPOLIS),
            down(CASABLANCA),
        )
    }

    /**
     * Shows the browse on [contentType] with [category] selected. OK on a row opens a stand-in for
     * the preview or the details, and Back there comes back to the browse; on Live TV, with the
     * channel [playingOnBack] gives for the one opened (the preview can zap), as the screen hands
     * it back (LT6).
     */
    private fun open(
        contentType: String,
        category: String,
        playingOnBack: (opened: String) -> String = { it },
    ) {
        val viewModel =
            ViewModelProvider(
                viewModels,
                CategoryViewModelFactory(ApplicationProvider.getApplicationContext(), contentType),
            )[CategoryViewModel::class.java]
        val categories = ImmutableCategoryList(VIRTUAL + if (contentType == ContentType.LIVE_TV) LIVE else FILMS)
        rule.setContent {
            val browseState = rememberSaveableStateHolder()
            var selectedId by remember { mutableStateOf(category) }
            var openedId by remember { mutableStateOf<String?>(null) }
            var returnedPlayingId by remember { mutableStateOf<String?>(null) }
            val opened = openedId
            if (opened != null) {
                OpenedScreen(
                    onBack = {
                        if (contentType == ContentType.LIVE_TV) returnedPlayingId = playingOnBack(opened)
                        openedId = null
                    },
                )
            } else {
                val selected = categories.first { it.id == selectedId }
                browseState.SaveableStateProvider(BROWSE_STATE_KEY) {
                    TwoColumnLayout(
                        categoryViewModel = viewModel,
                        categories = categories,
                        selectedCategoryId = selected.id,
                        selectedCategoryKey = selected.key,
                        streams = ImmutableMediaList(ROWS[selected.id].orEmpty()),
                        streamsLoading = false,
                        categoriesRefreshing = false,
                        lastPlayedItemId = null,
                        nowPlaying = ImmutableNowPlaying(),
                        contentType = contentType,
                        returnedPlayingId = returnedPlayingId,
                        supportsNativeEpg = false,
                        epgIndexState = EpgIndexState.NotIndexed,
                        onCategorySelected = { categoryId, _ -> selectedId = categoryId },
                        onStreamSelected = { streamId, _, _, _, _ -> openedId = streamId },
                        onRefreshCategories = {},
                        onRefreshStreams = {},
                        onEpgClick = { _, _, _ -> },
                        onBack = {},
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    /** The preview or the details: it takes focus, and Back leaves it. */
    @Composable
    private fun OpenedScreen(onBack: () -> Unit) {
        val focus = remember { FocusRequester() }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = OPENED }
                    .onKeyEvent { event ->
                        val isBack = event.key == Key.Back
                        if (isBack && event.type == KeyEventType.KeyUp) onBack()
                        isBack
                    }.focusRequester(focus)
                    .focusable(),
        )
        LaunchedEffect(Unit) { focus.requestFocus() }
    }

    private companion object {
        const val BROWSE_STATE_KEY = "browse"
        const val OPENED = "Opened screen"

        const val SOURCE = 1L

        const val NEWS = "news"
        const val SPORT = "sport"
        const val KIDS = "kids"
        const val DRAMA = "drama"

        const val RECENT = "Recent"
        const val FAVOURITES = "Favourites"
        const val REFRESH = "Refresh"
        const val CARTOON_TIME = "Cartoon Time"
        const val BROWSE_CATEGORIES = "Browse categories"
        const val NEWS_NAME = "News"
        const val SPORT_NAME = "Sport"
        const val KIDS_NAME = "Kids"

        const val MORNING_NEWS = "Morning News"
        const val WORLD_REPORT = "World Report"
        const val EVENING_BULLETIN = "Evening Bulletin"
        const val EVENING_BULLETIN_ID = "103"
        const val MATCH_DAY = "Match Day"
        const val MATCH_DAY_ID = "201"

        const val AMELIE = "Amelie"
        const val METROPOLIS = "Metropolis"
        const val CASABLANCA = "Casablanca"

        val VIRTUAL =
            listOf(
                MediaCategory(id = CategoryViewModel.RECENT_CATEGORY_ID, name = RECENT, isVirtual = true),
                MediaCategory(id = CategoryViewModel.FAVORITES_CATEGORY_ID, name = FAVOURITES, isVirtual = true),
            )
        val LIVE =
            listOf(
                MediaCategory(id = NEWS, name = NEWS_NAME, providerId = SOURCE),
                MediaCategory(id = SPORT, name = SPORT_NAME, providerId = SOURCE),
                MediaCategory(id = KIDS, name = KIDS_NAME, providerId = SOURCE),
            )
        val FILMS = listOf(MediaCategory(id = DRAMA, name = "Drama", providerId = SOURCE))

        val ROWS: Map<String, List<MediaItem>> =
            mapOf(
                NEWS to
                    listOf(
                        channel("101", MORNING_NEWS, NEWS),
                        channel("102", WORLD_REPORT, NEWS),
                        channel(EVENING_BULLETIN_ID, EVENING_BULLETIN, NEWS),
                    ),
                SPORT to listOf(channel(MATCH_DAY_ID, MATCH_DAY, SPORT), channel("202", "Racing Live", SPORT)),
                KIDS to listOf(channel("301", CARTOON_TIME, KIDS)),
                CategoryViewModel.FAVORITES_CATEGORY_ID to listOf(channel("301", CARTOON_TIME, KIDS)),
                DRAMA to listOf(film("401", AMELIE), film("402", METROPOLIS), film("403", CASABLANCA)),
            )

        fun channel(
            id: String,
            name: String,
            categoryId: String,
        ) = MediaItem(id = id, name = name, mediaType = MediaType.LIVE_CHANNEL, categoryId = categoryId, providerId = SOURCE)

        fun film(
            id: String,
            name: String,
        ) = MediaItem(id = id, name = name, mediaType = MediaType.MOVIE, categoryId = DRAMA, providerId = SOURCE)
    }
}
