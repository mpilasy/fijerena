package org.njarasoa.fijerena

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.njarasoa.fijerena.navigation.MobileNavHost
import org.njarasoa.fijerena.ui.theme.FirstVideoPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FirstVideoPlayerTheme {
                MobileNavHost()
            }
        }
    }
}