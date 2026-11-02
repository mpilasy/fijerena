package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.MenuAction
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.inMenuOrder
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.components.input.TvInputListItem
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * The TV's action menu (a row's long-press or Menu-key menu, a More menu, a season's menu): the
 * [title], then the [actions] as one list of rows of the same look, then Cancel as the dialog's
 * cancel button. The same data as the phone's `CinemaActionMenu`. The removals come last, just
 * above Cancel, whatever their place in [actions]. Each row has its icon and label in the text
 * colour, a removal's in the danger colour.
 *
 * Focus opens on the first safe action; with only removals, on Cancel (CONTRIBUTING rule 7). Every
 * action closes the menu ([onDismiss] after its own click); Back or Cancel closes it without acting.
 */
@Composable
fun TvActionMenu(
    title: String,
    actions: List<MenuAction>,
    onDismiss: () -> Unit,
) {
    val listed = actions.inMenuOrder()
    val firstSafe = listed.firstOrNull { !it.removal }
    val firstSafeFocus = remember { FocusRequester() }
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        text =
            listed.takeIf { it.isNotEmpty() }?.let {
                {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(CinemaSpacing.dialogBody),
                    ) {
                        listed.forEach { action ->
                            ActionRow(
                                action = action,
                                onDismiss = onDismiss,
                                modifier = if (action === firstSafe) Modifier.focusRequester(firstSafeFocus) else Modifier,
                            )
                        }
                    }
                }
            },
        // No safe action: the dialog's own first focus, Cancel.
        initialFocus = firstSafeFocus.takeIf { firstSafe != null },
        cancel = DialogAction(stringResource(R.string.common_cancel), onClick = onDismiss),
    )
}

@Composable
private fun ActionRow(
    action: MenuAction,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    val tone = if (action.removal) TextTone.Danger else TextTone.Primary
    TvInputListItem(
        selected = false,
        onClick = {
            action.onClick()
            onDismiss()
        },
        modifier = modifier.fillMaxWidth(),
        leadingContent = {
            TvIcon(
                imageVector = action.icon,
                contentDescription = null,
                modifier = Modifier.size(TvDimensions.iconSmall),
                tone = tone,
            )
        },
        headlineContent = { CinemaText(text = action.label, tone = tone) },
    )
}
