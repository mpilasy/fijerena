package org.njarasoa.fijerena.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.theme.DialogActionArrangement
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle
import org.njarasoa.fijerena.core.ui.theme.ModernStyle
import org.njarasoa.fijerena.core.ui.theme.UiDialogActionTokens
import org.njarasoa.fijerena.testing.FocusWalk
import org.njarasoa.fijerena.testing.down
import org.njarasoa.fijerena.testing.left
import org.njarasoa.fijerena.testing.right
import org.njarasoa.fijerena.testing.up
import org.robolectric.RobolectricTestRunner

/**
 * The dialog's actions as data (style layout tokens plan, Phase 1): focus opens on the main action,
 * or on Cancel when there is a Danger action; a row puts the main action at the end, a stack puts
 * it on top and Cancel at the bottom.
 */
@RunWith(RobolectricTestRunner::class)
class CinemaAlertDialogFocusTest {
    @get:Rule
    val rule =
        androidx.compose.ui.test.junit4
            .createComposeRule()

    private val walk = FocusWalk(rule)

    private fun setDialog(
        arrangement: DialogActionArrangement = DialogActionArrangement.ROW,
        main: Boolean = true,
        others: List<String> = emptyList(),
        danger: Boolean = false,
    ) {
        val style = ModernStyle.copy(dialog = ModernStyle.dialog.copy(actions = UiDialogActionTokens(arrangement)))
        rule.setContent {
            CompositionLocalProvider(LocalUiStyle provides style) {
                CinemaAlertDialog(
                    onDismissRequest = {},
                    main = if (main) DialogAction("Save") {} else null,
                    others = others.map { DialogAction(it) {} },
                    danger = if (danger) DialogAction("Delete") {} else null,
                    cancel = DialogAction("Cancel") {},
                )
            }
        }
    }

    @Test
    fun `a row opens on the main action, with Cancel before it`() {
        setDialog()

        walk.assertFocused("Save")
        walk.run(left("Cancel"), right("Save"))
    }

    @Test
    fun `a Danger action opens on Cancel`() {
        setDialog(main = false, danger = true)

        walk.assertFocused("Cancel")
        walk.run(right("Delete"))
    }

    @Test
    fun `a stack puts the main action on top and Cancel at the bottom`() {
        setDialog(arrangement = DialogActionArrangement.STACKED, others = listOf("Remind me"))

        walk.assertFocused("Save")
        walk.run(down("Remind me"), down("Cancel"), up("Remind me"))
    }

    @Test
    fun `a row of three actions becomes a stack`() {
        setDialog(others = listOf("Remind me"))

        walk.assertFocused("Save")
        walk.run(down("Remind me"), down("Cancel"))
    }
}
