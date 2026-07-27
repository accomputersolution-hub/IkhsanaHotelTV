package `in`.pcncloud.hotel.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import `in`.pcncloud.hotel.ui.HotelViewModelFactory
import `in`.pcncloud.hotel.ui.alerts.AlertsScreen
import `in`.pcncloud.hotel.ui.dining.DiningScreen
import `in`.pcncloud.hotel.ui.home.HomeScreen
import `in`.pcncloud.hotel.ui.hotelinfo.HotelInfoScreen
import `in`.pcncloud.hotel.ui.services.ServicesScreen

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
