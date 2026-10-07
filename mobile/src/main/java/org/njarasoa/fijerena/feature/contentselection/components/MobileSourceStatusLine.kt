package org.njarasoa.fijerena.feature.contentselection.components

import android.text.format.DateUtils
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.home.SourceSyncStatus
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.CinemaWarning
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * The top bar's status line under the source name (phone home overhaul plan, Phase 2): a dot and
 * "Updating…" / "Update failed" / "Updated 2 hours ago". Nothing for a source that has never synced
 * (Jellyfin, M3U, a new Xtream source). [onFailedClick] opens the reason when the update failed.
 */
@Composable
fun MobileSourceStatusLine(
    status: SourceSyncStatus,
    lastSyncedAtMs: Long,
    onFailedClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (status == SourceSyncStatus.NONE) return
    // Ticks so "Updated 2 minutes ago" keeps counting while Home stays up; only this line recomposes.
    val now = rememberMinuteTick()
    val text =
        when (status) {
            SourceSyncStatus.UPDATING -> {
                stringResource(R.string.home_source_updating)
            }

            SourceSyncStatus.FAILED -> {
                stringResource(R.string.home_source_update_failed)
            }

            else -> {
                // Under a minute, getRelativeTimeSpanString says "0 minutes ago".
                val ago =
                    if (now - lastSyncedAtMs < DateUtils.MINUTE_IN_MILLIS) {
                        stringResource(R.string.live_sync_just_now)
                    } else {
                        DateUtils.getRelativeTimeSpanString(lastSyncedAtMs, now, DateUtils.MINUTE_IN_MILLIS).toString()
                    }
                stringResource(R.string.home_source_updated, ago)
            }
        }
    val dotColor =
        when (status) {
            SourceSyncStatus.UPDATING -> CinemaWarning
            SourceSyncStatus.FAILED -> CinemaError
            else -> CinemaSuccess
        }
    // Always the 48 dp touch height, so the top bar doesn't change size when the line turns tappable.
    val clickModifier =
        if (status == SourceSyncStatus.FAILED) Modifier.clickable(role = Role.Button, onClick = onFailedClick) else Modifier
    Row(
        modifier = modifier.minimumInteractiveComponentSize().then(clickModifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color = dotColor, pulsing = status == SourceSyncStatus.UPDATING)
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = CinemaTextSecondary,
            maxLines = 1,
            modifier = Modifier.padding(start = CinemaSpacing.xs),
        )
    }
}

/** The wall clock, re-read on each minute boundary. */
@Composable
private fun rememberMinuteTick(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(DateUtils.MINUTE_IN_MILLIS - now % DateUtils.MINUTE_IN_MILLIS)
        }
    }
    return now
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
                .background(color, CircleShape),
    )
}
