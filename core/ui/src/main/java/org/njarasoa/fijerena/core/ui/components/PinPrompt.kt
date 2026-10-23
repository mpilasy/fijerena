package org.njarasoa.fijerena.core.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.njarasoa.fijerena.core.network.profile.ProfilePins
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.PinStyle

/**
 * The PIN prompt's dots: one per digit, filled for each of the [typed] ones. Read aloud as the
 * count, never the digits.
 */
@Composable
fun PinDots(
    typed: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.semantics { contentDescription = "$typed / ${ProfilePins.LENGTH}" },
        horizontalArrangement = Arrangement.spacedBy(PinStyle.dotGap),
    ) {
        repeat(ProfilePins.LENGTH) { index ->
            val dot = Modifier.size(PinStyle.dotSize)
            Box(
                if (index < typed) {
                    dot.background(PinStyle.filled, PinStyle.dotShape)
                } else {
                    dot.border(PinStyle.dotOutline, PinStyle.empty, PinStyle.dotShape)
                },
            )
        }
    }
}

/** What a PIN check says under the dots, or null when it was right. */
fun ProfilePins.Check.message(
    context: Context,
    now: Long = System.currentTimeMillis(),
): String? =
    when (this) {
        ProfilePins.Check.Right -> {
            null
        }

        is ProfilePins.Check.Wrong -> {
            if (waitUntil > now) {
                context.getString(R.string.pin_wrong_wait, waitText(context, waitUntil - now))
            } else {
                context.getString(R.string.pin_wrong_left, triesLeft)
            }
        }

        is ProfilePins.Check.Waiting -> {
            context.getString(R.string.pin_waiting, waitText(context, until - now))
        }
    }

private fun waitText(
    context: Context,
    ms: Long,
): String {
    val seconds = ((ms + 999) / 1000).coerceAtLeast(1)
    return if (seconds < 60) {
        context.getString(R.string.pin_seconds, seconds.toInt())
    } else {
        context.getString(R.string.pin_minutes, ((seconds + 59) / 60).toInt())
    }
}
