package org.njarasoa.fijerena

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.navigation.TvNavHost
import org.njarasoa.fijerena.ui.theme.FirstVideoPlayerTheme
import org.njarasoa.fijerena.ui.theme.LocalUiScale

import org.njarasoa.fijerena.core.ui.utils.LocaleManager

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        android.util.Log.i("MainActivity", "dispatchKeyEvent: action=${event.action}, code=${event.keyCode}")
        return super.dispatchKeyEvent(event)
    }
}
