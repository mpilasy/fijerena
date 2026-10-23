package org.njarasoa.fijerena.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton

/**
 * Asked once after a Wishlist title is watched (wishlist UX plan, U-D3): remove it from the
 * Wishlist, or keep it. Dismissing keeps it.
 */
@Composable
fun WishlistRemovePrompt(
    title: String,
    onAnswer: (remove: Boolean) -> Unit,
) {
    CinemaAlertDialog(
        onDismissRequest = { onAnswer(false) },
        title = { CinemaText(text = stringResource(R.string.wanted_prompt_title, title), role = TextRole.Heading) },
        text = { CinemaText(text = stringResource(R.string.wanted_prompt_text), tone = TextTone.Secondary) },
        confirmButton = {
            CinemaDialogActionButton(onClick = { onAnswer(true) }, kind = ButtonKind.Danger) {
                CinemaText(stringResource(R.string.wanted_prompt_remove))
            }
        },
        dismissButton = {
            CinemaDialogActionButton(onClick = { onAnswer(false) }, kind = ButtonKind.Other) {
                CinemaText(stringResource(R.string.wanted_prompt_keep))
            }
        },
    )
}
