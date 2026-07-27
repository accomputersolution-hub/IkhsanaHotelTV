package `in`.pcncloud.hotel.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.pcncloud.hotel.data.model.GuestProfile
import `in`.pcncloud.hotel.data.model.HotelAlert
import `in`.pcncloud.hotel.data.model.HotelBranding
import `in`.pcncloud.hotel.data.model.RoomStatus
import `in`.pcncloud.hotel.data.repository.FirestoreRepository
import `in`.pcncloud.hotel.ui.services.ServiceToastType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val guestProfile: GuestProfile = GuestProfile(),
    val branding: HotelBranding = HotelBranding(),
    val rooms: List<RoomStatus> = emptyList(),
    val alerts: List<HotelAlert> = emptyList(),
    val activePopupAlert: HotelAlert? = null,
    val isLoading: Boolean = true,
    val serviceToastMessage: String? = null,
    val serviceToastType: ServiceToastType = ServiceToastType.STATUS,
) {
    /** True when Hotels/{hotelId}.status is explicitly "inactive". */
    val isHotelInactive: Boolean
        get() = branding.status.equals("inactive", ignoreCase = true)
}

class HomeViewModel(
    private val repository: FirestoreRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val knownAlertIds = mutableSetOf<String>()
    private var alertsInitialized = false
    private val knownRequestStatuses = mutableMapOf<String, String>()
    private var requestsInitialized = false
    private var trackedSessionKey: String? = null

    init {
        viewModelScope.launch {
            combine(
                repository.observeGuestProfile(),
                repository.observeHotelBranding(),
                repository.observeRooms(),
            ) { profile, branding, rooms ->
                Triple(profile.mergeBranding(branding), branding, rooms)
            }.collect { (profile, branding, rooms) ->
                Log.d(
                    TAG,
                    "HomeViewModel sync → guest=${profile.guestName} room=${profile.roomNumber} " +
                        "hotel=${branding.hotelName} tagline=${branding.tagline} " +
                        "logo_url=${branding.logoUrl} bg_wallpaper=${branding.bgWallpaperUrl} " +
                        "status=${branding.status} roomsCount=${rooms.size}",
                )
                val sessionChanged = trackedSessionKey != null &&
                    trackedSessionKey != profile.sessionKey
                if (sessionChanged) {
                    resetForNewSession(profile, branding, rooms)
                } else {
                    trackedSessionKey = profile.sessionKey
                    _uiState.update {
                        it.copy(
                            guestProfile = profile,
                            branding = branding,
                            rooms = rooms,
                            isLoading = false,
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.observeAlerts().collect { alerts ->
                val active = alerts.filter { !it.read && !it.revoked }
                val currentPopup = _uiState.value.activePopupAlert

                val newlyArrived = if (alertsInitialized) {
                    active.filter { it.id !in knownAlertIds }
                } else {
                    active.take(1)
                }
                alerts.forEach { knownAlertIds.add(it.id) }
                alertsInitialized = true

                val nextPopup = when {
                    newlyArrived.isNotEmpty() ->
                        newlyArrived.maxByOrNull { it.timestamp }
                    currentPopup != null && active.any { it.id == currentPopup.id } ->
                        currentPopup
                    else -> null
                }

                Log.d(
                    TAG,
                    "HomeViewModel alerts → total=${alerts.size}, active=${active.size}, " +
                        "new=${newlyArrived.size}, popup=${nextPopup?.title}",
                )

                _uiState.update { state ->
                    state.copy(alerts = alerts, activePopupAlert = nextPopup)
                }
            }
        }
        viewModelScope.launch {
            repository.observeRoomServiceRequests().collect { requests ->
                requests.forEach { request ->
                    val previous = knownRequestStatuses[request.id]
                    if (requestsInitialized && previous != null && previous != request.status) {
                        when (request.status) {
                            "in_progress" -> showServiceToast(
                                "Your ${request.serviceLabel} request is In Progress",
                                ServiceToastType.STATUS,
                            )
                            "completed" -> showServiceToast(
                                "${request.serviceLabel} — Completed!",
                                ServiceToastType.SUCCESS,
                            )
                        }
                    }
                    knownRequestStatuses[request.id] = request.status
                }
                requestsInitialized = true
            }
        }
    }

    private fun showServiceToast(message: String, type: ServiceToastType) {
        viewModelScope.launch {
            _uiState.update { it.copy(serviceToastMessage = message, serviceToastType = type) }
            delay(4000)
            _uiState.update { state ->
                if (state.serviceToastMessage == message) state.copy(serviceToastMessage = null) else state
            }
        }
    }

    fun dismissPopup() {
        val alert = _uiState.value.activePopupAlert ?: return
        viewModelScope.launch {
            repository.markAlertRead(alert.id)
            val next = _uiState.value.alerts
                .filter { !it.read && !it.revoked && it.id != alert.id }
                .maxByOrNull { it.timestamp }
            _uiState.update { it.copy(activePopupAlert = next) }
        }
    }

    private fun resetForNewSession(
        profile: GuestProfile,
        branding: HotelBranding,
        rooms: List<RoomStatus>,
    ) {
        Log.d(TAG, "Session reset → new sessionKey=${profile.sessionKey}, guest=${profile.guestName}")
        knownAlertIds.clear()
        alertsInitialized = false
        knownRequestStatuses.clear()
        requestsInitialized = false
        trackedSessionKey = profile.sessionKey
        _uiState.update {
            it.copy(
                guestProfile = profile,
                branding = branding,
                rooms = rooms,
                alerts = emptyList(),
                activePopupAlert = null,
                serviceToastMessage = null,
                isLoading = false,
            )
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"

        private fun GuestProfile.mergeBranding(branding: HotelBranding): GuestProfile = copy(
            hotelName = branding.hotelName.ifBlank { hotelName },
            hotelLogoUrl = hotelLogoUrl.ifBlank { branding.logoUrl },
            bgWallpaperUrl = branding.bgWallpaperUrl.ifBlank { bgWallpaperUrl },
            themeColor = branding.themeColor.ifBlank { themeColor },
            tagline = branding.tagline.ifBlank { tagline },
            welcomeMessage = branding.welcomeMessage
                .ifBlank { hotelInfo }
                .ifBlank { welcomeMessage },
            hotelInfo = branding.welcomeMessage.ifBlank { hotelInfo },
        )
    }
}
