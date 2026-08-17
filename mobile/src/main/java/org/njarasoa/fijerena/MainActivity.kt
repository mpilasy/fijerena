package org.njarasoa.fijerena

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.navigation.MobileNavHost
import org.njarasoa.fijerena.ui.theme.FirstVideoPlayerTheme

import org.njarasoa.fijerena.core.ui.utils.LocaleManager

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate() — this is what paints the marble over the cold-start
        // window instead of leaving it bare black until Compose's first frame.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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

        // Ensure PiP auto-enter is disabled by default
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            setPictureInPictureParams(
                android.app.PictureInPictureParams.Builder()
                    .setAutoEnterEnabled(false)
                    .build()
            )
        }

        val appSettings = AppSettings(applicationContext)
        var themeId by mutableStateOf(appSettings.themeId)
        var uiStyleId by mutableStateOf(appSettings.uiStyleId)

        setContent {
            // First composition is close enough to first draw here: the nav host renders
            // AppLoadingScreen immediately, so the splash never lifts onto an empty window.
            LaunchedEffect(Unit) { contentReady = true }

            FirstVideoPlayerTheme(themeId = themeId, styleId = uiStyleId) {
                MobileNavHost(
                    onThemeChanged = { newThemeId ->
                        appSettings.themeId = newThemeId
                        themeId = newThemeId
                    },
                    onUiStyleChanged = { newStyleId ->
                        appSettings.uiStyleId = newStyleId
                        uiStyleId = newStyleId
                    },
                )
            }
        }
    }

    private companion object {
        const val SPLASH_EXIT_SCALE = 1.15f
        const val SPLASH_EXIT_MS = 350L
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val playbackViewModel = ViewModelProvider(this)[PlaybackViewModel::class.java]
        playbackViewModel.updatePictureInPictureMode(isInPictureInPictureMode)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Fallback for Android < 12 if auto-enter is not supported or failed
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            val playbackViewModel = ViewModelProvider(this)[PlaybackViewModel::class.java]
            val state = playbackViewModel.playbackState.value
            if (state is org.njarasoa.fijerena.core.player.model.PlaybackState.Playing ||
                state is org.njarasoa.fijerena.core.player.model.PlaybackState.Buffering
            ) {
                enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build())
            }
        }
    }
}
