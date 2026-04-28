package com.ultraprocessed

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ultraprocessed.theme.UltraprocessedTheme
import com.ultraprocessed.ui.UltraprocessedApp
import com.ultraprocessed.widget.ROUTE_EXTRA

class MainActivity : ComponentActivity() {

    /**
     * Mutable so the home screen reacts to a fresh widget tap when we're
     * already running (singleTask -> onNewIntent). Reset to null after
     * consumption so back-navigation doesn't re-trigger.
     */
    private val deepLinkRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        deepLinkRoute.value = intent.getStringExtra(ROUTE_EXTRA)
        setContent {
            UltraprocessedTheme {
                UltraprocessedApp(
                    deepLinkRoute = deepLinkRoute.value,
                    onDeepLinkConsumed = { deepLinkRoute.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLinkRoute.value = intent.getStringExtra(ROUTE_EXTRA)
    }
}
