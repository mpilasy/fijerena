package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * The one TV empty state (TV UI audit, X7): an optional [icon], one sentence ([message]), an
 * optional [secondary] line and an optional single action, centred in the space it is given.
 * Nothing went wrong here — the list, guide or section simply has nothing to show — so nothing is
 * red and there is no Retry; real failures keep [TvErrorState].
 *
 * With an action ([actionLabel] and [onAction]) the button takes focus on entry. [onBack] also
 * takes the Back key, in `onPreviewKeyEvent`: with the button focused a `BackHandler` would miss
 * the first press (docs/NAVIGATION_GUIDE.md → "TV Back on Detail Screens"). Without an action
 * nothing here is focusable, so the screen keeps focus on its own controls.
 *
 * ```
 * TvEmptyState(
 *     message = stringResource(R.string.epg_guide_no_channels),
 *     icon = CinemaIcons.LiveTv,
 *     actionLabel = stringResource(R.string.common_back),
 *     onAction = onBack,
 *     onBack = onBack,
 * )
 * ```
 */
@Composable
fun TvEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    secondary: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    val hasAction = actionLabel != null && onAction != null
    val actionFocusRequester = remember { FocusRequester() }
    if (hasAction) {
        LaunchedEffect(Unit) { actionFocusRequester.requestFocusWithRetry() }
    }

    Box(
        modifier =
            modifier.fillMaxSize().onPreviewKeyEvent { event ->
                val isBack = onBack != null && event.key == Key.Back && event.type == KeyEventType.KeyUp
                if (isBack) onBack?.invoke()
                isBack
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.padding(Spacing.xl),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CinemaTextSecondary,
                    modifier = Modifier.size(TvDimensions.iconLarge),
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.headlineSmall,
                color = CinemaTextPrimary,
                textAlign = TextAlign.Center,
            )
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = CinemaTextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            if (hasAction) {
                CinemaPrimaryButton(
                    onClick = { onAction?.invoke() },
                    text = actionLabel.orEmpty(),
                    modifier = Modifier.padding(top = Spacing.sm).focusRequester(actionFocusRequester),
                )
            }
        }
    }
}
