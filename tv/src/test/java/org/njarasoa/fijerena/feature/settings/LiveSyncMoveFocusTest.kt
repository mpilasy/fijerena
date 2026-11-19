package org.njarasoa.fijerena.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.sync.MoveBlock
import org.njarasoa.fijerena.core.network.sync.OldServerState
import org.njarasoa.fijerena.core.network.sync.SyncWire
import org.njarasoa.fijerena.core.ui.viewmodels.SyncSettingsViewModel.MoveUi
import org.njarasoa.fijerena.testing.FocusWalk
import org.njarasoa.fijerena.testing.down
import org.njarasoa.fijerena.testing.up
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.FirstVideoPlayerTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TV Live Sync, the move rows (the sync server move plan) between the devices and Leave, laid out
 * as SyncSettingsScreen lays them out (in a scrolling column): Down from the last device's Remove
 * goes to Purge old server, then to Move to another server (disabled, but still a focus stop so
 * its reason shows), then to Leave; Up goes back the same way to the Remove at the far right of
 * its row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class LiveSyncMoveFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private val walk = FocusWalk(rule)

    @Test
    fun `old server and move rows sit between the devices and Leave`() {
        val moveUi =
            MoveUi(
                block = MoveBlock.OldServerLeft,
                blockLoaded = true,
                oldServer = "https://old.example.com",
                oldServerState = OldServerState.NotMoved(listOf("Living Room TV")),
            )
        rule.setContent {
            FirstVideoPlayerTheme {
                val lastAction = remember { FocusRequester() }
                val moveFocus = remember { FocusRequester() }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DevicesPanel(
                        devices = DEVICES,
                        nowPlaying = emptyMap(),
                        builds = emptyMap(),
                        reports = null,
                        lastActionFocus = lastAction,
                        returnFocus = rememberNavReturnFocus(),
                        onRename = {},
                        onLogs = {},
                        onRemove = {},
                    )
                    OldServerPanel(
                        address = moveUi.oldServer!!,
                        moveUi = moveUi,
                        action = oldServerAction(moveUi),
                        onPurge = {},
                        onForget = {},
                        upFocus = lastAction,
                    )
                    MovePanel(moveUi = moveUi, onMove = {}, moveFocus = moveFocus, upFocus = null)
                    CinemaDangerButton(onClick = {}, text = "Leave the sync group", modifier = Modifier.focusProperties { up = moveFocus })
                }
                LaunchedEffect(Unit) { lastAction.requestFocusWithRetry() }
            }
        }

        walk.awaitFocused("Remove")
        walk.run(
            down("Purge old server"),
            down("Move to another server"),
            down("Leave the sync group"),
            up("Move to another server"),
            up("Purge old server"),
            up("Remove"),
        )
    }

    @Test
    fun `without an old server, Up from Move goes to the last Remove`() {
        val moveUi = MoveUi(block = MoveBlock.UpdateFirst(listOf("Living Room TV")), blockLoaded = true)
        rule.setContent {
            FirstVideoPlayerTheme {
                val lastAction = remember { FocusRequester() }
                val moveFocus = remember { FocusRequester() }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DevicesPanel(
                        devices = DEVICES,
                        nowPlaying = emptyMap(),
                        builds = emptyMap(),
                        reports = null,
                        lastActionFocus = lastAction,
                        returnFocus = rememberNavReturnFocus(),
                        onRename = {},
                        onLogs = {},
                        onRemove = {},
                    )
                    MovePanel(moveUi = moveUi, onMove = {}, moveFocus = moveFocus, upFocus = lastAction)
                    CinemaDangerButton(onClick = {}, text = "Leave the sync group", modifier = Modifier.focusProperties { up = moveFocus })
                }
                LaunchedEffect(Unit) { moveFocus.requestFocusWithRetry() }
            }
        }

        walk.awaitFocused("Move to another server")
        walk.run(
            up("Remove"),
            down("Move to another server"),
            down("Leave the sync group"),
            up("Move to another server"),
        )
    }

    private companion object {
        const val NOW = 1_800_000_000_000L

        val DEVICES =
            listOf(
                SyncWire.Device("this-device", "Living room TV", createdAt = 1, lastSeen = NOW, revoked = false, current = true),
                SyncWire.Device("other-device", "Phone", createdAt = 2, lastSeen = NOW, revoked = false, current = false),
            )
    }
}
