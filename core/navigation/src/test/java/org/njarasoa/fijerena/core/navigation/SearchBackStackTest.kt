package org.njarasoa.fijerena.core.navigation

import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.toRoute
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** There is only ever one Search on the back stack: Back never returns to a replaced query. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchBackStackTest {
    private fun navController(): TestNavHostController {
        val nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        val graph =
            nav.createGraph(startDestination = Screen.ContentTypeSelection) {
                composable<Screen.ContentTypeSelection> {}
                composable<Screen.Search> {}
                composable<Screen.MovieDetails> {}
            }
        nav.graph = graph
        return nav
    }

    @Test
    fun `a new search replaces the old one and what it opened`() {
        val nav = navController()
        // navigateToSearch's steps (its RESUMED guard aside: a test controller's screens never are).
        nav.dropSearches()
        nav.navigate(Screen.Search("ALL", initialQuery = "matrix"))
        nav.navigate(Screen.MovieDetails("1", "The Matrix", "c", providerId = 1L))

        nav.dropSearches()
        nav.navigate(Screen.Search("ALL", initialQuery = "godfather"))

        val searches = nav.currentBackStack.value.filter { it.destination.hasRoute<Screen.Search>() }
        assertEquals(listOf("godfather"), searches.map { it.toRoute<Screen.Search>().initialQuery })
        nav.popBackStack()
        assertEquals(true, nav.currentDestination?.hasRoute<Screen.ContentTypeSelection>())
    }
}
