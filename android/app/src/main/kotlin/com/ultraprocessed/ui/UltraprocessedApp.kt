package com.ultraprocessed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ultraprocessed.theme.Semantic
import com.ultraprocessed.ui.help.HelpScreen
import com.ultraprocessed.ui.home.HomeScreen
import com.ultraprocessed.ui.pairing.PairingScanScreen
import com.ultraprocessed.ui.places.PlacesScreen
import com.ultraprocessed.ui.result.ResultScreen
import com.ultraprocessed.ui.scan.ScanScreen
import com.ultraprocessed.ui.search.SearchScreen
import com.ultraprocessed.ui.settings.SettingsScreen

/**
 * Top-level Compose host. Owns the NavController and the
 * activity-scoped [MainViewModel]; the home screen is the default
 * destination, with Scan reachable as a push from the FAB.
 */
@Composable
fun UltraprocessedApp(
    deepLinkRoute: String? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val mainVm: MainViewModel = viewModel()
    val navController = rememberNavController()

    LaunchedEffect(deepLinkRoute) {
        when (deepLinkRoute) {
            "scan" -> navController.navigate(Routes.Scan)
            "search" -> navController.navigate(Routes.Search)
            "settings" -> navController.navigate(Routes.Settings)
            "places" -> navController.navigate(Routes.Places)
            // "home" or null - already at start destination.
            else -> Unit
        }
        if (deepLinkRoute != null) onDeepLinkConsumed()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Semantic.colors.bg)
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.Home
        ) {
            composable(Routes.Home) {
                HomeScreen(
                    onScanTap = { navController.navigate(Routes.Scan) },
                    onSearchTap = { navController.navigate(Routes.Search) },
                    onOpenSettings = { navController.navigate(Routes.Settings) },
                    onOpenPlaces = { navController.navigate(Routes.Places) }
                )
            }
            composable(Routes.Search) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onLogged = { navController.popBackStack() }
                )
            }
            composable(Routes.Scan) {
                ScanScreen(
                    mainVm = mainVm,
                    onResult = { navController.navigate(Routes.Result) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.Result) {
                ResultScreen(
                    mainVm = mainVm,
                    onDone = {
                        mainVm.clearPending()
                        // Pop result + scan, returning to Home so the user
                        // sees their updated totals.
                        navController.popBackStack(Routes.Home, inclusive = false)
                    }
                )
            }
            composable(Routes.Settings) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onScanPairingQr = { navController.navigate(Routes.PairingScan) },
                    onOpenHelp = { navController.navigate(Routes.Help) },
                    onOpenPlaces = { navController.navigate(Routes.Places) }
                )
            }
            composable(Routes.PairingScan) {
                PairingScanScreen(
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() }
                )
            }
            composable(Routes.Help) {
                HelpScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.Places) {
                PlacesScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
