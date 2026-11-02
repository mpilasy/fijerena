package org.njarasoa.fijerena.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.window.DialogProperties
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.components.buttons.CinemaTextLabelButton
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog as CoreAlertDialog

/**
 * The TV dialog: [org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog] with the TV's own
 * buttons. Give the actions as data ([main], [others], [danger], [cancel]); the Look and Feel lays
 * them out and the dialog picks each button's kind. Focus opens on the safe default: Cancel when
 * there is a [danger] action, else [main], else Cancel; a list dialog passes [initialFocus].
 *
 * The [title] is text: the dialog gives it its role. [header] replaces it only where the title
 * needs more than text (the PIN pad's centred title). The colours come from the theme: the surface
 * behind, the text colours on it.
 */
@Composable
fun CinemaAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    main: DialogAction? = null,
    others: List<DialogAction> = emptyList(),
    danger: DialogAction? = null,
    cancel: DialogAction? = null,
    icon: (@Composable () -> Unit)? = null,
    title: String? = null,
    header: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    initialFocus: FocusRequester? = null,
    properties: DialogProperties = DialogProperties(),
    wide: Boolean = false,
    stackActions: Boolean = false,
) = CoreAlertDialog(
    onDismissRequest = onDismissRequest,
    actionButton = { action, kind, buttonModifier ->
        CinemaTextLabelButton(
            onClick = action.onClick,
            text = action.label,
            kind = kind,
            modifier = buttonModifier,
            enabled = action.enabled,
        )
    },
    modifier = modifier,
    main = main,
    others = others,
    danger = danger,
    cancel = cancel,
    icon = icon,
    title = header ?: title?.let { { CinemaText(it, role = TextRole.Title) } },
    text = text,
    initialFocus = initialFocus,
    containerColor = CinemaTheme.colors.surface,
    titleContentColor = CinemaTheme.colors.textPrimary,
    textContentColor = CinemaTheme.colors.textSecondary,
    properties = properties,
    wide = wide,
    stackActions = stackActions,
)
