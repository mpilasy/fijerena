package org.njarasoa.fijerena.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Text
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.ui.components.rail.RailItem
import org.robolectric.RobolectricTestRunner

/**
 * The rail's picks on a real back stack: Search and Settings are never a section's place, so a
 * pick never comes back to them (TV walk C2: the first pick after Search opened Settings or
 * Search again).
 */
@RunWith(RobolectricTestRunner::class)
class RailPickTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var nav: NavHostController

    @Before
    fun setUp() {
        rule.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = Screen.ContentTypeSelection) {
                composable<Screen.ContentTypeSelection> { Text("Home") }
                composable<Screen.LiveTvTab> { Text("Live TV") }
                composable<Screen.MoviesTab> { Text("Films") }
                composable<Screen.Search> { Text("Search") }
                composable<Screen.Settings> { Text("Settings") }
            }
        }
        rule.waitForIdle()
    }

    /** The item the rail lights for the back stack, as TvSectionRail sets it. */
    private fun current(): RailItem = railCurrent(nav.currentDestination!!.topScreen(), nav.currentSection())

    private fun pick(item: RailItem) {
        rule.runOnUiThread { nav.selectRailItem(item, current()) { nav.showSection(it) } }
        rule.waitForIdle()
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private inline fun <reified T : Any> assertOnTop() {
        val top = nav.currentDestination!!
        assertTrue("on top: ${top.route}", top.hasRoute<T>())
    }

    @Test
    fun `Search from the rail, Back, then Films opens Films`() {
        pick(RailItem.SEARCH)
        assertOnTop<Screen.Search>()
        back()
        assertOnTop<Screen.ContentTypeSelection>()

        pick(RailItem.MOVIES)

        assertOnTop<Screen.MoviesTab>()
    }

    @Test
    fun `Search opened over Settings, then the section's item goes back to the section, not Settings`() {
        pick(RailItem.LIVE_TV)
        pick(RailItem.SETTINGS)
        pick(RailItem.SEARCH)

        pick(RailItem.LIVE_TV)

        assertOnTop<Screen.LiveTvTab>()
    }

    @Test
    fun `a section left from Search comes back without Search`() {
        pick(RailItem.LIVE_TV)
        pick(RailItem.SETTINGS)
        pick(RailItem.SEARCH)
        pick(RailItem.MOVIES)
        assertOnTop<Screen.MoviesTab>()

        pick(RailItem.LIVE_TV)

        assertOnTop<Screen.LiveTvTab>()
    }

    @Test
    fun `Home from Search over a section, then the section comes back without Search`() {
        pick(RailItem.MOVIES)
        pick(RailItem.SEARCH)
        pick(RailItem.HOME)
        assertOnTop<Screen.ContentTypeSelection>()

        pick(RailItem.MOVIES)

        assertOnTop<Screen.MoviesTab>()
    }

    @Test
    fun `the section's item from Search over it goes back to the section`() {
        pick(RailItem.MOVIES)
        pick(RailItem.SEARCH)

        pick(RailItem.MOVIES)

        assertOnTop<Screen.MoviesTab>()
    }
}
