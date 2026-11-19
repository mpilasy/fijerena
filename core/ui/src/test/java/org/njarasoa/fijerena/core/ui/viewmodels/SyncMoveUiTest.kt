package org.njarasoa.fijerena.core.ui.viewmodels

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.sync.MoveBlock
import org.njarasoa.fijerena.core.network.sync.MoveState
import org.njarasoa.fijerena.core.ui.viewmodels.SyncSettingsViewModel.MoveUi

/** When Move to Another Server is enabled — see the sync server move plan → Design, step 0. */
class SyncMoveUiTest {
    private val checked = MoveUi(blockLoaded = true)

    @Test
    fun `enabled once the check found no block`() {
        assertFalse(MoveUi().canMove)
        assertTrue(checked.canMove)
    }

    @Test
    fun `disabled while blocked or when the check failed`() {
        assertFalse(checked.copy(block = MoveBlock.UpdateFirst(listOf("Living Room TV"))).canMove)
        assertFalse(checked.copy(block = MoveBlock.MoveInProgress).canMove)
        assertFalse(checked.copy(block = MoveBlock.OldServerLeft).canMove)
        assertFalse(MoveUi(blockError = "Couldn't reach the sync server.").canMove)
    }

    @Test
    fun `disabled while a move runs or after it is done, enabled again after it stopped`() {
        assertFalse(checked.copy(move = MoveState.Running(MoveState.Stage.COPYING, copied = 1_200, total = 4_000)).canMove)
        assertFalse(checked.copy(move = MoveState.Done).canMove)
        // A move that stopped continues when started again, even when its own move record blocks a new one.
        assertTrue(MoveUi(move = MoveState.Failed("timeout"), block = MoveBlock.MoveInProgress, blockLoaded = true).canMove)
    }

    @Test
    fun `Cancel Move shows while a move runs or stopped, enabled only before the move record`() {
        assertFalse(checked.showCancel)
        assertFalse(checked.copy(move = MoveState.Done).showCancel)

        val copying = checked.copy(move = MoveState.Running(MoveState.Stage.COPYING, canCancel = true))
        assertTrue(copying.showCancel)
        assertTrue(copying.canCancel)
        val switching = checked.copy(move = MoveState.Running(MoveState.Stage.SWITCHING, canCancel = false))
        assertTrue(switching.showCancel)
        assertFalse(switching.canCancel)
        assertTrue(checked.copy(move = MoveState.Failed("timeout", canCancel = true)).canCancel)
        assertFalse(checked.copy(move = MoveState.Failed("timeout", canCancel = false)).canCancel)
        // Cancel Move runs: the button goes, and Move stays disabled.
        val cancelling = checked.copy(move = MoveState.Running(MoveState.Stage.CANCELLING))
        assertFalse(cancelling.showCancel)
        assertFalse(cancelling.canMove)
    }

    @Test
    fun `an open screen asks the old server again after each sync pass and on each tick, only while there is one`() =
        runTest(UnconfinedTestDispatcher()) {
            val oldServer = MutableStateFlow<String?>(null)
            val lastSyncAt = MutableStateFlow(1L)
            val ticks = MutableStateFlow(0L)
            val asked = mutableListOf<String>()
            backgroundScope.launch { oldServerRefreshes(oldServer, lastSyncAt, ticks).collect { asked += it } }

            lastSyncAt.value = 2
            ticks.value = 1
            assertEquals(emptyList<String>(), asked)

            oldServer.value = "https://old.test"
            lastSyncAt.value = 3
            lastSyncAt.value = 3
            ticks.value = 2
            assertEquals(List(3) { "https://old.test" }, asked)

            oldServer.value = null
            lastSyncAt.value = 4
            assertEquals(3, asked.size)
        }
}
