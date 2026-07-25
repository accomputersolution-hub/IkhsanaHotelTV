package com.example.ikhsanahoteltv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ikhsanahoteltv.ui.HotelViewModelFactory
import com.example.ikhsanahoteltv.ui.alerts.AlertsScreen
import com.example.ikhsanahoteltv.ui.dining.DiningScreen
import com.example.ikhsanahoteltv.ui.home.HomeScreen
import com.example.ikhsanahoteltv.ui.hotelinfo.HotelInfoScreen
import com.example.ikhsanahoteltv.ui.services.ServicesScreen

object Routes {
    const val HOME = "home"
    const val DINING = "dining"
    const val HOTEL_INFO = "hotel_info"
    const val ALERTS = "alerts"
    const val SERVICES = "services"
}

@Composable
fun HotelNavGraph(
    viewModelFactory: HotelViewModelFactory,
    navController: NavHostController = rememberNavController(),
) {
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
            )
        }
        composable(Routes.DINING) {
            DiningScreen(
                viewModelFactory = viewModelFactory,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.HOTEL_INFO) {
            HotelInfoScreen(
                viewModelFactory = viewModelFactory,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.ALERTS) {
            AlertsScreen(
                viewModelFactory = viewModelFactory,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SERVICES) {
            ServicesScreen(
                viewModelFactory = viewModelFactory,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
