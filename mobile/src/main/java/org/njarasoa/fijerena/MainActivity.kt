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

import org.njarasoa.fijerena.core.ui.utils.LocaleManager

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
