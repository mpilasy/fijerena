package org.njarasoa.fijerena.feature.category

import org.junit.Assert.assertEquals
import org.junit.Test

class RowMenuActionsTest {
    @Test
    fun `a channel has only the favourite`() {
        assertEquals(
            emptyList<RowMenuAction>(),
            rowMenuActions(isShowRow = false, isWatchable = false, showWatchedMark = true, canRemoveFromRecent = false),
        )
    }

    @Test
    fun `a channel on Recent can be removed from it`() {
        assertEquals(
            listOf(RowMenuAction.REMOVE_FROM_RECENT),
            rowMenuActions(isShowRow = false, isWatchable = false, showWatchedMark = true, canRemoveFromRecent = true),
        )
    }

    @Test
    fun `a film or an episode is marked watched`() {
        assertEquals(
            listOf(RowMenuAction.MARK_WATCHED),
            rowMenuActions(isShowRow = false, isWatchable = true, showWatchedMark = true, canRemoveFromRecent = false),
        )
    }

    @Test
    fun `a show row marks the whole show`() {
        assertEquals(
            listOf(RowMenuAction.MARK_SHOW_WATCHED, RowMenuAction.REMOVE_FROM_RECENT),
            rowMenuActions(isShowRow = true, isWatchable = true, showWatchedMark = true, canRemoveFromRecent = true),
        )
    }

    @Test
    fun `no watched marks where the source keeps them on its server`() {
        assertEquals(
            emptyList<RowMenuAction>(),
            rowMenuActions(isShowRow = true, isWatchable = true, showWatchedMark = false, canRemoveFromRecent = false),
        )
        assertEquals(
            listOf(RowMenuAction.REMOVE_FROM_RECENT),
            rowMenuActions(isShowRow = false, isWatchable = true, showWatchedMark = false, canRemoveFromRecent = true),
        )
    }
}
