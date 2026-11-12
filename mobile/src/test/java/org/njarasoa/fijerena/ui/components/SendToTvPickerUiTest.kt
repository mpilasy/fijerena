package org.njarasoa.fijerena.ui.components

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.ui.viewmodels.SendItem
import org.njarasoa.fijerena.core.ui.viewmodels.SendToTvSheet
import org.njarasoa.fijerena.core.ui.viewmodels.TvRow
import org.njarasoa.fijerena.core.ui.viewmodels.TvStatus
import org.robolectric.RobolectricTestRunner

/** The picker sheet, and what a list row sends (the send to TV plan, ST-D12). */
@RunWith(RobolectricTestRunner::class)
class SendToTvPickerUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val item = SendItem(1L, "42", "Malcolm X", "c1", ContentType.MOVIES)

    private fun show(rows: List<TvRow>): MutableList<TvRow> {
        val picked = mutableListOf<TvRow>()
        rule.setContent { SendToTvPicker(SendToTvSheet(rows, item, null), onPick = { picked += it }, onDismiss = {}) }
        return picked
    }

    @Test
    fun `an open TV can be picked, a closed one shows why and cannot be`() {
        val open = TvRow("a", "Living Room", TvStatus.OPEN, playing = "Malcolm X")
        val closed = TvRow("b", "Bedroom", TvStatus.NOT_OPEN)
        val picked = show(listOf(open, closed))

        rule.onNodeWithText("Living Room").assertExists()
        rule.onNodeWithText("Open · Malcolm X").assertExists()
        rule.onNodeWithText("Not open · Open Fijerena on the TV").assertExists()

        rule.onNodeWithText("Living Room").performClick()
        assertEquals(listOf(open), picked)

        rule.onNode(hasText("Bedroom", substring = true) and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun `a TV that needs an update says so`() {
        show(listOf(TvRow("b", "Bedroom", TvStatus.NEEDS_UPDATE)))

        rule.onNodeWithText("Needs an update").assertExists()
    }

    @Test
    fun `no TV says so`() {
        show(emptyList())

        rule.onNodeWithText("No TV in this sync group").assertExists()
    }

    @Test
    fun `a film or channel row sends, a whole show and a category do not`() {
        val movie = MediaItem(id = "42", name = "A", mediaType = MediaType.MOVIE, categoryId = "c")
        assertEquals("42", movie.sendItem(ContentType.MOVIES, 3L)?.streamId)
        assertEquals(3L, movie.sendItem(ContentType.MOVIES, 3L)?.providerId)

        val show = MediaItem(id = "7", name = "S", mediaType = MediaType.SERIES, categoryId = "c")
        assertNull(show.sendItem(ContentType.TV_SHOWS, 3L))

        val ref = movie.copy(providerData = mapOf("isCategoryRef" to "true"))
        assertNull(ref.sendItem(ContentType.MOVIES, 3L))
    }
}
