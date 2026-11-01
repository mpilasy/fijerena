package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.RetryWhenOnline
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton

/**
 * The phone error state (the TV's is `TvErrorState`): [title] over [message] (left out when blank),
 * a Retry button and a Back button when [onBack] is set, centred in the space it is given. Retries
 * by itself, once, when the network comes back ([RetryWhenOnline]). Use it for real failures only;
 * an empty result is [MobileEmptyState].
 *
 * [extra] goes under the buttons (a film's or show's other copies); with it, the state scrolls.
 * Without it, the state does not scroll, so it can also be a lazy list's item.
 */
@Composable
fun MobileErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.common_error),
    retryLabel: String = stringResource(R.string.common_retry),
    onBack: (() -> Unit)? = null,
    backLabel: String = stringResource(R.string.common_back),
    extra: (@Composable () -> Unit)? = null,
) {
    RetryWhenOnline(onRetry)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
            modifier =
                (if (extra != null) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(CinemaSpacing.xl),
        ) {
            CinemaText(text = title, role = TextRole.Display, tone = TextTone.Danger, textAlign = TextAlign.Center)
            if (message.isNotBlank()) {
                CinemaText(text = message, tone = TextTone.Secondary, textAlign = TextAlign.Center)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.md)) {
                CinemaButton(onClick = onRetry) { CinemaText(retryLabel) }
                if (onBack != null) {
                    CinemaButton(kind = ButtonKind.Other, onClick = onBack) { CinemaText(backLabel) }
                }
            }
            extra?.invoke()
        }
    }
}
