package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle

/**
 * Themed replacement for [Button] used inside `confirmButton`/`dismissButton` dialog slots —
 * M3's default button shape ignores the app's [org.njarasoa.fijerena.core.ui.theme.UiStyle]
 * shape tokens (hardcoded stadium corner), so every dialog action row needs this instead.
 */
@Composable
fun CinemaDialogActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(LocalUiStyle.current.shapes.button),
        colors = colors,
        contentPadding = contentPadding,
        content = content,
    )
}

/** Themed replacement for [TextButton] used inside dialog action rows (e.g. plain "Cancel"). */
@Composable
fun CinemaDialogTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(LocalUiStyle.current.shapes.button),
        colors = colors,
        contentPadding = contentPadding,
        content = content,
    )
}
