package org.njarasoa.fijerena.ui.player.components.dialogs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.testing.FocusWalk
import org.njarasoa.fijerena.testing.down
import org.njarasoa.fijerena.testing.up
import org.robolectric.RobolectricTestRunner

/** The Audio dialog's Night Audio switch: entry focus stays on the current track, Up reaches the switch. */
@RunWith(RobolectricTestRunner::class)
class TvSelectorDialogFocusTest {
    @get:Rule
    val rule =
        androidx.compose.ui.test.junit4
            .createComposeRule()

    private val walk = FocusWalk(rule)

    private fun track(
        title: String,
        selected: Boolean,
    ) = TvSelectorOption(title = title, selected = selected, onSelect = {})

    private fun show(
        options: List<TvSelectorOption>,
        onChecked: (Boolean) -> Unit = {},
    ) {
        rule.setContent {
            var checked by remember { mutableStateOf(false) }
            TvSelectorDialog(
                title = "Audio",
                options = options,
                onDismiss = {},
                switchRow =
                    TvSelectorSwitch(
                        title = "Night Audio",
                        description = "Quieter loud sounds, clearer speech",
                        checked = checked,
                        onCheckedChange = {
                            checked = it
                            onChecked(it)
                        },
                    ),
            )
        }
    }

    @Test
    fun `entry focus is on the selected track, Up reaches the switch`() {
        show(listOf(track("English", selected = true), track("French", selected = false)))

        walk.awaitFocused("English")
        walk.run(
            up("Night Audio / Quieter loud sounds"),
            down("English"),
            down("French"),
            down("Cancel"),
        )
    }

    @Test
    fun `with one track the switch alone takes focus`() {
        var checked = false
        show(emptyList()) { checked = it }

        walk.awaitFocused("Night Audio")
        walk.press(Key.DirectionCenter)
        rule.runOnIdle { assertTrue(checked) }
    }
}
