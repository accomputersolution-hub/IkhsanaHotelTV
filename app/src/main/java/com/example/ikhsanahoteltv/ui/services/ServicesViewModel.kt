package com.example.ikhsanahoteltv.ui.services

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ikhsanahoteltv.data.model.GuestProfile
import com.example.ikhsanahoteltv.data.model.ServiceRequest
import com.example.ikhsanahoteltv.data.repository.FirestoreRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServiceOption(
    val department: String,
    val serviceType: String,
    val label: String,
    val icon: String,
)

enum class ServiceToastType {
    SUCCESS,
    ERROR,
    STATUS,
}

data class ServicesUiState(
    val guestName: String = "Guest",
    val isSubmitting: Boolean = false,
    val toastMessage: String? = null,
    val toastType: ServiceToastType = ServiceToastType.SUCCESS,
    val activeRequests: List<ServiceRequest> = emptyList(),
    /** True when the room's Firestore doc shows the room is OCCUPIED. Fail-closed until known. */
    val roomOccupied: Boolean = false,
    /** Show the "room not checked-in" blocking dialog. */
    val showVacantRoomDialog: Boolean = false,
)

class ServicesViewModel(
    private val repository: FirestoreRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServicesUiState())
    val uiState: StateFlow<ServicesUiState> = _uiState.asStateFlow()

    val serviceOptions = listOf(
        ServiceOption("housekeeping", "housekeeping", "Room Cleaning", "🧹"),
        ServiceOption("housekeeping", "extra_towels", "Extra Towels & Linen", "🧻"),
        ServiceOption("housekeeping", "amenities", "Bottled Water / Amenities", "🍾"),
        ServiceOption("concierge", "concierge_call", "Concierge / Front Desk Call", "🚖"),
    )

    private val knownRequestStatuses = mutableMapOf<String, String>()
    private var requestsInitialized = false
    private var trackedSessionKey: String? = null

    init {
        viewModelScope.launch {
            repository.observeGuestProfile().collect { profile ->
                val sessionChanged = trackedSessionKey != null &&
                    trackedSessionKey != profile.sessionKey
                if (sessionChanged) {
                    resetForNewSession(profile)
                } else {
                    trackedSessionKey = profile.sessionKey
                    _uiState.update { it.copy(guestName = profile.guestName) }
                }
            }
        }
        viewModelScope.launch {
            repository.observeRoomServiceRequests().collect { requests ->
                handleRequestUpdates(requests)
            }
        }
        viewModelScope.launch {
            repository.observeThisRoomStatus().collect { roomStatus ->
                _uiState.update { it.copy(roomOccupied = roomStatus.occupied) }
            }
        }
    }

    private suspend fun handleRequestUpdates(requests: List<ServiceRequest>) {
        requests.forEach { request ->
            val previous = knownRequestStatuses[request.id]
            if (requestsInitialized && previous != null && previous != request.status) {
                when (request.status) {
                    "in_progress" -> showToast(
                        "Your ${request.serviceLabel} request is In Progress",
                        ServiceToastType.STATUS,
                    )
                    "completed" -> showToast(
                        "${request.serviceLabel} — Completed!",
                        ServiceToastType.SUCCESS,
                    )
                    "cancelled" -> showToast(
                        "${request.serviceLabel} request was cancelled",
                        ServiceToastType.ERROR,
                    )
                }
            }
            knownRequestStatuses[request.id] = request.status
        }
        requestsInitialized = true
        _uiState.update { it.copy(activeRequests = requests.filter { it.status != "cancelled" }) }
    }

    fun dismissVacantRoomDialog() {
        _uiState.update { it.copy(showVacantRoomDialog = false) }
    }

    fun requestService(option: ServiceOption) {
        if (_uiState.value.isSubmitting) return

        if (!_uiState.value.roomOccupied) {
            _uiState.update { it.copy(showVacantRoomDialog = true) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            repository.submitServiceRequest(
                department = option.department,
                serviceType = option.serviceType,
                serviceLabel = option.label,
                guestName = _uiState.value.guestName,
            ).onSuccess { requestId ->
                knownRequestStatuses[requestId] = "pending"
                showToast("Request sent to Front Desk!", ServiceToastType.SUCCESS)
                _uiState.update { it.copy(isSubmitting = false) }
            }.onFailure { err ->
                val vacant = err.message?.contains("Checked-In", ignoreCase = true) == true ||
                    err.message?.contains("not occupied", ignoreCase = true) == true
                if (vacant) {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            showVacantRoomDialog = true,
                            roomOccupied = false,
                        )
                    }
                } else {
                    showToast("Could not send request. Please try again.", ServiceToastType.ERROR)
                    _uiState.update { it.copy(isSubmitting = false) }
                }
            }
        }
    }

    private suspend fun showToast(message: String, type: ServiceToastType) {
        _uiState.update { it.copy(toastMessage = message, toastType = type) }
        delay(4000)
        _uiState.update { state ->
            if (state.toastMessage == message) state.copy(toastMessage = null) else state
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun resetForNewSession(profile: GuestProfile) {
        knownRequestStatuses.clear()
        requestsInitialized = false
        trackedSessionKey = profile.sessionKey
        _uiState.update {
            it.copy(
                guestName = profile.guestName,
                activeRequests = emptyList(),
                toastMessage = null,
                isSubmitting = false,
                showVacantRoomDialog = false,
            )
        }
    }
}
