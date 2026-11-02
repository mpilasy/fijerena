package org.njarasoa.fijerena.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog as CoreAlertDialog

/**
 * The phone dialog: [org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog] with the phone's
 * own buttons. Give the actions as data ([main], [others], [danger], [cancel]); the Look and Feel
 * lays them out and the dialog picks each button's kind.
 *
 * The [title] is text: the dialog gives it its role. [header] replaces it only where the title
 * needs more than text (the PIN dialog's note above its centred title). The colours come from the
 * theme.
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
) = CoreAlertDialog(
    onDismissRequest = onDismissRequest,
    actionButton = { action, kind, buttonModifier ->
        CinemaDialogActionButton(onClick = action.onClick, modifier = buttonModifier, enabled = action.enabled, kind = kind) {
            CinemaText(action.label, role = TextRole.Label, tone = TextTone.Inherit, textAlign = TextAlign.Center)
        }
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
    properties = properties,
)
