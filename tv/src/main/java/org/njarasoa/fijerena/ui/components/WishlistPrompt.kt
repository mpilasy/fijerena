package org.njarasoa.fijerena.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton

/**
 * Asked once after a Wishlist title is watched (wishlist UX plan, U-D3): remove it from the
 * Wishlist, or keep it. Keep is the dismiss button, so focus never opens on the removal (focus
 * contract rule 7); Back keeps it too.
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
            CinemaButton(onClick = { onAnswer(true) }, kind = ButtonKind.Danger) {
                CinemaText(text = stringResource(R.string.wanted_prompt_remove), role = TextRole.Label, tone = TextTone.Inherit)
            }
        },
        dismissButton = {
            CinemaButton(onClick = { onAnswer(false) }, kind = ButtonKind.Other) {
                CinemaText(text = stringResource(R.string.wanted_prompt_keep), role = TextRole.Label, tone = TextTone.Inherit)
            }
        },
        containerColor = CinemaTheme.colors.surface,
        titleContentColor = CinemaTheme.colors.textPrimary,
        textContentColor = CinemaTheme.colors.textSecondary,
    )
}
