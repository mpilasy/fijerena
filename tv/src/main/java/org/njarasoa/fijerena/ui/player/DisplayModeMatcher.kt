package org.njarasoa.fijerena.ui.player

import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.display.ModeSpec
import org.njarasoa.fijerena.core.player.display.displayModeToRequest
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService

/**
 * Match Frame Rate (the match frame rate plan): while the full-screen player shows, asks the
 * display for a mode whose refresh rate fits the frame rate of the content, through the window's
 * `preferredDisplayModeId` (it works from Android 6, so on the Shields too).
 *
 * A film (MF-Q5) pauses before the change and plays again when the display shows the new mode,
 * plus [SETTLE_MS], at most [MAX_HOLD_MS] in all: over HDMI the picture goes black for some
 * seconds, and the film must not play on in the black. Live (MF-Q3) never waits, and changes the
 * mode only when the rate family changes; the mode stays across zaps, because Live TV's full
 * screen keeps one [PlayerScreen]. [reset] gives the display back to the system mode (MF-Q4).
 */
class DisplayModeMatcher(
    private val window: Window,
    private val display: Display,
    private val displayManager: DisplayManager,
) {
    /**
     * The This Device switch is on, and on Android 12 and later the system's Match Content Frame
     * Rate is not at Never (MF-Q6): "Seamless only" and "Always" let the app ask.
     */
    fun allowed(settings: AppSettings): Boolean =
        settings.matchFrameRate &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    displayManager.matchContentFrameRateUserPreference != DisplayManager.MATCH_CONTENT_FRAMERATE_NEVER
            )

    /** Asks for a mode at each new rate of the content, until the coroutine stops. */
    suspend fun run(service: StreamingPlaybackService) {
        service.contentFrameRate.filterNotNull().distinctUntilChanged().collect { fps ->
            // The service sets the metadata where it resets the rate, so this is the stream of the rate.
            val live = service.currentMetadata.value.isLive
            val modes = display.supportedModes.map { it.toSpec() }
            displayModeToRequest(enabled = true, isLive = live, fps = fps, modes = modes, current = display.mode.toSpec())
                ?.let { switchTo(it, service, live) }
        }
    }

    /** Drops the app's request, when it made one: the system mode comes back. */
    fun reset() {
        if (window.attributes.preferredDisplayModeId != 0) request(0)
    }

    private suspend fun switchTo(
        mode: ModeSpec,
        service: StreamingPlaybackService,
        live: Boolean,
    ) {
        // Only a film that plays waits; live never does, and a paused film stays paused.
        val hold = !live && service.getPlayer()?.playWhenReady == true
        if (hold) service.pause()
        request(mode.id)
        if (hold) {
            withTimeoutOrNull(MAX_HOLD_MS) {
                modeIds().first { it == mode.id }
                delay(SETTLE_MS)
            }
            // The user can press Play during the wait.
            if (service.getPlayer()?.playWhenReady == false) service.resume()
        }
    }

    private fun request(modeId: Int) {
        val attributes = window.attributes
        attributes.preferredDisplayModeId = modeId
        window.attributes = attributes
    }

    /** The id of the display's mode, now and at each change. */
    private fun modeIds(): Flow<Int> =
        callbackFlow {
            val listener =
                object : DisplayManager.DisplayListener {
                    override fun onDisplayAdded(displayId: Int) = Unit

                    override fun onDisplayRemoved(displayId: Int) = Unit

                    override fun onDisplayChanged(displayId: Int) {
                        if (displayId == display.displayId) trySend(display.mode.modeId)
                    }
                }
            displayManager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
            send(display.mode.modeId)
            awaitClose { displayManager.unregisterDisplayListener(listener) }
        }

    private companion object {
        /** After the display shows the new mode, the TV and the AV receiver need time for the picture and the sound. */
        const val SETTLE_MS = 1_500L

        /** A film never waits longer than this, also when the display does not tell of the change. */
        const val MAX_HOLD_MS = 6_000L
    }
}

/** [Display.Mode] as [ModeSpec], which the shared code reads. */
internal fun Display.Mode.toSpec() = ModeSpec(modeId, physicalWidth, physicalHeight, refreshRate)

/**
 * Runs [DisplayModeMatcher] while the composable that calls it shows. Only [PlayerScreen] calls
 * it, so the Live TV preview never changes the mode. On dispose the system mode comes back.
 */
@Composable
fun MatchFrameRateEffect() {
    val activity = LocalActivity.current
    val view = LocalView.current
    val matcher =
        remember(activity, view) {
            val window = activity?.window
            val display = view.display
            val manager = view.context.getSystemService(DisplayManager::class.java)
            if (window != null && display != null && manager != null) DisplayModeMatcher(window, display, manager) else null
        }
    DisposableEffect(matcher) {
        onDispose { matcher?.reset() }
    }
    LaunchedEffect(matcher) {
        val settings = AppSettings(view.context.applicationContext)
        if (matcher != null && matcher.allowed(settings)) {
            StreamingPlaybackService.awaitInstanceOrNull()?.let { service -> matcher.run(service) }
        }
    }
}
