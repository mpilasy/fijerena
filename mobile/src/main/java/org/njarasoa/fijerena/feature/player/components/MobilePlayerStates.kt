package org.njarasoa.fijerena.feature.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.MitohanaLoading
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        MitohanaLoading(
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun ErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.padding(Spacing.xl),
        ) {
            Text(
                text = stringResource(R.string.player_error),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                CinemaButton(onClick = onRetry) {
                    Text(stringResource(R.string.player_retry))
                }
                CinemaButton(onClick = onBack) {
                    Text(stringResource(R.string.player_back))
                }
            }
        }
    }
}

@Composable
fun ErrorOverlay(
    error: PlaybackState.Error,
    isDevMode: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier.padding(Spacing.xl),
        color = CinemaBackground.copy(alpha = CinemaAlpha.overlayMedium),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Spacing.lg),
        ) {
            Text(
                text = stringResource(R.string.player_error),
                style = MaterialTheme.typography.headlineSmall,
                color = CinemaError,
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium,
                color = CinemaTextPrimary,
            )
            // Developer mode: the raw error beneath the friendly one.
            val rawMessage = error.exception?.message
            if (isDevMode && rawMessage != null) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = rawMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = CinemaTextSecondary,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                CinemaButton(onClick = onRetry) {
                    Text(stringResource(R.string.player_retry))
                }
                CinemaButton(onClick = onBack) {
                    Text(stringResource(R.string.player_back))
                }
            }
        }
    }
}

/**
 * "Connection too slow for this video (needs ~60 Mbps, getting ~20 Mbps)" at the top of the
 * picture while the stream starves (P4, docs/plans/20261004_playback-capability-errors-plan.md).
 */
@Composable
fun SlowConnectionBanner(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(top = Spacing.xl),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = Spacing.md),
            color = CinemaBackground.copy(alpha = CinemaAlpha.overlayMedium),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = CinemaTextPrimary,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }
    }
}
