package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.RetryWhenOnline
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * The TV error screen: [title] over [message], a Retry button that takes focus on entry, and a Back
 * button when [backLabel] is set. Retries by itself, once, when the network comes back
 * ([RetryWhenOnline]). [onBack] also takes the Back key, in `onPreviewKeyEvent` — with Retry
 * focused, a `BackHandler` would miss the first press (docs/NAVIGATION_GUIDE.md → "TV Back on Detail
 * Screens"). docs/plans/20261002_next-level-rock-solid-resilience-plan.md → R-15.
 */
@Composable
fun TvErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.common_error),
    retryLabel: String = stringResource(R.string.common_retry),
    onBack: (() -> Unit)? = null,
    backLabel: String? = null,
) {
    val retryFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { retryFocusRequester.requestFocusWithRetry() }
    RetryWhenOnline(onRetry)

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
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            modifier = Modifier.padding(Spacing.xl),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displayMedium,
                color = CinemaError,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = CinemaTextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                CinemaPrimaryButton(
                    onClick = onRetry,
                    text = retryLabel,
                    modifier = Modifier.focusRequester(retryFocusRequester),
                )
                if (onBack != null && backLabel != null) {
                    CinemaSecondaryButton(onClick = onBack, text = backLabel)
                }
            }
        }
    }
}
