package org.njarasoa.fijerena.feature.contentselection.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.CinemaWarning
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * The line under a tab root's title: one line for all the profile's sources (O3 of
 * docs/plans/20261012_multi-source-search-history-plan.md) — a dot and "Updated 2 hours ago" /
 * "Updating 1 of 3…" (with the running sync's phase once it has counted something) / "1 failed".
 * Tapping it opens the sources sheet ([onClick]); with no source that syncs (Jellyfin, M3U only)
 * it is empty and not tappable, keeping its 48 dp row so the top bar keeps its size.
 */
@Composable
fun MobileSyncSummaryLine(
    summary: SyncSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Ticks so "Updated 2 minutes ago" keeps counting while the tab stays up; only this line recomposes.
    val text = syncSummaryText(summary, rememberMinuteTick())
    val clickModifier = if (summary.opensSheet) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier = modifier.minimumInteractiveComponentSize().then(clickModifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (text != null) StatusText(summary.status, text)
    }
}

/** A dot coloured by [status], then [text]. */
@Composable
private fun StatusText(
    status: SourceSyncStatus,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(
            color =
                when (status) {
                    SourceSyncStatus.UPDATING -> CinemaTheme.colors.warning
                    SourceSyncStatus.FAILED -> CinemaTheme.colors.danger
                    else -> CinemaTheme.colors.success
                },
            pulsing = status == SourceSyncStatus.UPDATING,
        )
        Spacer(modifier = Modifier.width(CinemaSpacing.xs))
        CinemaText(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            role = TextRole.Body,
            tone = TextTone.Secondary,
        )
    }
}

/**
 * The summary line's sheet: each of the profile's sources with its status and, after a failed
 * update, its reason — already with the raw detail in developer mode (ProviderSyncRunner).
 */
@Composable
fun MobileSourcesStatusSheet(
    states: List<SourceSyncState>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isDevMode = remember { AppSettings(context.applicationContext).isDevMode }
    val now = rememberMinuteTick()
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            CinemaText(
                stringResource(R.string.home_sources_status_title),
                role = TextRole.Title,
                tone = TextTone.Inherit,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
            ) {
                states.forEach { state ->
                    Column {
                        CinemaText(
                            text = if (isDevMode) "${state.name} (${state.type})" else state.name,
                            role = TextRole.Body,
                            tone = TextTone.Inherit,
                            emphasis = true,
                        )
                        sourceStatusText(state, now)?.let { StatusText(state.status, it) }
                        state.lastSyncError?.takeIf { state.status == SourceSyncStatus.FAILED }?.let { error ->
                            CinemaText(
                                text = stringResource(R.string.home_source_update_failed_detail, error),
                                role = TextRole.Body,
                                tone = TextTone.Danger,
                            )
                        }
                    }
                }
            }
        },
        // The sole action: the app's dialog button, as other one-button dialogs (OK), not a text link.
        confirmButton = {
            CinemaDialogActionButton(onClick = onDismiss, kind = ButtonKind.Main) {
                CinemaText(
                    stringResource(R.string.common_close),
                    role = TextRole.Label,
                    tone = TextTone.Inherit,
                )
            }
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
    // recompose the line every frame.
    Box(
        modifier =
            Modifier
                .size(MobileDimensions.liveDotSize)
                .graphicsLayer { this.alpha = alpha?.value ?: 1f }
                .background(color, CinemaTheme.shapes.identity),
    )
}
