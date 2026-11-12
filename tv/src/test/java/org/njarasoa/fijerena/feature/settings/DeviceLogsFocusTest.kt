package org.njarasoa.fijerena.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.sync.DeviceReport
import org.njarasoa.fijerena.core.network.sync.SyncWire
import org.njarasoa.fijerena.core.ui.viewmodels.DeviceLogsViewModel
import org.njarasoa.fijerena.testing.FocusWalk
import org.njarasoa.fijerena.testing.down
import org.njarasoa.fijerena.testing.left
import org.njarasoa.fijerena.testing.right
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.FirstVideoPlayerTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TV Live Sync, a Nerd's devices list (the device diagnostics sync plan): the other device's row
 * has Logs between Rename and Remove; OK opens Device Logs on its first block, Down reaches the
 * entries, and Back returns to that Logs action. Navigation is simulated as Navigation Compose does
 * it: the list leaves the composition and comes back with its saved state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class DeviceLogsFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private val walk = FocusWalk(rule)

    @Test
    fun `Logs sits after Rename, opens the page on its first block, and Back returns to it`() {
        var opened: String? = null
        rule.setContent {
            FirstVideoPlayerTheme {
                val pages = rememberSaveableStateHolder()
                var open by remember { mutableStateOf<String?>(null) }
                val page = open
                if (page == null) {
                    pages.SaveableStateProvider("list") {
                        val returnFocus = rememberNavReturnFocus()
                        val start = remember { FocusRequester() }
                        Column {
                            CinemaSecondaryButton(onClick = {}, text = "Start", modifier = Modifier.focusRequester(start))
                            DevicesPanel(
                                devices = DEVICES,
                                nowPlaying = emptyMap(),
                                builds = emptyMap(),
                                reports = mapOf(OTHER to REPORT),
                                lastActionFocus = remember { FocusRequester() },
                                returnFocus = returnFocus,
                                onRename = {},
                                onLogs = {
                                    opened = it.id
                                    open = it.id
                                },
                                onRemove = {},
                            )
                        }
                        NavReturnFocusEffect(returnFocus)
                        LaunchedEffect(Unit) { if (returnFocus.key == null) start.requestFocusWithRetry() }
                    }
                } else {
                    pages.SaveableStateProvider("logs") {
                        // The nav host's Back.
                        Box(
                            Modifier.onPreviewKeyEvent { event ->
                                val back = event.key == Key.Back && event.type == KeyEventType.KeyDown
                                if (back) open = null
                                back
                            },
                        ) {
                            DeviceLogsContent(DeviceLogsViewModel.State("Living room TV", report = REPORT))
                        }
                    }
                }
            }
        }

        walk.awaitFocused("Start")
        // The row's actions sit at its end: Down from this device's Rename lands on the next row's
        // last action, as before the Logs action.
        walk.run(
            down("Rename"),
            down("Remove"),
            left("Logs"),
            left("Rename"),
            right("Logs"),
        )
        walk.press(Key.DirectionCenter)
        assertEquals(OTHER, opened)
        walk.awaitFocused("Sent ")
        walk.press(Key.DirectionDown)
        val entry = walk.focusedText()
        assertTrue(entry, entry.contains("Crash"))
        walk.press(Key.Back)
        walk.awaitFocused("Logs")
        walk.run(right("Remove"))
    }

    private companion object {
        const val THIS = "this-device"
        const val OTHER = "other-device"
        const val NOW = 1_800_000_000_000L

        val DEVICES =
            listOf(
                SyncWire.Device(THIS, "Phone", createdAt = 1, lastSeen = NOW, revoked = false, current = true),
                SyncWire.Device(OTHER, "Living room TV", createdAt = 2, lastSeen = NOW, revoked = false, current = false),
            )

        val REPORT =
            DeviceReport(
                sentAt = NOW,
                versionName = "0.9.20261111000000",
                counts7d = DeviceReport.Counts(crashes = 1),
                entries =
                    listOf(
                        DeviceReport.Entry(NOW - 1_000, DeviceReport.KIND_CRASH, "uncaught on main", "IllegalStateException: boom"),
                    ),
            )
    }
}
