@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.contentselection.components

import android.text.format.DateUtils
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.home.SourceSyncState
import org.njarasoa.fijerena.core.ui.home.SourceSyncStatus
import org.njarasoa.fijerena.core.ui.home.SyncSummary
import org.njarasoa.fijerena.core.ui.home.rememberMinuteTick
import org.njarasoa.fijerena.core.ui.home.sourceStatusText
import org.njarasoa.fijerena.core.ui.home.syncSummaryText
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaBackground
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.CinemaWarning
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.input.TvOptionRow
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * Home's one line for all the profile's sources (O3): a dot and "Updated 2 hours ago" /
 * "Updating 1 of 3…" (with the running sync's phase once it has counted something) / "1 failed".
 * Nothing when no source syncs its catalogue (Jellyfin, M3U, a new Xtream source).
 */
@Composable
internal fun SyncSummaryLine(
    summary: SyncSummary,
    modifier: Modifier = Modifier,
) {
    // Ticks so "Updated 2 minutes ago" keeps counting while Home stays up.
    val text = syncSummaryText(summary, rememberMinuteTick()) ?: return
    StatusLine(summary.status, text, modifier)
}

/** A dot coloured by [status] and [text] beside it: the summary line, or one source's in the sheet. */
@Composable
private fun StatusLine(
    status: SourceSyncStatus,
    text: String,
    modifier: Modifier = Modifier,
) {
    val dotColor =
        when (status) {
            SourceSyncStatus.UPDATING -> CinemaTheme.colors.warning
            SourceSyncStatus.FAILED -> CinemaTheme.colors.danger
            else -> CinemaTheme.colors.success
        }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        StatusDot(color = dotColor, pulsing = status == SourceSyncStatus.UPDATING)
        CinemaText(
            text = text,
            role = TextRole.Label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

/**
 * The summary line's sheet: each of the profile's sources with its status and, after a failed
 * update, its reason — already with the raw detail in developer mode (ProviderSyncRunner). One
 * focus stop per source (the information is reachable by D-pad), focus on the first; OK and Back
 * close it.
 */
@Composable
internal fun SourcesStatusSheet(
    states: List<SourceSyncState>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isDevMode = remember { AppSettings(context.applicationContext).isDevMode }
    val now = rememberMinuteTick()
    val firstRow = remember { FocusRequester() }
    CinemaAlertDialog(
        initialFocus = firstRow,
        onDismissRequest = onDismiss,
        // The darkest surface behind the rows, as the TV pickers (TV UI audit #3).
        containerColor = CinemaTheme.colors.background,
        titleContentColor = CinemaTheme.colors.textPrimary,
        textContentColor = CinemaTheme.colors.textSecondary,
        title = { CinemaText(stringResource(R.string.home_sources_status_title), role = TextRole.Title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                states.forEachIndexed { index, state ->
                    val status = sourceStatusText(state, now)
                    val reason =
                        state.lastSyncError
                            ?.takeIf { state.status == SourceSyncStatus.FAILED }
                            ?.let { stringResource(R.string.home_source_update_failed_detail, it) }
                    TvOptionRow(
                        title = if (isDevMode) "${state.name} (${state.type})" else state.name,
                        subtitle = listOfNotNull(status, reason).joinToString("\n").ifEmpty { null },
                        role = TextRole.Heading,
                        selected = false,
                        onClick = onDismiss,
                        modifier = if (index == 0) Modifier.focusRequester(firstRow) else Modifier,
                    )
                }
            }
        },
        confirmButton = {
            CinemaButton(
                onClick = onDismiss,
                kind = ButtonKind.Other,
            ) { CinemaText(stringResource(R.string.common_close), role = TextRole.Label, tone = TextTone.Inherit) }
        },
    )
}

@Composable
private fun StatusDot(
    color: Color,
    pulsing: Boolean,
) {
    val alpha =
        if (pulsing) {
            val transition = rememberInfiniteTransition(label = "source_sync_pulse")
            transition.animateFloat(
                initialValue = 1f,
                targetValue = 0.3f,
                animationSpec = infiniteRepeatable(tween(CinemaAnimation.shimmerDurationMs), RepeatMode.Reverse),
                label = "source_sync_pulse_alpha",
            )
        } else {
            null
        }
    // The pulse is read in graphicsLayer's lambda, at draw time: read in the body it would
    // recompose Home every frame (see the live pulse in ContentTypeSelectionScreen).
    Box(
        modifier =
            Modifier
                .size(TvDimensions.liveDotSize)
                .graphicsLayer { this.alpha = alpha?.value ?: 1f }
                .background(color, CinemaTheme.shapes.identity),
    )
}
