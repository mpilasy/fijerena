package org.njarasoa.fijerena.ui.components

import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.ui.components.buttons.CinemaTextLabelButton
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog as CoreAlertDialog

/**
 * The TV dialog: [org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog] with the TV's own
 * buttons. Give the actions as data ([main], [others], [danger], [cancel]); the Look and Feel lays
 * them out and the dialog picks each button's kind. Focus opens on the safe default: Cancel when
 * there is a [danger] action, else [main], else Cancel; a list dialog passes [initialFocus].
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
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    initialFocus: FocusRequester? = null,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    properties: DialogProperties = DialogProperties(),
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
    title = title,
    text = text,
    initialFocus = initialFocus,
    containerColor = containerColor,
    iconContentColor = iconContentColor,
    titleContentColor = titleContentColor,
    textContentColor = textContentColor,
    properties = properties,
)
