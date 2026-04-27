package com.ultraprocessed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ultraprocessed.theme.UltraprocessedTheme
import com.ultraprocessed.ui.UltraprocessedApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            UltraprocessedTheme {
                UltraprocessedApp()
            }
        }
    }
}
