package org.njarasoa.fijerena.navigation

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.home.BrowseSource
import org.njarasoa.fijerena.ui.components.rail.RailItem
import org.njarasoa.fijerena.ui.components.rail.TvNavRail
import org.njarasoa.fijerena.ui.components.rail.TvNavRailState
import org.robolectric.RobolectricTestRunner

/**
 * A Jellyfin or M3U source's "Browse <source>" entry (docs/plans/20261012_multi-source-search-history-plan.md
 * → O1): on the rail below the sections, it calls back with its source; with several sections, a
 * dialog opens on the first and picks one.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class BrowseEntryTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val jellyfin = BrowseSource(providerId = 9, name = "Jelly")

    @Test
    fun `the rail's Browse entry opens its own source`() {
        val browsed = mutableListOf<BrowseSource>()
        composeTestRule.setContent {
            TvNavRail(
                state = TvNavRailState(),
                items = railItems(setOf(ContentType.LIVE_TV)),
                profileInitial = "A",
                onSelect = {},
                browse = listOf(jellyfin),
                onBrowse = { browsed += it },
            )
        }

        composeTestRule.onNodeWithContentDescription("Browse Jelly").requestFocus()
        composeTestRule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()

        assertEquals(listOf(jellyfin), browsed)
    }

    @Test
    fun `the sections dialog opens on the first section, OK picks it`() {
        val picked = mutableListOf<String>()
        composeTestRule.setContent {
            BrowseSectionsDialog(
                source = jellyfin,
                contentTypes = listOf(ContentType.MOVIES, ContentType.TV_SHOWS),
                onPick = { picked += it },
                onDismiss = {},
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Movies").assertIsFocused()
        composeTestRule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()

        assertEquals(listOf(ContentType.MOVIES), picked)
    }

    @Test
    fun `Browse entries are not rail items`() {
        assertEquals(
            listOf(RailItem.PROFILE, RailItem.HOME, RailItem.LIVE_TV, RailItem.SEARCH, RailItem.SETTINGS),
            railItems(setOf(ContentType.LIVE_TV)),
        )
    }
}
