package com.ultraprocessed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.ui.history.HistoryScreen
import com.ultraprocessed.ui.pairing.PairingScanScreen
import com.ultraprocessed.ui.result.ResultScreen
import com.ultraprocessed.ui.scan.ScanScreen
import com.ultraprocessed.ui.settings.SettingsScreen

/**
 * Top-level Compose host. Owns the NavController and the
 * activity-scoped [MainViewModel]; all four screens read shared state
 * from it (notably the pending scan result).
 */
@Composable
fun UltraprocessedApp() {
    val mainVm: MainViewModel = viewModel()
    val navController = rememberNavController()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Semantic.colors.bg)
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.Scan
        ) {
            composable(Routes.Scan) {
                ScanScreen(
                    mainVm = mainVm,
                    onResult = { navController.navigate(Routes.Result) },
                    onOpenHistory = { navController.navigate(Routes.History) },
                    onOpenSettings = { navController.navigate(Routes.Settings) }
                )
            }
            composable(Routes.Result) {
                ResultScreen(
                    mainVm = mainVm,
                    onDone = {
                        mainVm.clearPending()
                        navController.popBackStack()
                    }
                )
            }
            composable(Routes.History) {
                HistoryScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.Settings) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onScanPairingQr = { navController.navigate(Routes.PairingScan) }
                )
            }
            composable(Routes.PairingScan) {
                PairingScanScreen(
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() }
                )
            }
        }
    }
}
