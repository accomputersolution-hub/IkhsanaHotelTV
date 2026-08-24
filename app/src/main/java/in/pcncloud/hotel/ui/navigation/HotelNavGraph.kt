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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import `in`.pcncloud.hotel.BuildConfig
import `in`.pcncloud.hotel.MainActivity
import `in`.pcncloud.hotel.admin.AdminSession
import `in`.pcncloud.hotel.kiosk.KioskPolicy
import `in`.pcncloud.hotel.ui.HotelViewModelFactory
import `in`.pcncloud.hotel.ui.admin.AdminSettingsScreen
import `in`.pcncloud.hotel.ui.agenda.AgendaScreen
import `in`.pcncloud.hotel.ui.alerts.AlertsScreen
import `in`.pcncloud.hotel.ui.dining.DiningScreen
import `in`.pcncloud.hotel.ui.entertainment.EntertainmentHubScreen
import `in`.pcncloud.hotel.ui.home.HomeScreen
import `in`.pcncloud.hotel.ui.hotelinfo.HotelInfoScreen
import `in`.pcncloud.hotel.ui.intro.IntroVideoScreen
import `in`.pcncloud.hotel.ui.services.ServicesScreen
import `in`.pcncloud.hotel.ui.theme.NavyDeep

object Routes {
    /** Cold-start gate — always the NavHost [startDestination]. */
    const val INTRO = "intro"
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
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val onIntroRoute = currentRoute == Routes.INTRO || currentRoute == null

    // Overlay route layered above retained Home (only while on HOME destination).
    val overlayRouteState = remember { mutableStateOf<String?>(null) }
    var overlayRoute by overlayRouteState
    val onGuestHome = !onIntroRoute && overlayRoute == null
    /** Bumped on every Staff Settings open/close so PIN/auth ViewModel cannot leak. */
    var adminSessionEpoch by remember { mutableIntStateOf(0) }

    fun clearStaffAdminSession(reason: String) {
        AdminSession.clear()
        adminSessionEpoch += 1
        Log.d(TAG, "clearStaffAdminSession ($reason) epoch=$adminSessionEpoch")
    }

    fun goToHomeReplacingIntro(reason: String) {
        Log.i(TAG, "navigate HOME replacing INTRO ($reason)")
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.INTRO) { inclusive = true }
            launchSingleTop = true
        }
        overlayRouteState.value = null
        mainActivity?.setSubMenuVisible(false)
        KioskPolicy.setOnGuestHomeScreen(context, true)
    }

    fun showOverlay(route: String) {
        if (onIntroRoute) {
            // Never open guest overlays during intro — finish intro first.
            goToHomeReplacingIntro("overlay_during_intro")
            return
        }
        if (overlayRoute == Routes.ADMIN && route != Routes.ADMIN) {
            clearStaffAdminSession("leave_admin_overlay")
        }
        overlayRoute = route
        mainActivity?.setSubMenuVisible(true)
        KioskPolicy.setOnGuestHomeScreen(context, false)
        Log.d(TAG, "showOverlay → $route")
    }

    fun openStaffSettings() {
        clearStaffAdminSession("open_staff_settings")
        showOverlay(Routes.ADMIN)
    }

    /** Same action as the Top-Left Home / back control — instant reveal of retained Home. */
    fun navigateToHomeView() {
        if (onIntroRoute) {
            goToHomeReplacingIntro("navigateToHomeView")
            return
        }
        if (overlayRouteState.value == Routes.ADMIN) {
            clearStaffAdminSession("navigate_home_from_admin")
        }
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

    DisposableEffect(mainActivity) {
        mainActivity?.registerHomeViewNavigator {
            if (navController.currentDestination?.route == Routes.INTRO) {
                goToHomeReplacingIntro("activity_home_navigator")
                return@registerHomeViewNavigator
            }
            if (overlayRouteState.value == Routes.ADMIN) {
                AdminSession.clear()
                adminSessionEpoch += 1
            }
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
        navigateToHomeView()
    }

    BackHandler(enabled = true) {
        val kioskEnabled = KioskPolicy.isKioskModeEnabled(context)
        val isHotel = !BuildConfig.IS_CORPORATE
        when {
            onIntroRoute -> {
                Log.d(TAG, "Intro route — Back skips to Home")
                goToHomeReplacingIntro("back")
            }
            isHotel && onGuestHome -> {
                Log.d(TAG, "Hotel flavor @ Home — Back consumed (no exit)")
            }
            isHotel && !onGuestHome -> {
                Log.d(TAG, "Hotel flavor @ $overlayRoute — navigateToHomeView")
                KioskPolicy.clearOttLaunchState(context)
                navigateToHomeView()
            }
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

    NavHost(
        navController = navController,
        startDestination = Routes.INTRO,
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDeep),
    ) {
        composable(Routes.INTRO) {
            // startDestination — Home is NOT in the back stack and is not composed.
            IntroVideoScreen(
                viewModelFactory = viewModelFactory,
                onFinished = {
                    goToHomeReplacingIntro("intro_finished")
                },
            )
        }
        composable(Routes.HOME) {
            GuestHomeRoute(
                viewModelFactory = viewModelFactory,
                overlayRoute = overlayRoute,
                onGuestHome = onGuestHome,
                adminSessionEpoch = adminSessionEpoch,
                showOverlay = ::showOverlay,
                openStaffSettings = ::openStaffSettings,
                navigateToHomeView = ::navigateToHomeView,
            )
        }
    }
}

@Composable
private fun GuestHomeRoute(
    viewModelFactory: HotelViewModelFactory,
    overlayRoute: String?,
    onGuestHome: Boolean,
    adminSessionEpoch: Int,
    showOverlay: (String) -> Unit,
    openStaffSettings: () -> Unit,
    navigateToHomeView: () -> Unit,
) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize().background(NavyDeep)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusProperties { canFocus = onGuestHome },
        ) {
            HomeScreen(
                viewModelFactory = viewModelFactory,
                isHomeVisible = onGuestHome,
                onNavigateToDining = { showOverlay(Routes.DINING) },
                onNavigateToAlerts = { showOverlay(Routes.ALERTS) },
                onNavigateToServices = { showOverlay(Routes.SERVICES) },
                onNavigateToAgenda = { showOverlay(Routes.AGENDA) },
                onNavigateToEntertainment = { showOverlay(Routes.ENTERTAINMENT) },
                onNavigateToAdmin = { openStaffSettings() },
            )
        }

        when (overlayRoute) {
            Routes.DINING -> {
                DiningScreen(
                    viewModelFactory = viewModelFactory,
                    onBack = { navigateToHomeView() },
                    onOpenAdmin = { openStaffSettings() },
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
                        onOpenAdmin = { openStaffSettings() },
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
                        onOpenAdmin = { openStaffSettings() },
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
                        onOpenAdmin = { openStaffSettings() },
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
                        onOpenAdmin = { openStaffSettings() },
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
                        onOpenAdmin = { openStaffSettings() },
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
                        sessionEpoch = adminSessionEpoch,
                    )
                }
            }
        }
    }
}
