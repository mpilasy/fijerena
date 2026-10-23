package org.njarasoa.fijerena

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.ui.history.ProvideSourceBadges
import org.njarasoa.fijerena.core.ui.theme.LocalPosterBackdrop
import org.njarasoa.fijerena.core.ui.utils.LocaleManager
import org.njarasoa.fijerena.navigation.TvNavHost
import org.njarasoa.fijerena.ui.theme.FirstVideoPlayerTheme
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.autoUiScale

class MainActivity : ComponentActivity() {
    /** Read once — a SharedPreferences hit per key event would be its own problem. */
    private val logKeyEvents by lazy { AppSettings(applicationContext).isDevMode }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate() — this is what paints the marble over the cold-start
        // window instead of leaving it bare black until Compose's first frame.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Hold the splash until Compose has actually drawn, then hand off to AppLoadingScreen,
        // which carries the same marble on the same black while the nav host finishes init.
        var contentReady = false
        splash.setKeepOnScreenCondition { !contentReady }
        splash.setOnExitAnimationListener { provider ->
            provider.view
                .animate()
                .alpha(0f)
                .scaleX(SPLASH_EXIT_SCALE)
                .scaleY(SPLASH_EXIT_SCALE)
                .setDuration(SPLASH_EXIT_MS)
                .withEndAction { provider.remove() }
                .start()
        }

        val appSettings = AppSettings(applicationContext)
        // The active profile's colour theme, Look and Feel and poster background: follows a profile switch, a change in
        // Settings and one received from another device. Before a profile is picked, the last one used.
        val look = appSettings.observeLook()
        val initialLook = appSettings.look(appSettings.activeProfileId)
        var uiScale by mutableStateOf(appSettings.uiScaleChoice ?: autoUiScale(this))

        setContent {
            val (themeId, uiStyleId, posterBackdrop) = look.collectAsState(initial = initialLook).value
            val currentDensity = LocalDensity.current
            // Create a scaled density based on the user's preference.
            // This ensures that all dp and sp values are automatically scaled.
            // We only scale the base density, not fontScale, to avoid double-scaling text.
            val scaledDensity =
                Density(
                    density = currentDensity.density * uiScale,
                    fontScale = currentDensity.fontScale,
                )

            // First composition is close enough to first draw here: the nav host renders
            // AppLoadingScreen immediately, so the splash never lifts onto an empty window.
            LaunchedEffect(Unit) { contentReady = true }

            CompositionLocalProvider(
                LocalUiScale provides uiScale,
                LocalDensity provides scaledDensity,
                LocalPosterBackdrop provides posterBackdrop,
            ) {
                FirstVideoPlayerTheme(themeId = themeId, styleId = uiStyleId) {
                    ProvideSourceBadges {
                        TvNavHost(
                            onUiScaleChanged = { choice ->
                                appSettings.uiScaleChoice = choice
                                uiScale = choice ?: autoUiScale(this@MainActivity)
                            },
                        )
                    }
                }
            }
        }
    }

    // Unlike mobile, TV has no background-playback feature (PiP is mobile-only — see
    // docs/RELEASE_NOTES.md) — leaving to the launcher has no reason to leave the stream, the
    // decoder, and the wake lock running. isChangingConfigurations guards the (currently
    // theoretical, TV is fixed-orientation) config-change case. stopAndRelease (not stop) fully
    // tears down the native player/decoder buffers instead of just halting playback — see its
    // kdoc for why leaving them resident caused freezes browsing categories afterward.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            StreamingPlaybackService.getInstance()?.stopAndRelease()
        }
    }

    private companion object {
        const val SPLASH_EXIT_SCALE = 1.15f
        const val SPLASH_EXIT_MS = 350L

        // A D-pad key and the direction focus enters the screen in when it comes back after a loss.
        val FOCUS_DIRECTIONS =
            mapOf(
                android.view.KeyEvent.KEYCODE_DPAD_UP to android.view.View.FOCUS_UP,
                android.view.KeyEvent.KEYCODE_DPAD_DOWN to android.view.View.FOCUS_DOWN,
                android.view.KeyEvent.KEYCODE_DPAD_LEFT to android.view.View.FOCUS_LEFT,
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT to android.view.View.FOCUS_RIGHT,
                android.view.KeyEvent.KEYCODE_DPAD_CENTER to android.view.View.FOCUS_DOWN,
            )
    }

    /**
     * Every D-pad press, including the repeat stream from a held key, used to log unconditionally —
     * with string interpolation — on the one path where latency is most visible. Kept behind dev
     * mode because the timestamps are genuinely useful: comparing them against injected key events
     * is how the "cursor dead then replays" stall was shown to be a rendering backlog rather than
     * lost input.
     */
    // RestrictedApi is a false positive here: lint resolves the override to androidx.core's
    // @RestrictTo ComponentActivity.dispatchKeyEvent, but this overrides Activity's public one.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (logKeyEvents) {
            android.util.Log.i("MainActivity", "dispatchKeyEvent: action=${event.action}, code=${event.keyCode}")
        }
        // Focus lost: the focused control left the screen (removed, or scrolled out of a list) and
        // nothing in the window holds focus, so every D-pad press went nowhere until Back. The
        // press gives focus back to the screen instead, in its own direction (Up takes the bottom
        // control on screen, as moving up into it would), and is noted in Diagnostics.
        if (event.action == android.view.KeyEvent.ACTION_DOWN && currentFocus == null) {
            val direction = FOCUS_DIRECTIONS[event.keyCode]
            val content = findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)
            if (direction != null && content != null) {
                val restored = (listOf(direction) + FOCUS_DIRECTIONS.values).distinct().any { content.requestFocus(it) }
                if (restored) {
                    org.njarasoa.fijerena.ui.DeadKeyDetector
                        .focusRestored(event)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event).also { handled ->
            org.njarasoa.fijerena.ui.DeadKeyDetector
                .onKey(event, handled)
        }
    }
}
