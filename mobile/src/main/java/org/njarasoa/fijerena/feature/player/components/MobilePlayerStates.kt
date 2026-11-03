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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.player.diagnostics.Redact
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaPanel
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.PanelCorner
import org.njarasoa.fijerena.core.ui.components.PanelFill
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.WaitIndicator
import org.njarasoa.fijerena.core.ui.components.WaitKind
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing

/** Before the stream is known: the wait picture for the route's kind of content, nothing named yet. */
@Composable
fun LoadingScreen(kind: WaitKind) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        WaitIndicator(kind = kind)
    }
}

@Composable
fun ErrorOverlay(
    error: PlaybackState.Error,
    nerd: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    CinemaPanel(
        modifier = Modifier.padding(Spacing.xl),
        fill = PanelFill.Notice,
        corner = PanelCorner.Card,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Spacing.lg),
        ) {
            CinemaText(
                text = stringResource(R.string.player_error),
                role = TextRole.Display,
                tone = TextTone.Danger,
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            CinemaText(
                text = error.message,
                role = TextRole.Body,
                tone = TextTone.Primary,
            )
            // A Nerd sees the raw error beneath the friendly one.
            // Masked: a raw message can hold the stream URL with the login and password.
            val rawMessage = error.exception?.message?.let(Redact::text)
            if (nerd && rawMessage != null) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                CinemaText(
                    text = rawMessage,
                    role = TextRole.BodySmall,
                    tone = TextTone.Secondary,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                CinemaButton(onClick = onRetry) {
                    CinemaText(
                        stringResource(R.string.player_retry),
                        role = TextRole.Label,
                        tone = TextTone.Inherit,
                    )
                }
                CinemaButton(kind = ButtonKind.Other, onClick = onBack) {
                    CinemaText(
                        stringResource(R.string.player_back),
                        role = TextRole.Label,
                        tone = TextTone.Inherit,
                    )
                }
            }
        }
    }
}

/**
 * "Connection too slow for this video (needs ~60 Mbps, getting ~20 Mbps)" at the top of the
 * picture while the stream starves (P4, the playback capability errors plan).
 */
@Composable
fun SlowConnectionBanner(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(top = Spacing.xl),
        contentAlignment = Alignment.TopCenter,
    ) {
        CinemaPanel(
            modifier = Modifier.padding(horizontal = Spacing.md),
            fill = PanelFill.Notice,
            corner = PanelCorner.Card,
        ) {
            CinemaText(
                text = text,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                role = TextRole.Body,
                tone = TextTone.Primary,
            )
        }
    }
}
