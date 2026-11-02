package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.ApplyDialogScrim
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.DialogPosition
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle
import org.njarasoa.fijerena.core.ui.theme.ProvideUiScaledDensity
import org.njarasoa.fijerena.core.ui.theme.cornerShape
import org.njarasoa.fijerena.ui.components.buttons.IconAction
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * The phone sheet: a list or a detail over the screen. The Look and Feel decides how it opens
 * (`UiDialogTokens.sheet`): from the bottom with a drag handle, as a centred panel, or full screen
 * with a Close button. Its corners and scrim come from the dialog tokens and its colour from the
 * theme, as for `CinemaAlertDialog`. Never call `ModalBottomSheet` or `Dialog` in a screen: use
 * this.
 *
 * A swipe down, a tap on the scrim or Back calls [onDismissRequest]. When [dismissible] is false,
 * none of them closes the sheet (the caller closes it). [content] gets `close`: it closes the sheet
 * as the Look and Feel does (a bottom sheet slides down first) and then calls [onDismissRequest].
 *
 * The sheet sets the content's padding in every Look: a gap under the content and, while [rows] is
 * false, the side margin. Give [rows] true when the content is a list of full-width rows that pad
 * their own sides (`SettingsListRow`, `CinemaRow`), and put a title over the rows with
 * [CinemaSheetHeader]. The content adds no side or bottom padding of its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CinemaSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissible: Boolean = true,
    rows: Boolean = false,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val sides = if (rows) Modifier else Modifier.padding(horizontal = CinemaSpacing.md)
    val tokens = LocalUiStyle.current.dialog
    val color = CinemaTheme.colors.surface
    val canDismiss by rememberUpdatedState(dismissible)
    val dismiss = { if (canDismiss) onDismissRequest() }

    when (tokens.sheet) {
        DialogPosition.BOTTOM_SHEET -> {
            val state =
                rememberModalBottomSheetState(
                    // Fully open: a half-open sheet hides the end of its list.
                    skipPartiallyExpanded = true,
                    confirmValueChange = { canDismiss || it != SheetValue.Hidden },
                )
            val scope = rememberCoroutineScope()
            val close: () -> Unit = {
                scope.launch { state.hide() }.invokeOnCompletion { if (!state.isVisible) onDismissRequest() }
            }
            ModalBottomSheet(
                onDismissRequest = dismiss,
                modifier = modifier,
                sheetState = state,
                shape = cornerShape(tokens.cornerRadius),
                containerColor = color,
                scrimColor = CinemaTheme.colors.mediaScrim.copy(alpha = tokens.scrimAlpha),
                properties = ModalBottomSheetProperties(shouldDismissOnBackPress = dismissible),
            ) {
                ProvideUiScaledDensity {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = CinemaSpacing.md).then(sides)) { content(close) }
                }
            }
        }

        DialogPosition.CENTERED -> {
            // The window covers the whole screen (under the system bars too), so the scrim dims all of
            // it. The panel is most of the screen's width and at most most of its height: a long list
            // scrolls in it. A tap on the scrim around the panel closes it.
            Dialog(
                onDismissRequest = dismiss,
                properties =
                    DialogProperties(
                        dismissOnBackPress = dismissible,
                        dismissOnClickOutside = false,
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false,
                    ),
            ) {
                ApplyDialogScrim(tokens.scrimAlpha)
                ProvideUiScaledDensity {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(modifier = Modifier.matchParentSize().pointerInput(onDismissRequest) { detectTapGestures { dismiss() } })
                        Surface(
                            modifier =
                                modifier
                                    .width(
                                        minOf(
                                            maxWidth * MobileDimensions.SHEET_CENTRED_WIDTH_FRACTION,
                                            MobileDimensions.sheetCentredMaxWidth,
                                        ),
                                    ).heightIn(max = maxHeight * MobileDimensions.SHEET_CENTRED_HEIGHT_FRACTION),
                            shape = cornerShape(tokens.cornerRadius),
                            color = color,
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = CinemaSpacing.md).then(sides)) {
                                content(onDismissRequest)
                            }
                        }
                    }
                }
            }
        }

        DialogPosition.FULL_BLEED -> {
            Dialog(
                onDismissRequest = dismiss,
                properties =
                    DialogProperties(
                        dismissOnBackPress = dismissible,
                        dismissOnClickOutside = false,
                        usePlatformDefaultWidth = false,
                    ),
            ) {
                ApplyDialogScrim(tokens.scrimAlpha)
                ProvideUiScaledDensity {
                    Surface(modifier = modifier.fillMaxSize(), shape = RectangleShape, color = color) {
                        Column(modifier = Modifier.fillMaxSize().padding(bottom = CinemaSpacing.md)) {
                            // No scrim to tap in full screen: Close is on the sheet.
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                if (dismissible) {
                                    IconAction(
                                        onClick = onDismissRequest,
                                        icon = CinemaIcons.Close,
                                        label = stringResource(R.string.common_close),
                                    )
                                }
                            }
                            Column(modifier = Modifier.fillMaxWidth().weight(1f, fill = false).then(sides)) {
                                content(onDismissRequest)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The title over a [CinemaSheet] of rows (`rows = true`): [title], an optional [subtitle] under it,
 * and optional icon [actions] at the end, with the sheet's side margin.
 */
@Composable
fun CinemaSheetHeader(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CinemaSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
            CinemaText(text = title, role = TextRole.Title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) CinemaText(text = subtitle, role = TextRole.BodySmall, tone = TextTone.Secondary)
        }
        actions()
    }
}
