package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.DialogActionArrangement
import org.njarasoa.fijerena.core.ui.theme.DialogActionWidth
import org.njarasoa.fijerena.core.ui.theme.DialogCancelPlace
import org.njarasoa.fijerena.core.ui.theme.DialogPosition
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle
import org.njarasoa.fijerena.core.ui.theme.ProvideUiScaledDensity
import org.njarasoa.fijerena.core.ui.theme.UiDialogActionTokens
import org.njarasoa.fijerena.core.ui.theme.cornerShape

/** One button of a dialog: its label and what it does. The dialog gives it its kind and its place. */
@Immutable
class DialogAction(
    val label: String,
    val enabled: Boolean = true,
    /** For a caller that moves focus to this button itself, e.g. Down from the last field. */
    val focusRequester: FocusRequester? = null,
    val onClick: () -> Unit,
)

/**
 * The first input in a dialog's body (a form's first field). A dialog whose body has an input opens
 * with focus on it, not on Cancel or a Save that is still disabled: the input claims this in
 * composition ([claimFirstField]) and the dialog lands its first focus there, unless the caller gives
 * `initialFocus`. Each app's field components claim it; a screen does nothing.
 */
class DialogFirstField {
    val requester = FocusRequester()

    var claimed = false
        private set

    /** True for the first input that asks, false for every later one. */
    fun claim(): Boolean {
        val first = !claimed
        claimed = true
        return first
    }
}

/** The dialog's [DialogFirstField] inside its body; null outside a dialog. */
val LocalDialogFirstField = staticCompositionLocalOf<DialogFirstField?> { null }

/**
 * For an input component: the [DialogFirstField] requester when this input is the first in a
 * dialog's body, else null. Put it on the input's focus target.
 */
@Composable
fun claimFirstField(): FocusRequester? {
    val firstField = LocalDialogFirstField.current
    return remember(firstField) { firstField?.takeIf { it.claim() }?.requester }
}

/** Draws one dialog button of [ButtonKind] in the app's own button look. */
typealias DialogActionButton = @Composable (action: DialogAction, kind: ButtonKind, modifier: Modifier) -> Unit

/**
 * Themed replacement for [androidx.compose.material3.AlertDialog] — branches on the active
 * [org.njarasoa.fijerena.core.ui.theme.UiStyle]'s [org.njarasoa.fijerena.core.ui.theme.UiDialogTokens.position]
 * so Cupertino renders as a bottom sheet and Roku renders full-bleed, matching each platform's
 * real dialog convention; Material/BRAVIA stay centered. Corner radius and scrim
 * alpha come from the same tokens.
 *
 * The caller gives the actions as data: one [main] action, [others], one [danger] action and
 * [cancel]. The style's [org.njarasoa.fijerena.core.ui.theme.UiDialogTokens.actions] lays them out
 * and the dialog gives each one its [ButtonKind]. Each app draws the buttons with [actionButton]
 * through its own `CinemaAlertDialog` (`ui.components`); feature code calls that one.
 *
 * [wide]: a centred panel as wide as its content asks (with [modifier]), not the platform's
 * dialog width — for a big form, such as the TV category filter.
 *
 * [stackActions]: the actions are always a stack, never a row. Give it to a dialog whose number of
 * actions changes with its data (the TV Guide's programme panel: Start over only when the programme
 * replays), so the panel keeps one layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CinemaAlertDialog(
    onDismissRequest: () -> Unit,
    actionButton: DialogActionButton,
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
    wide: Boolean = false,
    stackActions: Boolean = false,
) {
    val tokens = LocalUiStyle.current.dialog
    val shape = cornerShape(tokens.cornerRadius)

    // A new Dialog window doesn't inherit Compose's focus state from the screen behind it, so
    // without an explicit initial-focus request TV's D-pad has nothing to move onto — key
    // events fall through to the (now invisible) triggering button. Land focus on the safe
    // default action once per dialog appearance: Cancel when there is a Danger action, else the
    // main action, else Cancel. A disabled button cannot take focus, so it is skipped.
    //
    // A dialog whose real content lives in [text] — a list of options to choose from — should pass
    // [initialFocus] instead, attached to its first item, so the user does not have to D-pad up
    // through the whole panel to reach it. When it is supplied the buttons are left alone.
    val actions = dialogActions(main, others, danger, cancel, tokens.actions.cancel)
    val focusedAction =
        when {
            initialFocus != null -> null
            danger != null -> listOfNotNull(cancel, main) + others + danger
            else -> listOfNotNull(main, cancel) + others
        }?.firstOrNull { it.enabled }
    val buttonFocusRequester = remember { FocusRequester() }
    // A form (a body with an input) opens on its first input, not on a button: see [DialogFirstField].
    val firstField = remember { DialogFirstField() }
    val actionArea: @Composable () -> Unit = {
        DialogActionArea(actions, tokens.actions, actionButton, stackActions) { action ->
            Modifier
                .then(if (action === focusedAction) Modifier.focusRequester(buttonFocusRequester) else Modifier)
                .then(action.focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
        }
    }
    LaunchedEffect(Unit) {
        // The dialog window composes after this screen, and a caller-supplied target may come later
        // still, so retry each frame until the request takes (an unattached requester returns
        // false, it doesn't throw). Same shape as the TV app's requestFocusWithRetry, which
        // core:ui can't depend on.
        // The body's first input claims its place while the dialog window composes, so the target
        // is read again on each try.
        val target = {
            initialFocus
                ?: firstField.requester.takeIf { firstField.claimed }
                ?: buttonFocusRequester.takeIf { focusedAction != null }
        }
        var frames = 0
        while (target()?.requestFocus(FocusDirection.Enter) != true && frames < FOCUS_RETRY_MAX_FRAMES) {
            withFrameNanos { }
            frames++
        }
    }

    when (tokens.position) {
        DialogPosition.CENTERED -> {
            // Material's own AlertDialog composes its padding and its Surface inside the dialog
            // window, where the scaled density no longer applies — see [ProvideUiScaledDensity].
            // BasicAlertDialog hands us that content slot, so the whole panel scales with the rest
            // of the app instead of only the app behind it.
            BasicAlertDialog(
                onDismissRequest = onDismissRequest,
                modifier = modifier,
                properties =
                    if (wide) {
                        DialogProperties(
                            dismissOnBackPress = properties.dismissOnBackPress,
                            dismissOnClickOutside = properties.dismissOnClickOutside,
                            securePolicy = properties.securePolicy,
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = properties.decorFitsSystemWindows,
                        )
                    } else {
                        properties
                    },
            ) {
                ApplyDialogScrim(tokens.scrimAlpha)
                RecordDialogKeys()
                ProvideUiScaledDensity {
                    // A safe margin above and below: a tall dialog stops short of the screen edges
                    // (the TV's overscan margin), and its body yields and scrolls.
                    Surface(
                        modifier = Modifier.padding(vertical = CinemaSpacing.xl),
                        shape = shape,
                        color = containerColor,
                        tonalElevation = AlertDialogDefaults.TonalElevation,
                    ) {
                        Column(modifier = Modifier.padding(CinemaSpacing.lg)) {
                            icon?.let {
                                CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                                    Box(Modifier.align(Alignment.CenterHorizontally)) { it() }
                                }
                                Spacer(Modifier.height(CinemaSpacing.md))
                            }
                            title?.let {
                                CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                                    ProvideTextStyle(textStyleOf(TextRole.Title), it)
                                }
                                Spacer(Modifier.height(CinemaSpacing.md))
                            }
                            text?.let {
                                // fill = false so a short body stays short, but a long one yields
                                // rather than pushing the buttons off the bottom of the screen.
                                Box(Modifier.weight(1f, fill = false)) {
                                    CompositionLocalProvider(
                                        LocalContentColor provides textContentColor,
                                        LocalDialogFirstField provides firstField,
                                    ) {
                                        ProvideTextStyle(textStyleOf(TextRole.Body), it)
                                    }
                                }
                                // No space under a body with no buttons after it (a list dialog
                                // whose last row is Cancel).
                                if (actions.isNotEmpty()) Spacer(Modifier.height(CinemaSpacing.lg))
                            }
                            actionArea()
                        }
                    }
                }
            }
        }

        DialogPosition.FULL_BLEED -> {
            Dialog(
                onDismissRequest = onDismissRequest,
                properties =
                    DialogProperties(
                        dismissOnBackPress = properties.dismissOnBackPress,
                        dismissOnClickOutside = properties.dismissOnClickOutside,
                        securePolicy = properties.securePolicy,
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = properties.decorFitsSystemWindows,
                    ),
            ) {
                ApplyDialogScrim(tokens.scrimAlpha)
                RecordDialogKeys()
                ProvideUiScaledDensity {
                    Surface(
                        modifier = modifier.fillMaxSize(),
                        shape = RectangleShape,
                        color = containerColor,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(CinemaSpacing.lg),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            // A long body yields, so the buttons stay on screen.
                            Column(Modifier.weight(1f, fill = false)) {
                                icon?.let {
                                    CompositionLocalProvider(LocalContentColor provides iconContentColor, content = it)
                                    Spacer(Modifier.height(CinemaSpacing.xs))
                                }
                                title?.let {
                                    CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                                        ProvideTextStyle(textStyleOf(TextRole.Title), it)
                                    }
                                    Spacer(Modifier.height(CinemaSpacing.xs))
                                }
                                text?.let {
                                    CompositionLocalProvider(
                                        LocalContentColor provides textContentColor,
                                        LocalDialogFirstField provides firstField,
                                    ) {
                                        ProvideTextStyle(textStyleOf(TextRole.Body), it)
                                    }
                                }
                            }
                            actionArea()
                        }
                    }
                }
            }
        }

        DialogPosition.BOTTOM_SHEET -> {
            ModalBottomSheet(
                onDismissRequest = onDismissRequest,
                modifier = modifier,
                // Fully open: a half-open sheet leaves its buttons below the screen edge.
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                shape = shape,
                containerColor = containerColor,
                scrimColor = CinemaTheme.colors.mediaScrim.copy(alpha = tokens.scrimAlpha),
            ) {
                ProvideUiScaledDensity {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = CinemaSpacing.lg, vertical = CinemaSpacing.xs)) {
                        icon?.let {
                            CompositionLocalProvider(LocalContentColor provides iconContentColor, content = it)
                            Spacer(Modifier.height(CinemaSpacing.xs))
                        }
                        title?.let {
                            CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                                ProvideTextStyle(textStyleOf(TextRole.Title), it)
                            }
                            Spacer(Modifier.height(CinemaSpacing.xs))
                        }
                        text?.let {
                            // A long body (a long picker list) yields, so the buttons stay on screen.
                            Box(Modifier.weight(1f, fill = false)) {
                                CompositionLocalProvider(
                                    LocalContentColor provides textContentColor,
                                    LocalDialogFirstField provides firstField,
                                ) {
                                    ProvideTextStyle(textStyleOf(TextRole.Body), it)
                                }
                            }
                            if (actions.isNotEmpty()) Spacer(Modifier.height(CinemaSpacing.md))
                        }
                        actionArea()
                        Spacer(Modifier.height(CinemaSpacing.md))
                    }
                }
            }
        }
    }
}

/** About half a second at 60 Hz for the dialog window to compose and attach its focus target. */
private const val FOCUS_RETRY_MAX_FRAMES = 30

private class KindedAction(
    val action: DialogAction,
    val kind: ButtonKind,
)

/** The actions in stack order (the main place first), each with its kind. */
private fun dialogActions(
    main: DialogAction?,
    others: List<DialogAction>,
    danger: DialogAction?,
    cancel: DialogAction?,
    cancelPlace: DialogCancelPlace,
): List<KindedAction> {
    val rest =
        listOfNotNull(main?.let { KindedAction(it, ButtonKind.Main) }) +
            others.map { KindedAction(it, ButtonKind.Other) } +
            listOfNotNull(danger?.let { KindedAction(it, ButtonKind.Danger) })
    val cancelAction = listOfNotNull(cancel?.let { KindedAction(it, ButtonKind.Other) })
    return if (cancelPlace == DialogCancelPlace.FIRST) cancelAction + rest else rest + cancelAction
}

/**
 * The dialog's buttons as [tokens] say. A stack puts the first action on top; a row puts it at
 * the end and goes to a stack of full-width buttons when it has more than two actions, its labels
 * do not fit or [stackActions] is set.
 */
@Composable
private fun DialogActionArea(
    actions: List<KindedAction>,
    tokens: UiDialogActionTokens,
    actionButton: DialogActionButton,
    stackActions: Boolean,
    modifierFor: (DialogAction) -> Modifier,
) {
    if (actions.isEmpty()) return
    val gap = CinemaSpacing.xs
    Layout(
        content = { actions.forEach { actionButton(it.action, it.kind, modifierFor(it.action)) } },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val gaps = gapPx * (measurables.size - 1)
        val labels = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else labels.sum() + gaps
        val share = (width - gaps) / measurables.size
        val full = tokens.width == DialogActionWidth.FULL
        val inRow =
            !stackActions &&
                tokens.arrangement == DialogActionArrangement.ROW &&
                measurables.size <= 2 &&
                if (full) labels.max() <= share else labels.sum() + gaps <= width
        // A row that cannot hold its buttons becomes a stack of full-width buttons, so every stack
        // in a Look of rows has the same look, whatever the length of its labels.
        val stackFull = full || tokens.arrangement == DialogActionArrangement.ROW
        val widths =
            when {
                inRow && full -> List(measurables.size) { share }
                inRow -> labels
                stackFull -> List(measurables.size) { width }
                else -> List(measurables.size) { labels.max().coerceAtMost(width) }
            }
        // Every button as tall as the tallest, so a label that wraps does not make one button
        // taller than the others.
        val buttonHeight =
            measurables
                .mapIndexed { i, it -> it.minIntrinsicHeight(widths[i]) }
                .max()
                .coerceAtMost(constraints.maxHeight)
        val placeables =
            measurables.mapIndexed { i, it ->
                it.measure(
                    Constraints(minWidth = widths[i], maxWidth = widths[i], minHeight = buttonHeight, maxHeight = constraints.maxHeight),
                )
            }
        val height = if (inRow) placeables.maxOf { it.height } else placeables.sumOf { it.height } + gaps
        layout(width, height) {
            var x = width
            var y = 0
            placeables.forEach {
                if (inRow) {
                    x -= it.width
                    it.place(x, (height - it.height) / 2)
                    x -= gapPx
                } else {
                    it.place(width - it.width, y)
                    y += it.height + gapPx
                }
            }
        }
    }
}

/**
 * Sets the underlying dialog window's dim amount to [alpha] — [Dialog] has no
 * direct scrim-alpha param, unlike [ModalBottomSheet]'s `scrimColor`, so this reaches into the
 * window directly. Must be called from within a [Dialog]'s content. For the dialog and sheet
 * components only.
 */
@Composable
fun ApplyDialogScrim(alpha: Float) {
    val view = LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.setDimAmount(alpha)
    }
}
