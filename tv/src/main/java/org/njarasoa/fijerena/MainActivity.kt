package org.njarasoa.fijerena

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.navigation.TvNavHost
import org.njarasoa.fijerena.ui.theme.FirstVideoPlayerTheme
import org.njarasoa.fijerena.ui.theme.LocalUiScale

class MainActivity : ComponentActivity() {
    private val playbackViewModel: PlaybackViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appSettings = AppSettings(applicationContext)
        var themeId by mutableStateOf(appSettings.themeId)
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

            CompositionLocalProvider(
                LocalUiScale provides uiScale,
                LocalDensity provides scaledDensity,
            ) {
                FirstVideoPlayerTheme(themeId = themeId) {
                    TvNavHost(
                        onThemeChanged = { newThemeId ->
                            appSettings.themeId = newThemeId
                            themeId = newThemeId
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

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        @Suppress("DEPRECATION")
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playbackViewModel.updatePictureInPictureMode(isInPictureInPictureMode)
    }

    override fun onUserLeaveHint() {
        @Suppress("DEPRECATION")
        super.onUserLeaveHint()
        // Fallback for Android < 12 if auto-enter is not supported or failed
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            val state = playbackViewModel.playbackState.value
            if (state is org.njarasoa.fijerena.core.player.model.PlaybackState.Playing ||
                state is org.njarasoa.fijerena.core.player.model.PlaybackState.Buffering
            ) {
                enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build())
            }
        }
    }
}
