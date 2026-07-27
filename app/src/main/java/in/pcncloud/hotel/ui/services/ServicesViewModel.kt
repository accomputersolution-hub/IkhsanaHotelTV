package `in`.pcncloud.hotel.ui.services

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.pcncloud.hotel.data.model.GuestProfile
import `in`.pcncloud.hotel.data.model.ServiceRequest
import `in`.pcncloud.hotel.data.repository.FirestoreRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SubItemKind {
    /** − Qty + stepper */
    QUANTITY,
    /** Single-select / multi-select toggle row */
    TOGGLE,
    /** Toggle with mutually exclusive choices (e.g. Instant / Scheduled) */
    CHOICE,
}

data class SubServiceItem(
    val id: String,
    val label: String,
    val kind: SubItemKind,
    /** For CHOICE items — e.g. Instant / Scheduled Time */
    val choices: List<String> = emptyList(),
)

data class ServiceOption(
    val department: String,
    val serviceType: String,
    val requestType: String,
    val label: String,
    val icon: String,
    val subtitle: String,
    val subItems: List<SubServiceItem>,
)

/** Live selection state for one sub-item inside the dialog. */
data class SubItemSelection(
    val quantity: Int = 0,
    val selected: Boolean = false,
    val choice: String? = null,
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
    val roomOccupied: Boolean = false,
    val showVacantRoomDialog: Boolean = false,
    /** Category currently open in the sub-options dialog (null = closed). */
    val activeCategory: ServiceOption? = null,
    /** Map of sub-item id → selection state for the open dialog. */
    val subSelections: Map<String, SubItemSelection> = emptyMap(),
) {
    val selectedItemLabels: List<String>
        get() {
            val category = activeCategory ?: return emptyList()
            return category.subItems.mapNotNull { item ->
                val sel = subSelections[item.id] ?: return@mapNotNull null
                when (item.kind) {
                    SubItemKind.QUANTITY ->
                        if (sel.quantity > 0) "${sel.quantity}x ${item.label}" else null
                    SubItemKind.TOGGLE ->
                        if (sel.selected) item.label else null
                    SubItemKind.CHOICE ->
                        if (sel.selected) {
                            val choice = sel.choice?.takeIf { it.isNotBlank() }
                            if (choice != null) "${item.label} ($choice)" else item.label
                        } else {
                            null
                        }
                }
            }
        }

    val canSubmitSubRequest: Boolean
        get() = selectedItemLabels.isNotEmpty() && !isSubmitting
}

class ServicesViewModel(
    private val repository: FirestoreRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServicesUiState())
    val uiState: StateFlow<ServicesUiState> = _uiState.asStateFlow()

    val serviceOptions = listOf(
        ServiceOption(
            department = "housekeeping",
            serviceType = "housekeeping",
            requestType = "CLEANING",
            label = "Room Cleaning",
            icon = "🧹",
            subtitle = "Request room cleanup or turndown service",
            subItems = listOf(
                SubServiceItem(
                    id = "full_room_service",
                    label = "Full Room Service",
                    kind = SubItemKind.CHOICE,
                    choices = listOf("Instant", "Scheduled Time"),
                ),
                SubServiceItem(
                    id = "express_trash",
                    label = "Express Trash Pickup",
                    kind = SubItemKind.TOGGLE,
                ),
            ),
        ),
        ServiceOption(
            department = "housekeeping",
            serviceType = "extra_towels",
            requestType = "TOWELS",
            label = "Extra Towels & Linen",
            icon = "🧻",
            subtitle = "Fresh towels, linen, and bedding refill",
            subItems = listOf(
                SubServiceItem(id = "bath_towels", label = "Bath Towels", kind = SubItemKind.QUANTITY),
                SubServiceItem(id = "extra_pillows", label = "Extra Pillows", kind = SubItemKind.QUANTITY),
                SubServiceItem(id = "blanket", label = "Blanket", kind = SubItemKind.QUANTITY),
            ),
        ),
        ServiceOption(
            department = "housekeeping",
            serviceType = "amenities",
            requestType = "AMENITIES",
            label = "Bottled Water / Amenities",
            icon = "🍾",
            subtitle = "Bottled water, toiletries, and room amenities",
            subItems = listOf(
                SubServiceItem(id = "mineral_water", label = "Mineral Water Bottles", kind = SubItemKind.QUANTITY),
                SubServiceItem(id = "dental_kit", label = "Dental Kit", kind = SubItemKind.QUANTITY),
                SubServiceItem(id = "soap_shampoo", label = "Soap & Shampoo Kit", kind = SubItemKind.QUANTITY),
            ),
        ),
        ServiceOption(
            department = "concierge",
            serviceType = "concierge_call",
            requestType = "CONCIERGE",
            label = "Concierge / Front Desk Call",
            icon = "🚖",
            subtitle = "Call front desk or request concierge assistance",
            subItems = listOf(
                SubServiceItem(
                    id = "airport_taxi",
                    label = "Airport Taxi Booking Request",
                    kind = SubItemKind.TOGGLE,
                ),
                SubServiceItem(
                    id = "luggage_assist",
                    label = "Luggage Assistance Request",
                    kind = SubItemKind.TOGGLE,
                ),
            ),
        ),
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

    /** Open sub-options dialog for a category card (after occupancy check). */
    fun openCategory(option: ServiceOption) {
        if (_uiState.value.isSubmitting) return
        if (!_uiState.value.roomOccupied) {
            _uiState.update { it.copy(showVacantRoomDialog = true) }
            return
        }
        val initial = option.subItems.associate { item ->
            item.id to when (item.kind) {
                SubItemKind.QUANTITY -> SubItemSelection(quantity = 0)
                SubItemKind.TOGGLE -> SubItemSelection(selected = false)
                SubItemKind.CHOICE -> SubItemSelection(
                    selected = false,
                    choice = item.choices.firstOrNull(),
                )
            }
        }
        _uiState.update {
            it.copy(activeCategory = option, subSelections = initial)
        }
    }

    fun dismissSubDialog() {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(activeCategory = null, subSelections = emptyMap()) }
    }

    fun incrementSubItem(itemId: String) {
        _uiState.update { state ->
            val current = state.subSelections[itemId] ?: return@update state
            state.copy(
                subSelections = state.subSelections + (
                    itemId to current.copy(quantity = (current.quantity + 1).coerceAtMost(20))
                    ),
            )
        }
    }

    fun decrementSubItem(itemId: String) {
        _uiState.update { state ->
            val current = state.subSelections[itemId] ?: return@update state
            state.copy(
                subSelections = state.subSelections + (
                    itemId to current.copy(quantity = (current.quantity - 1).coerceAtLeast(0))
                    ),
            )
        }
    }

    fun toggleSubItem(itemId: String) {
        _uiState.update { state ->
            val current = state.subSelections[itemId] ?: return@update state
            state.copy(
                subSelections = state.subSelections + (
                    itemId to current.copy(selected = !current.selected)
                    ),
            )
        }
    }

    fun selectChoice(itemId: String, choice: String) {
        _uiState.update { state ->
            val current = state.subSelections[itemId] ?: return@update state
            state.copy(
                subSelections = state.subSelections + (
                    itemId to current.copy(selected = true, choice = choice)
                    ),
            )
        }
    }

    /** Submit the dialog selections to Firestore. */
    fun submitSubRequest() {
        val state = _uiState.value
        val category = state.activeCategory ?: return
        if (!state.canSubmitSubRequest) return
        if (!state.roomOccupied) {
            _uiState.update { it.copy(showVacantRoomDialog = true) }
            return
        }

        val items = state.selectedItemLabels
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            repository.submitServiceRequest(
                department = category.department,
                serviceType = category.serviceType,
                serviceLabel = category.label,
                guestName = state.guestName,
                requestType = category.requestType,
                items = items,
            ).onSuccess { requestId ->
                knownRequestStatuses[requestId] = "pending"
                showToast("Request sent to Front Desk!", ServiceToastType.SUCCESS)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        activeCategory = null,
                        subSelections = emptyMap(),
                    )
                }
            }.onFailure { err ->
                val vacant = err.message?.contains("Checked-In", ignoreCase = true) == true ||
                    err.message?.contains("not occupied", ignoreCase = true) == true
                if (vacant) {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            showVacantRoomDialog = true,
                            roomOccupied = false,
                            activeCategory = null,
                            subSelections = emptyMap(),
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
                activeCategory = null,
                subSelections = emptyMap(),
            )
        }
    }
}
