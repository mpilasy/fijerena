package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.ui.components.buttons.IconAction

/**
 * Under a film's or show's backdrop when its details came from the device because the source
 * couldn't be reached (`isSavedCopy`): says so, with Retry.
 */
@Composable
fun SavedCopyNote(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CinemaText(
            text = stringResource(R.string.details_saved_copy),
            role = TextRole.BodySmall,
            tone = TextTone.Secondary,
            modifier = Modifier.weight(1f),
        )
        IconAction(onClick = onRetry, icon = CinemaIcons.Refresh, label = stringResource(R.string.common_retry))
    }
}
