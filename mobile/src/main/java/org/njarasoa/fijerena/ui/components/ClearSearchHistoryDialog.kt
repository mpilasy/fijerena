package org.njarasoa.fijerena.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.TextTone

/**
 * The question before the recent searches (Search, Search the Guide) are cleared. Clearing a full
 * list asks once, as Clear Recent does; removing one entry is immediate.
 */
@Composable
fun ClearSearchHistoryDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.search_history_clear_confirm_title),
        text = { CinemaText(text = stringResource(R.string.search_history_clear_confirm_message), tone = TextTone.Secondary) },
        danger = DialogAction(stringResource(R.string.epg_browser_clear_all_description), onClick = onConfirm),
        cancel = DialogAction(stringResource(R.string.common_cancel), onClick = onDismiss),
    )
}
