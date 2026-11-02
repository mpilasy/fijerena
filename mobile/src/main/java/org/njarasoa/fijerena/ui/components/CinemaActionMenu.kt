package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.MenuAction
import org.njarasoa.fijerena.core.ui.components.inMenuOrder
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing

/**
 * The phone's action menu (a long-press menu, a More menu, a season's menu): the [title], then the
 * [actions] as one list of rows of the same look, then Cancel in the dialog's cancel place. The
 * removals come last, just above Cancel, whatever their place in [actions] (CONTRIBUTING rule 7).
 * Each row has its icon and label in the text colour, a removal's in the danger colour, as on the
 * TV's `TvActionMenu`. Every action closes the menu ([onDismiss] after its own click).
 */
@Composable
fun CinemaActionMenu(
    title: String,
    actions: List<MenuAction>,
    onDismiss: () -> Unit,
) {
    val listed = actions.inMenuOrder()
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        text =
            listed.takeIf { it.isNotEmpty() }?.let {
                {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(CinemaSpacing.dialogBody),
                    ) {
                        listed.forEach { action -> ActionRow(action, onDismiss) }
                    }
                }
            },
        cancel = DialogAction(stringResource(R.string.common_cancel), onClick = onDismiss),
    )
}

@Composable
private fun ActionRow(
    action: MenuAction,
    onDismiss: () -> Unit,
) {
    CinemaChoiceRow(
        label = action.label,
        onClick = {
            action.onClick()
            onDismiss()
        },
        modifier = Modifier.fillMaxWidth(),
        icon = action.icon,
        removal = action.removal,
    )
}
