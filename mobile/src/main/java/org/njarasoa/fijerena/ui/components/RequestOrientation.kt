package org.njarasoa.fijerena.ui.components

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * Holds the activity at [orientation] while this is in the composition. Requests stack: the
 * newest one still composed wins, and the manifest's portrait comes back when none is left.
 *
 * A screen putting back the value it found on entry is not enough: navigation starts the next
 * screen's effects before disposing the previous screen, so Start over's catch-up player (landscape)
 * was undone by the Live TV full screen it replaced, which then put back portrait.
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
