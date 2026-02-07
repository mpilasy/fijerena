package org.njarasoa.fijerena

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.navigation.TvNavHost
import org.njarasoa.fijerena.ui.theme.FirstVideoPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        EpgFileManager.getInstance(applicationContext).initialize()

        val appSettings = AppSettings(applicationContext)
        var themeId by mutableStateOf(appSettings.themeId)

        setContent {
            FirstVideoPlayerTheme(themeId = themeId) {
                TvNavHost(
                    onThemeChanged = { newThemeId ->
                        appSettings.themeId = newThemeId
                        themeId = newThemeId
                    }
                )
            }
        }
    }
}
