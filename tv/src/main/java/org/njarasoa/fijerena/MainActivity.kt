package org.njarasoa.fijerena

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.njarasoa.fijerena.navigation.TvNavHost
import org.njarasoa.fijerena.ui.theme.FirstVideoPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FirstVideoPlayerTheme {
                TvNavHost()
            }
        }
    }
}