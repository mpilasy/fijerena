package org.njarasoa.fijerena

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.sports.GameReminderAlerts
import org.njarasoa.fijerena.core.network.sports.GameReminderNotifications
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.history.ProvideSourceBadges
import org.njarasoa.fijerena.core.ui.theme.LocalPosterBackdrop
import org.njarasoa.fijerena.core.ui.trailer.TrailerHost
import org.njarasoa.fijerena.core.ui.utils.LocaleManager
import org.njarasoa.fijerena.navigation.MobileNavHost
import org.njarasoa.fijerena.ui.components.PinRequestHost
import org.njarasoa.fijerena.ui.theme.FirstVideoPlayerTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate(): it paints the marble over the cold-start window, not
        // bare black until Compose's first frame.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Hold the splash until Compose has drawn, then hand off to AppLoadingScreen, which shows
        // the same marble on the same black while the nav host starts.
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

        // PiP auto-enter is off by default.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            setPictureInPictureParams(
                android.app.PictureInPictureParams
                    .Builder()
                    .setAutoEnterEnabled(false)
                    .build(),
            )
        }

        // A tapped game reminder asks to tune its channel; a recreated activity's intent was handled.
        if (savedInstanceState == null) GameReminderAlerts.handleLaunchIntent(intent)
        // Its "Manage reminders" asks for the Reminders page.
        if (savedInstanceState == null) GameReminderNotifications.handleLaunchIntent(applicationContext, intent)

        val appSettings = AppSettings(applicationContext)
        // The active profile's colour theme, Look and Feel and poster background: follows a profile switch, a change in
        // Settings and one received from another device. Before a profile is picked, the last one used.
        val look = appSettings.observeLook()
        val initialLook = appSettings.look(appSettings.activeProfileId)

        setContent {
            val (themeId, uiStyleId, posterBackdrop) = look.collectAsState(initial = initialLook).value
            // The first composition is close enough to the first draw: the nav host shows
            // AppLoadingScreen at once, so the splash never lifts onto an empty window.
            LaunchedEffect(Unit) { contentReady = true }

            FirstVideoPlayerTheme(themeId = themeId, styleId = uiStyleId) {
                CompositionLocalProvider(LocalPosterBackdrop provides posterBackdrop) {
                    ProvideSourceBadges {
                        TrailerHost {
                            MobileNavHost()
                        }
                        PinRequestHost()
                    }
                }
            }
        }
    }

    // A game reminder tapped while the app runs: the nav host tunes its channel, or opens the
    // Reminders page for its "Manage reminders".
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        GameReminderAlerts.handleLaunchIntent(intent)
        GameReminderNotifications.handleLaunchIntent(applicationContext, intent)
    }

    private companion object {
        const val SPLASH_EXIT_SCALE = 1.15f
        const val SPLASH_EXIT_MS = 350L
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val playbackViewModel = ViewModelProvider(this)[PlaybackViewModel::class.java]
        playbackViewModel.updatePictureInPictureMode(isInPictureInPictureMode)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Fallback below Android 12, without auto-enter.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            val playbackViewModel = ViewModelProvider(this)[PlaybackViewModel::class.java]
            val state = playbackViewModel.playbackState.value
            // Live only: the same rule as the S+ auto-enter params of the player screens.
            if (playbackViewModel.currentMetadata.value.isLive &&
                (
                    state is org.njarasoa.fijerena.core.player.model.PlaybackState.Playing ||
                        state is org.njarasoa.fijerena.core.player.model.PlaybackState.Buffering
                )
            ) {
                enterPictureInPictureMode(
                    android.app.PictureInPictureParams
                        .Builder()
                        .build(),
                )
            }
        }
    }
}
