@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.ui.components.MitohanaLoading
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaSurface
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

@Composable
fun IdleContent(onBack: () -> Unit) {
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier.padding(Spacing.xl),
    ) {
        Text(
            text = "Ready to play",
            color = CinemaTextPrimary,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        CinemaSecondaryButton(
            onClick = onBack,
            text = "Back",
            modifier = Modifier.padding(Spacing.xs),
        )
    }
}

@Composable
fun BufferingContent() {
    MitohanaLoading(
        style = MaterialTheme.typography.headlineSmall,
        color = CinemaAccent,
    )
}

@Composable
fun EndedContent(onBack: () -> Unit) {
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier.padding(Spacing.xl),
    ) {
        Text(
            text = "Playback ended",
            color = CinemaTextPrimary,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        CinemaSecondaryButton(
            onClick = onBack,
            text = "Back",
        )
    }
}

@Composable
fun ErrorContent(
    error: PlaybackState.Error,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val isDevMode = appSettings.isDevMode

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CinemaBackground.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = CenterHorizontally,
            modifier =
                Modifier
                    .padding(Spacing.xxl)
                    .width(TvDimensions.dialogWidth),
        ) {
            // Error icon/title
            Text(
                text = "⚠️ Playback Error",
                color = CinemaError,
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            // User-friendly error message
            Text(
                text = error.message,
                color = CinemaTextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // Technical details in dev mode
            if (isDevMode && error.exception != null) {
                Spacer(modifier = Modifier.height(Spacing.xl))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = CinemaSurface,
                    shape = RoundedCornerShape(CornerRadius.small),
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                    ) {
                        Text(
                            text = "Technical Details (Dev Mode):",
                            color = CinemaAccent,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))

                        val exception = error.exception
                        val errorDetails =
                            buildString {
                                append("Type: ${exception?.javaClass?.simpleName ?: "Unknown"}\n")
                                exception?.message?.let { msg ->
                                    append("Message: $msg\n")
                                }
                                // Get stack trace preview (first 5 lines)
                                val stackTrace =
                                    exception
                                        ?.stackTraceToString()
                                        ?.lines()
                                        ?.take(5)
                                        ?.joinToString("\n") ?: "No stack trace available"
                                append("\nStack Trace:\n$stackTrace")
                            }

                        Text(
                            text = errorDetails,
                            color = CinemaTextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(TvDimensions.statsOverlayPanelHeight)
                                    .focusable(false)
                                    .verticalScroll(rememberScrollState()),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xxl + Spacing.xs))

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                CinemaPrimaryButton(
                    onClick = onRetry,
                    text = "Retry",
                    modifier = Modifier.width(120.dp).height(TvDimensions.trackItemHeight),
                )
                CinemaSecondaryButton(
                    onClick = onBack,
                    text = "Back",
                    modifier = Modifier.width(120.dp).height(TvDimensions.trackItemHeight),
                )
            }
        }
    }
}
