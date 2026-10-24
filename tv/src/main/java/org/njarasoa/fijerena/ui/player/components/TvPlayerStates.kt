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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.BufferingDetails
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.PlayerWait
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.colorOf
import org.njarasoa.fijerena.core.ui.components.textStyleOf
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaSurface
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * The player's wait: the iris, reel or ring for [metadata] in the centre, its logo or name at the
 * top left when [showBadge] (in the banner's logo place), and Expert mode's details under it.
 * [tint] is the colour taken from the frozen frame, null for the accent.
 */
@Composable
fun BufferingContent(
    metadata: PlayerMetadata,
    showBadge: Boolean,
    tint: Color? = null,
) {
    val context = LocalContext.current
    val isDevMode = remember { AppSettings(context.applicationContext).isDevMode }
    PlayerWait(
        metadata = metadata,
        tint = tint,
        showBadge = showBadge,
        showDetails = isDevMode,
        // Where the banner's top bar puts its logo, so one replaces the other in place.
        badgeModifier = Modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.xl),
    )
}

@Composable
fun EndedContent(onBack: () -> Unit) {
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier.padding(Spacing.xl),
    ) {
        CinemaText(text = stringResource(R.string.player_playback_ended), role = TextRole.Title)
        Spacer(modifier = Modifier.height(Spacing.md))
        CinemaSecondaryButton(
            onClick = onBack,
            text = stringResource(R.string.player_back),
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
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { retryFocus.requestFocusWithRetry() }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CinemaTheme.colors.background.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = CenterHorizontally,
            modifier =
                Modifier
                    .padding(Spacing.xxl)
                    .width(TvDimensions.dialogWidth),
        ) {
            CinemaText(text = stringResource(R.string.player_playback_error_prefix), role = TextRole.Display, tone = TextTone.Danger)

            Spacer(modifier = Modifier.height(Spacing.lg))

            CinemaText(text = error.message, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            if (isDevMode && error.exception != null) {
                Spacer(modifier = Modifier.height(Spacing.xl))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = CinemaTheme.colors.surface,
                    shape = CinemaTheme.shapes.chip,
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                    ) {
                        CinemaText(text = stringResource(R.string.player_technical_details), role = TextRole.Label, tone = TextTone.Accent)
                        Spacer(modifier = Modifier.height(Spacing.xs))

                        val typeLabel = stringResource(R.string.player_error_type_label)
                        val messageLabel = stringResource(R.string.player_error_message_label)
                        val unknownText = stringResource(R.string.player_error_unknown)
                        val stackTraceLabel = stringResource(R.string.player_error_stack_trace_label)
                        val noStackTraceText = stringResource(R.string.player_no_stack_trace)

                        val exception = error.exception
                        val errorDetails =
                            buildString {
                                append("$typeLabel${exception?.javaClass?.simpleName ?: unknownText}\n")
                                exception?.message?.let { msg ->
                                    append("$messageLabel$msg\n")
                                }
                                val stackTrace =
                                    exception
                                        ?.stackTraceToString()
                                        ?.lines()
                                        ?.take(5)
                                        ?.joinToString("\n") ?: noStackTraceText
                                append("\n$stackTraceLabel\n$stackTrace")
                            }

                        // look-ok: developer-mode stack trace, monospace; its size and colour are the theme's roles
                        Text(
                            text = errorDetails,
                            color = colorOf(TextTone.Secondary),
                            style = textStyleOf(TextRole.Caption),
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                CinemaPrimaryButton(
                    onClick = onRetry,
                    text = stringResource(R.string.player_retry),
                    modifier =
                        Modifier
                            .width(TvDimensions.epgTimeSlotWidth)
                            .height(TvDimensions.trackItemHeight)
                            .focusRequester(retryFocus),
                )
                CinemaSecondaryButton(
                    onClick = onBack,
                    text = stringResource(R.string.player_back),
                    modifier = Modifier.width(TvDimensions.epgTimeSlotWidth).height(TvDimensions.trackItemHeight),
                )
            }
        }
    }
}
