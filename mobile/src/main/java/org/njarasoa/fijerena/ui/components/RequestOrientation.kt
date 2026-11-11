package org.njarasoa.fijerena.ui.components

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * Holds the activity at [orientation] while this is in the composition. Requests stack: the
 * newest one still composed wins, and the manifest's portrait comes back when none is left.
 *
 * Putting back the value found on entry is not enough: navigation starts the next screen's effects
 * before it disposes the previous screen, so the Live TV full screen undid Start over's landscape
 * catch-up player by putting back portrait.
 */
@Composable
fun RequestOrientation(
    activity: Activity?,
    orientation: Int,
) {
    DisposableEffect(activity, orientation) {
        val request = OrientationRequest(orientation)
        requests += request
        activity?.requestedOrientation = orientation
        onDispose {
            requests -= request
            activity?.requestedOrientation = requests.lastOrNull()?.orientation ?: ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
}

// Identity, not value: two requests for the same orientation are still two requests.
private class OrientationRequest(
    val orientation: Int,
)

private val requests = mutableListOf<OrientationRequest>()
