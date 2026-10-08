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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.home.SourceSyncStatus
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.CinemaWarning
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * The line under a tab root's title: the source's name and its last update — a dot and
 * "jellyxtream · Updated 2 hours ago" / "· Updating…" / "· Update failed"; no dot nor status for a
 * source that has never synced (Jellyfin, M3U, a new Xtream source) — and ▾ when [showsPicker]. The
 * name is cut first, so the status stays readable. [onClick] (null: not tappable) opens the source
 * picker, or with a single source the reason the update failed.
 */
@Composable
fun MobileSourceLine(
    name: String,
    status: SourceSyncStatus,
    lastSyncedAtMs: Long,
    showsPicker: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // Ticks so "Updated 2 minutes ago" keeps counting while the tab stays up; only this line recomposes.
    val now = rememberMinuteTick()
    val statusText =
        when (status) {
            SourceSyncStatus.NONE -> {
                null
            }

            SourceSyncStatus.UPDATING -> {
                stringResource(R.string.home_source_updating)
            }

            SourceSyncStatus.FAILED -> {
                stringResource(R.string.home_source_update_failed)
            }

            SourceSyncStatus.UPDATED -> {
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
    val switchDescription = stringResource(R.string.content_switch_provider_description_format, name)
    val clickModifier =
        when {
            onClick == null -> {
                Modifier
            }

            showsPicker -> {
                Modifier
                    .clickable(role = Role.DropdownList, onClick = onClick)
                    .semantics { contentDescription = switchDescription }
            }

            else -> {
                Modifier.clickable(role = Role.Button, onClick = onClick)
            }
        }
    // Always the 48 dp touch height, so the top bar keeps its size whatever the status.
    Row(
        modifier = modifier.minimumInteractiveComponentSize().then(clickModifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status != SourceSyncStatus.NONE) {
            StatusDot(
                color =
                    when (status) {
                        SourceSyncStatus.UPDATING -> CinemaWarning
                        SourceSyncStatus.FAILED -> CinemaError
                        else -> CinemaSuccess
                    },
                pulsing = status == SourceSyncStatus.UPDATING,
            )
            Spacer(modifier = Modifier.width(CinemaSpacing.xs))
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = CinemaTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (statusText != null) {
            Text(
                text = " · $statusText",
                style = MaterialTheme.typography.bodyMedium,
                color = CinemaTextSecondary,
                maxLines = 1,
            )
        }
        if (showsPicker) {
            Icon(
                imageVector = CinemaIcons.ArrowDropDown,
                contentDescription = null,
                tint = CinemaTextSecondary,
            )
        }
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
