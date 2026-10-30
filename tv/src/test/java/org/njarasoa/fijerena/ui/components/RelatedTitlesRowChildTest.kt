package org.njarasoa.fijerena.ui.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.ui.theme.FirstVideoPlayerTheme
import org.robolectric.RobolectricTestRunner

/** UX-D5: a More Like This card shows its language tag to an adult, and none to a child. */
@RunWith(RobolectricTestRunner::class)
class RelatedTitlesRowChildTest {
    @get:Rule
    val rule =
        androidx.compose.ui.test.junit4
            .createComposeRule()

    private val settings = AppSettings(ApplicationProvider.getApplicationContext())
    private val profile = "related-row-test"
    private val greekTitle = MediaItem(id = "1", name = "GR - The Lion King", mediaType = MediaType.MOVIE, categoryId = "1")

    @After
    fun tearDown() {
        settings.setChildProfile(profile, false)
    }

    private fun showRow(child: Boolean) {
        settings.activeProfileId = profile
        settings.setChildProfile(profile, child)
        rule.setContent { FirstVideoPlayerTheme { RelatedTitlesRow(title = null, items = listOf(greekTitle), onItemClick = {}) } }
    }

    @Test
    fun anAdultSeesTheLanguageTag() {
        showRow(child = false)

        rule.onAllNodesWithText("GR").assertCountEquals(1)
        rule.onAllNodesWithText("The Lion King").assertCountEquals(1)
    }

    @Test
    fun aChildSeesNoLanguageTag() {
        showRow(child = true)

        rule.onAllNodesWithText("GR").assertCountEquals(0)
        rule.onAllNodesWithText("The Lion King").assertCountEquals(1)
    }
}
