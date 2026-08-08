package `in`.pcncloud.hotel.ui.navigation

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import `in`.pcncloud.hotel.MainActivity
import `in`.pcncloud.hotel.kiosk.KioskPolicy
import `in`.pcncloud.hotel.ui.HotelViewModelFactory
import `in`.pcncloud.hotel.ui.admin.AdminSettingsScreen
import `in`.pcncloud.hotel.ui.agenda.AgendaScreen
import `in`.pcncloud.hotel.ui.alerts.AlertsScreen
import `in`.pcncloud.hotel.ui.dining.DiningScreen
import `in`.pcncloud.hotel.ui.entertainment.EntertainmentHubScreen
import `in`.pcncloud.hotel.ui.home.HomeScreen
import `in`.pcncloud.hotel.ui.hotelinfo.HotelInfoScreen
import `in`.pcncloud.hotel.ui.services.ServicesScreen
import `in`.pcncloud.hotel.ui.theme.NavyDeep

object Routes {
    const val HOME = "home"
    const val DINING = "dining"
    const val HOTEL_INFO = "hotel_info"
    const val ALERTS = "alerts"
    const val SERVICES = "services"
    /** Hotel-only: housekeeping department filter on ServicesScreen. */
    const val SERVICES_HOUSEKEEPING = "services_housekeeping"
    /** Hotel-only: concierge / front desk filter on ServicesScreen. */
    const val SERVICES_CONCIERGE = "services_concierge"
    const val AGENDA = "agenda"
    const val ADMIN = "admin"
    const val ENTERTAINMENT = "entertainment"
}

private const val TAG = "HotelNavGraph"

@Composable
fun HotelNavGraph(
    viewModelFactory: HotelViewModelFactory,
    navigateHomeSignal: Long = 0L,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val mainActivity = context as? MainActivity

    // Overlay route layered above retained Home (GONE/VISIBLE equivalent).
    // null = Home visible; any other route = that sub-screen covers Home.
    val overlayRouteState = remember { mutableStateOf<String?>(null) }
    var overlayRoute by overlayRouteState
    val onGuestHome = overlayRoute == null

    fun showOverlay(route: String) {
        overlayRoute = route
        mainActivity?.setSubMenuVisible(true)
        KioskPolicy.setOnGuestHomeScreen(context, false)
        Log.d(TAG, "showOverlay → $route")
    }

    /** Same action as the Top-Left Home / back control — instant reveal of retained Home. */
    fun navigateToHomeView() {
        if (overlayRouteState.value == null) {
            mainActivity?.setSubMenuVisible(false)
            KioskPolicy.setOnGuestHomeScreen(context, true)
            return
        }
        Log.i(TAG, "navigateToHomeView — hide overlay=${overlayRouteState.value} (instant)")
        overlayRouteState.value = null
        mainActivity?.setSubMenuVisible(false)
        KioskPolicy.setOnGuestHomeScreen(context, true)
    }

    // MainActivity remote BACK / safety-net must call the same path as the Home icon.
    // Mutate remembered MutableState so the callback never goes stale across recompositions.
    DisposableEffect(mainActivity) {
        mainActivity?.registerHomeViewNavigator {
            if (overlayRouteState.value != null) {
                Log.i(TAG, "navigateToHomeView (Activity) — hide overlay=${overlayRouteState.value}")
                overlayRouteState.value = null
            }
            mainActivity.setSubMenuVisible(false)
            KioskPolicy.setOnGuestHomeScreen(context, true)
        }
        onDispose {
            mainActivity?.registerHomeViewNavigator(null)
            mainActivity?.setSubMenuVisible(false)
        }
    }

    LaunchedEffect(onGuestHome) {
        KioskPolicy.setOnGuestHomeScreen(context, onGuestHome)
    }

    LaunchedEffect(navigateHomeSignal) {
        if (navigateHomeSignal <= 0L) return@LaunchedEffect
        Log.i(TAG, "navigateHomeSignal=$navigateHomeSignal — navigateToHomeView")
        // Do NOT clearOttLaunchState here — pre-OTT Root Home must keep isExternalAppActive.
        navigateToHomeView()
    }

    /**
     * Kiosk ON + Home → block Back.
     * Kiosk ON + submenu → same as Home icon (hide overlay).
     * Kiosk OFF → leave task.
     */
    BackHandler(enabled = true) {
        val kioskEnabled = KioskPolicy.isKioskModeEnabled(context)
        when {
            kioskEnabled && onGuestHome -> {
                Log.d(TAG, "Kiosk ON @ Home — Back consumed")
            }
            kioskEnabled && !onGuestHome -> {
                Log.d(TAG, "Kiosk ON @ $overlayRoute — navigateToHomeView")
                KioskPolicy.clearOttLaunchState(context)
                navigateToHomeView()
            }
            !onGuestHome -> {
                navigateToHomeView()
            }
            else -> {
                Log.d(TAG, "Kiosk OFF — allowing leave task")
                activity?.moveTaskToBack(true)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDeep),
    ) {
        // Retained Home — always composed; never destroyed when opening sub-screens.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusProperties { canFocus = onGuestHome },
        ) {
            HomeScreen(
                viewModelFactory = viewModelFactory,
                onNavigateToDining = { showOverlay(Routes.DINING) },
                onNavigateToAlerts = { showOverlay(Routes.ALERTS) },
                onNavigateToServices = { showOverlay(Routes.SERVICES) },
                onNavigateToAgenda = { showOverlay(Routes.AGENDA) },
                onNavigateToEntertainment = { showOverlay(Routes.ENTERTAINMENT) },
                onNavigateToAdmin = { showOverlay(Routes.ADMIN) },
            )
        }

        // Sub-screens layered above Home — clearing overlayRoute reveals Home with 0ms inflate.
        when (overlayRoute) {
            Routes.DINING -> {
                DiningScreen(
                    viewModelFactory = viewModelFactory,
                    onBack = { navigateToHomeView() },
                    onOpenAdmin = { showOverlay(Routes.ADMIN) },
                )
            }
            Routes.HOTEL_INFO -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NavyDeep),
                ) {
                    HotelInfoScreen(
                        viewModelFactory = viewModelFactory,
                        onBack = { navigateToHomeView() },
                        onOpenAdmin = { showOverlay(Routes.ADMIN) },
                    )
                }
            }
            Routes.ALERTS -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NavyDeep),
                ) {
                    AlertsScreen(
                        viewModelFactory = viewModelFactory,
                        onBack = { navigateToHomeView() },
                        onOpenAdmin = { showOverlay(Routes.ADMIN) },
                    )
                }
            }
            Routes.SERVICES,
            Routes.SERVICES_HOUSEKEEPING,
            Routes.SERVICES_CONCIERGE,
            -> {
                val departmentFilter = when (overlayRoute) {
                    Routes.SERVICES_HOUSEKEEPING -> "housekeeping"
                    Routes.SERVICES_CONCIERGE -> "concierge"
                    else -> null
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NavyDeep),
                ) {
                    ServicesScreen(
                        viewModelFactory = viewModelFactory,
                        onBack = { navigateToHomeView() },
                        onOpenAdmin = { showOverlay(Routes.ADMIN) },
                        departmentFilter = departmentFilter,
                    )
                }
            }
            Routes.AGENDA -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NavyDeep),
                ) {
                    AgendaScreen(
                        viewModelFactory = viewModelFactory,
                        onBack = { navigateToHomeView() },
                        onOpenAdmin = { showOverlay(Routes.ADMIN) },
                    )
                }
            }
            Routes.ENTERTAINMENT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NavyDeep),
                ) {
                    EntertainmentHubScreen(
                        viewModelFactory = viewModelFactory,
                        onBack = {
                            KioskPolicy.clearOttLaunchState(context)
                            navigateToHomeView()
                        },
                        onOpenAdmin = { showOverlay(Routes.ADMIN) },
                    )
                }
            }
            Routes.ADMIN -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NavyDeep),
                ) {
                    AdminSettingsScreen(
                        onExitToHome = { navigateToHomeView() },
                    )
                }
            }
        }
    }
}
