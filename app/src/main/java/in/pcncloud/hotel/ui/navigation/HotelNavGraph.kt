package `in`.pcncloud.hotel.ui.navigation

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import `in`.pcncloud.hotel.kiosk.KioskPolicy
import `in`.pcncloud.hotel.ui.HotelViewModelFactory
import `in`.pcncloud.hotel.ui.admin.AdminSettingsScreen
import `in`.pcncloud.hotel.ui.alerts.AlertsScreen
import `in`.pcncloud.hotel.ui.dining.DiningScreen
import `in`.pcncloud.hotel.ui.entertainment.EntertainmentHubScreen
import `in`.pcncloud.hotel.ui.home.HomeScreen
import `in`.pcncloud.hotel.ui.hotelinfo.HotelInfoScreen
import `in`.pcncloud.hotel.ui.services.ServicesScreen

object Routes {
    const val HOME = "home"
    const val DINING = "dining"
    const val HOTEL_INFO = "hotel_info"
    const val ALERTS = "alerts"
    const val SERVICES = "services"
    const val ADMIN = "admin"
    const val ENTERTAINMENT = "entertainment"
}

private const val TAG = "HotelNavGraph"

@Composable
fun HotelNavGraph(
    viewModelFactory: HotelViewModelFactory,
    navigateHomeSignal: Long = 0L,
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val onGuestHome = currentRoute == Routes.HOME || currentRoute == null

    // Keep Accessibility HOME interceptor in sync with Compose route.
    LaunchedEffect(onGuestHome) {
        KioskPolicy.setOnGuestHomeScreen(context, onGuestHome)
    }
    LaunchedEffect(navigateHomeSignal) {
        if (navigateHomeSignal <= 0L) return@LaunchedEffect
        Log.i(TAG, "navigateHomeSignal=$navigateHomeSignal — resetting to ${Routes.HOME}")
        // Do NOT clearOttLaunchState here — pre-OTT Root Home uses this signal and must
        // keep isExternalAppActive=true so Watchdog does not reclaim over YouTube.
        // Return-from-OTT cleanup happens in MainActivity.finishReturnFromExternalApp().
        navController.navigate(Routes.HOME) {
            launchSingleTop = true
            popUpTo(navController.graph.startDestinationId) {
                inclusive = false
                saveState = false
            }
            restoreState = false
        }
        KioskPolicy.setOnGuestHomeScreen(context, true)
    }

    /**
     * Kiosk ON + Home → block Back (cannot exit / minimize via remote Back).
     * Kiosk ON + submenu → pop back to guest dashboard.
     * Kiosk OFF → standard: leave the task (stock TV UI / previous launcher).
     */
    BackHandler(enabled = true) {
        val kioskEnabled = KioskPolicy.isKioskModeEnabled(context)
        when {
            kioskEnabled && onGuestHome -> {
                Log.d(TAG, "Kiosk ON @ Home — Back consumed")
            }
            kioskEnabled && !onGuestHome -> {
                Log.d(TAG, "Kiosk ON @ $currentRoute — navigating to Home")
                KioskPolicy.clearOttLaunchState(context)
                navController.popBackStack(Routes.HOME, inclusive = false)
            }
            else -> {
                Log.d(TAG, "Kiosk OFF — allowing leave task")
                activity?.moveTaskToBack(true)
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModelFactory = viewModelFactory,
                onNavigateToDining = { navController.navigate(Routes.DINING) },
                onNavigateToAlerts = { navController.navigate(Routes.ALERTS) },
                onNavigateToServices = { navController.navigate(Routes.SERVICES) },
                onNavigateToEntertainment = { navController.navigate(Routes.ENTERTAINMENT) },
                onNavigateToAdmin = { navController.navigate(Routes.ADMIN) },
            )
        }
        composable(Routes.DINING) {
            DiningScreen(
                viewModelFactory = viewModelFactory,
                onBack = { navController.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
        composable(Routes.HOTEL_INFO) {
            HotelInfoScreen(
                viewModelFactory = viewModelFactory,
                onBack = { navController.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
        composable(Routes.ALERTS) {
            AlertsScreen(
                viewModelFactory = viewModelFactory,
                onBack = { navController.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
        composable(Routes.SERVICES) {
            ServicesScreen(
                viewModelFactory = viewModelFactory,
                onBack = { navController.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
        composable(Routes.ENTERTAINMENT) {
            EntertainmentHubScreen(
                onBack = {
                    KioskPolicy.clearOttLaunchState(context)
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }
        composable(Routes.ADMIN) {
            AdminSettingsScreen(
                onExitToHome = {
                    if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
                        navController.navigate(Routes.HOME) {
                            launchSingleTop = true
                        }
                    }
                },
            )
        }
    }
}
