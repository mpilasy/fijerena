@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.contentselection.components

import android.text.format.DateUtils
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.home.SourceSyncStatus
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaWarning
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import java.util.Date

/**
 * The wall clock, re-read on each minute boundary. Only the composable reading it recomposes, once
 * a minute — never the whole of Home.
 */
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

/** Home's clock, in the system's 12/24 h format. */
@Composable
fun HomeClock(modifier: Modifier = Modifier) {
    val now = rememberMinuteTick()
    Text(
        text = TimeFormat.formatClockTime(Date(now)),
        style = MaterialTheme.typography.titleLarge,
        color = CinemaTextSecondary,
        modifier = modifier,
    )
}

/**
 * The source pill's status: a dot and "Updating…" / "Update failed" / "Updated 2 hours ago".
 * Nothing for a source that has never synced (Jellyfin, M3U, a new Xtream source).
 */
@Composable
internal fun SourceSyncStatusLine(
    status: SourceSyncStatus,
    lastSyncedAtMs: Long,
    modifier: Modifier = Modifier,
) {
    if (status == SourceSyncStatus.NONE) return
    // Ticks so "Updated 2 minutes ago" keeps counting while Home stays up.
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
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        StatusDot(color = dotColor, pulsing = status == SourceSyncStatus.UPDATING)
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = CinemaTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
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
                .background(color, CircleShape),
    )
}
