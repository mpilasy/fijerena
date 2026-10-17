package org.njarasoa.fijerena.ui.components.buttons

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.njarasoa.fijerena.core.ui.theme.ButtonKind

/**
 * The phone's text button, looking as [PhoneButtonStyle] says: [kind]'s fill and outline, the
 * Display style's shape, the label centred. Screens and dialogs use it alike, directly or through
 * the [CinemaButton] family.
 */
@Composable
fun CinemaDialogActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    kind: ButtonKind = ButtonKind.Main,
    content: @Composable RowScope.() -> Unit,
) {
    // look-ok: the component that wraps Material's Button
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = PhoneButtonStyle.minHeight),
        enabled = enabled,
        shape = PhoneButtonStyle.shape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = PhoneButtonStyle.container(kind),
                contentColor = PhoneButtonStyle.content(kind),
                disabledContainerColor = PhoneButtonStyle.disabledContainer,
                disabledContentColor = PhoneButtonStyle.disabledContent,
            ),
        border = PhoneButtonStyle.outline(kind),
        contentPadding = PhoneButtonStyle.contentPadding,
        content = content,
    )
}
