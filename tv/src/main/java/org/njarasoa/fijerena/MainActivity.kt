package org.njarasoa.fijerena

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.navigation.TvNavHost
import org.njarasoa.fijerena.ui.theme.FirstVideoPlayerTheme
import org.njarasoa.fijerena.ui.theme.LocalUiScale

import org.njarasoa.fijerena.core.ui.utils.LocaleManager

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
        var themeId by mutableStateOf(appSettings.themeId)
        var uiStyleId by mutableStateOf(appSettings.uiStyleId)
        var uiScale by mutableStateOf(appSettings.uiScale)

        setContent {
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
            ) {
                FirstVideoPlayerTheme(themeId = themeId, styleId = uiStyleId) {
                    TvNavHost(
                        onThemeChanged = { newThemeId ->
                            appSettings.themeId = newThemeId
                            themeId = newThemeId
                        },
                        onUiStyleChanged = { newStyleId ->
                            appSettings.uiStyleId = newStyleId
                            uiStyleId = newStyleId
                        },
                        onUiScaleChanged = { newScale ->
                            appSettings.uiScale = newScale
                            uiScale = newScale
                        },
                    )
                }
            }
        }
    }

    // Unlike mobile, TV has no background-playback feature (PiP is mobile-only — see
    // docs/RELEASE_NOTES.md) — leaving to the launcher has no reason to leave the stream, the
    // decoder, and the wake lock running. isChangingConfigurations guards the (currently
    // theoretical, TV is fixed-orientation) config-change case.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            StreamingPlaybackService.getInstance()?.stop()
        }
    }

    private companion object {
        const val SPLASH_EXIT_SCALE = 1.15f
        const val SPLASH_EXIT_MS = 350L
    }

    /**
     * Every D-pad press, including the repeat stream from a held key, used to log unconditionally —
     * with string interpolation — on the one path where latency is most visible. Kept behind dev
     * mode because the timestamps are genuinely useful: comparing them against injected key events
     * is how the "cursor dead then replays" stall was shown to be a rendering backlog rather than
     * lost input.
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (logKeyEvents) {
            android.util.Log.i("MainActivity", "dispatchKeyEvent: action=${event.action}, code=${event.keyCode}")
        }
        return super.dispatchKeyEvent(event)
    }
}
