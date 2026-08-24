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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
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
import `in`.pcncloud.hotel.ui.intro.StartupGateViewModel
import `in`.pcncloud.hotel.ui.intro.StartupRoute
import `in`.pcncloud.hotel.ui.intro.StartupWelcomeSplash
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

    val startupGate: StartupGateViewModel = viewModel(factory = viewModelFactory)
    val startupRoute by startupGate.route.collectAsState()

    // Overlay route layered above retained Home (GONE/VISIBLE equivalent).
    val overlayRouteState = remember { mutableStateOf<String?>(null) }
    var overlayRoute by overlayRouteState
    val onHomeRoute = startupRoute is StartupRoute.Home
    val onGuestHome = onHomeRoute && overlayRoute == null
    var adminSessionEpoch by remember { mutableIntStateOf(0) }

    fun clearStaffAdminSession(reason: String) {
        AdminSession.clear()
        adminSessionEpoch += 1
        Log.d(TAG, "clearStaffAdminSession ($reason) epoch=$adminSessionEpoch")
    }

    fun showOverlay(route: String) {
        if (!onHomeRoute) {
            Log.w(TAG, "showOverlay ignored — not on Home yet ($startupRoute)")
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

    fun navigateToHomeView() {
        if (!onHomeRoute) {
            startupGate.goHome("navigateToHomeView_before_home")
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
            if (startupGate.route.value !is StartupRoute.Home) {
                startupGate.goHome("activity_home_navigator")
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

    LaunchedEffect(startupRoute) {
        Log.i(TAG, "startupRoute=$startupRoute")
    }

    // Checking: Back fails open to Home. Intro owns its own BackHandler.
    BackHandler(enabled = startupRoute is StartupRoute.Checking) {
        Log.d(TAG, "Back during Checking → Home")
        startupGate.goHome("back_during_check")
    }

    BackHandler(enabled = onHomeRoute) {
        val kioskEnabled = KioskPolicy.isKioskModeEnabled(context)
        val isHotel = !BuildConfig.IS_CORPORATE
        when {
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

    // Atomic root switch — never compose Home underneath Checking/Intro.
    when (val route = startupRoute) {
        is StartupRoute.Checking -> {
            StartupWelcomeSplash()
        }
        is StartupRoute.IntroVideo -> {
            IntroVideoScreen(
                videoUrl = route.url,
                onFinished = { reason ->
                    Log.i(TAG, "Intro finished ($reason) → Home")
                    startupGate.onIntroFinished(reason)
                },
            )
        }
        is StartupRoute.Home -> {
            GuestHomeContent(
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
private fun GuestHomeContent(
    viewModelFactory: HotelViewModelFactory,
    overlayRoute: String?,
    onGuestHome: Boolean,
    adminSessionEpoch: Int,
    showOverlay: (String) -> Unit,
    openStaffSettings: () -> Unit,
    navigateToHomeView: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDeep),
    ) {
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
