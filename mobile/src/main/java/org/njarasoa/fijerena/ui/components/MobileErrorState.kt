package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton

/**
 * The phone error state (the TV's is `TvErrorState`): [title] over [message] and a Retry button,
 * centred in the space it is given. Retries by itself, once, when the network comes back
 * ([RetryWhenOnline]). Use it for real failures only; an empty result is [MobileEmptyState].
 */
@Composable
fun MobileErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.common_error),
) {
    RetryWhenOnline(onRetry)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
            modifier = Modifier.padding(CinemaSpacing.xl),
        ) {
            CinemaText(text = title, role = TextRole.Display, tone = TextTone.Danger, textAlign = TextAlign.Center)
            CinemaText(text = message, tone = TextTone.Secondary, textAlign = TextAlign.Center)
            CinemaButton(onClick = onRetry) { CinemaText(stringResource(R.string.common_retry)) }
        }
    }
}
