package org.njarasoa.fijerena.feature.category

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel

class DockPanelTest {
    private fun channel(
        id: String,
        providerId: Long = 0L,
    ) = MediaItem(id = id, name = "Channel $id", mediaType = MediaType.LIVE_CHANNEL, categoryId = "news", providerId = providerId)

    @Test
    fun `a pick from a category docks with that category`() {
        assertEquals("7/news", dockCategoryKey("news", "7/news"))
    }

    @Test
    fun `a pick from Recent or Favourites docks with no category`() {
        assertNull(dockCategoryKey(CategoryViewModel.RECENT_CATEGORY_ID, CategoryViewModel.RECENT_CATEGORY_ID))
        assertNull(dockCategoryKey(CategoryViewModel.FAVORITES_CATEGORY_ID, CategoryViewModel.FAVORITES_CATEGORY_ID))
        assertNull(dockCategoryKey(null, null))
    }

    @Test
    fun `the category is the third of three tabs, else Recent and Favourites`() {
        assertEquals(
            listOf(PreviewListSource.RECENT, PreviewListSource.FAVORITES, PreviewListSource.CATEGORY),
            dockTabs(hasCategory = true),
        )
        assertEquals(listOf(PreviewListSource.RECENT, PreviewListSource.FAVORITES), dockTabs(hasCategory = false))
    }

    @Test
    fun `the category tab falls back to Recent when there is no category`() {
        assertEquals(PreviewListSource.RECENT, dockListSource(PreviewListSource.CATEGORY, hasCategory = false))
        assertEquals(PreviewListSource.CATEGORY, dockListSource(PreviewListSource.CATEGORY, hasCategory = true))
        assertEquals(PreviewListSource.FAVORITES, dockListSource(PreviewListSource.FAVORITES, hasCategory = false))
    }

    @Test
    fun `the mark follows the loader after a zap in full screen`() {
        val key = dockPlayingKey(channel("1"), resolvedStreamId = "2", loaderSource = 7L)
        assertTrue(isPlayingRow(channel("2"), key, listSource = 7L))
        assertFalse(isPlayingRow(channel("1"), key, listSource = 7L))
    }

    @Test
    fun `the mark is the target while the loader resolves or failed`() {
        val key = dockPlayingKey(channel("1"), resolvedStreamId = null, loaderSource = 7L)
        assertTrue(isPlayingRow(channel("1"), key, listSource = 7L))
        assertNull(dockPlayingKey(null, resolvedStreamId = "1", loaderSource = 7L))
    }

    @Test
    fun `the same id on another source is not marked`() {
        val key = dockPlayingKey(channel("1"), resolvedStreamId = "1", loaderSource = 7L)
        assertFalse(isPlayingRow(channel("1", providerId = 8L), key, listSource = 7L))
        assertTrue(isPlayingRow(channel("1", providerId = 7L), key, listSource = 3L))
    }

    @Test
    fun `back from full screen the dock is on the loader's channel`() {
        val target = channel("1", providerId = 7L)
        val zapped = channel("2")
        val after = dockTargetAfterFullScreen(target, "2", "Channel 2", listOf(channel("1"), zapped))
        assertEquals("2", after.id)
        assertEquals(7L, after.providerId)
        assertSame(target, dockTargetAfterFullScreen(target, "1", "Channel 1", emptyList()))
        assertSame(target, dockTargetAfterFullScreen(target, null, null, emptyList()))
    }

    @Test
    fun `a zapped channel not in the list keeps the loader's name`() {
        val after = dockTargetAfterFullScreen(channel("1"), "9", "Nine", emptyList())
        assertEquals("9", after.id)
        assertEquals("Nine", after.name)
    }

    @Test
    fun `next is the channel after the current one, wrapping at the end`() {
        val list = listOf(channel("1"), channel("2"), channel("3"))
        assertEquals("2", nextDockChannel(list, channel("1"), 0L) { true }?.id)
        assertEquals("1", nextDockChannel(list, channel("3"), 0L) { true }?.id)
    }

    @Test
    fun `next skips rows that cannot play`() {
        val list = listOf(channel("1"), channel("2"), channel("3"))
        assertEquals("3", nextDockChannel(list, channel("1"), 0L) { it.id != "2" }?.id)
    }

    @Test
    fun `next is the first channel when the current one is not in the list`() {
        val list = listOf(channel("1"), channel("2"))
        assertEquals("1", nextDockChannel(list, channel("9"), 0L) { true }?.id)
    }

    @Test
    fun `no next when no other channel can play`() {
        assertNull(nextDockChannel(listOf(channel("1")), channel("1"), 0L) { true })
        assertNull(nextDockChannel(null, channel("1"), 0L) { true })
    }
}
