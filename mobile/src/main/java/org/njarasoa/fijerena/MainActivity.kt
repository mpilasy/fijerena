package org.njarasoa.fijerena

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.navigation.MobileNavHost
import org.njarasoa.fijerena.ui.theme.FirstVideoPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appSettings = AppSettings(applicationContext)
        var themeId by mutableStateOf(appSettings.themeId)

        setContent {
            FirstVideoPlayerTheme(themeId = themeId) {
                MobileNavHost(
                    onThemeChanged = { newThemeId ->
                        appSettings.themeId = newThemeId
                        themeId = newThemeId
                    },
                )
            }
        }
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
