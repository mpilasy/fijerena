package org.njarasoa.fijerena.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle
import org.njarasoa.fijerena.core.ui.theme.cornerShape
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * A centred panel over the screen, in the screen's own layout tree (not a `Dialog` window): for a
 * picker over the player, where a dialog window over the video can break the video surface. The
 * Look and Feel gives its corners and scrim (`UiDialogTokens`) and the theme its surface colour, as
 * for `CinemaAlertDialog`; on TV it stays centred in every Look and Feel (D3).
 *
 * Focus stays in the panel (CONTRIBUTING.md → In-tree overlays trap focus). Back closes it with
 * [onDismiss]. The caller puts the initial focus in [content].
 *
 * The [title] stays at the top and the [footer] (the Cancel button) at the bottom; only [content]
 * (the rows) scrolls between them, to the focused row. The rows run to the edges of that part, so
 * a list that does not fit shows that it goes on, with no blank band under it.
 */
@Composable
fun TvOverlayPanel(
    onDismiss: () -> Unit,
    title: String? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalUiStyle.current.dialog
    val screenHeight = Dp(LocalConfiguration.current.screenHeightDp.toFloat())

    // Inert fallback: Back is taken in onPreviewKeyEvent below while focus is on a row, since a
    // focused TV Button/Surface can swallow the first press before BackHandler sees it
    // (CONTRIBUTING.md → Back on TV).
    BackHandler { onDismiss() }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CinemaTheme.colors.mediaScrim.copy(alpha = tokens.scrimAlpha))
                .onPreviewKeyEvent { keyEvent ->
                    val isBack = keyEvent.key == Key.Back
                    // Close on KeyUp, so the release does not land on the button focus returns to.
                    if (isBack && keyEvent.type == KeyEventType.KeyUp) onDismiss()
                    isBack
                },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier
                    .width(TvDimensions.dialogWidth)
                    .heightIn(max = screenHeight * PANEL_SCREEN_FRACTION)
                    .padding(Spacing.xxl),
            shape = cornerShape(tokens.cornerRadius),
            color = CinemaTheme.colors.surface,
        ) {
            Column(
                modifier =
                    Modifier
                        // Keep focus inside the panel: it is in the screen's own tree, not a Dialog window, so a D-pad
                        // press past its edge would move to the controls behind it. focusProperties sits right before
                        // focusGroup so the exit belongs to the group; without the group, focus froze on the first row.
                        .focusProperties { onExit = { cancelFocusChange() } }
                        .focusGroup(),
            ) {
                if (title != null) {
                    CinemaText(
                        text = title,
                        role = TextRole.Title,
                        tone = TextTone.Primary,
                        emphasis = true,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = Spacing.xxl, end = Spacing.xxl, top = Spacing.xxl, bottom = Spacing.sm),
                    )
                }
                // The rows: the only part that scrolls. Its padding is inside the scroll, so the
                // rows meet the title and the footer while the list scrolls.
                Column(
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = Spacing.xxl,
                                end = Spacing.xxl,
                                top = if (title == null) Spacing.xxl else Spacing.xs,
                                bottom = if (footer == null) Spacing.xxl else Spacing.xs,
                            ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    content = content,
                )
                if (footer != null) {
                    Column(
                        modifier = Modifier.padding(start = Spacing.xxl, end = Spacing.xxl, top = Spacing.sm, bottom = Spacing.xxl),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        content = footer,
                    )
                }
            }
        }
    }
}

/** The panel's tallest height, as a part of the screen height. */
private const val PANEL_SCREEN_FRACTION = 0.95f
