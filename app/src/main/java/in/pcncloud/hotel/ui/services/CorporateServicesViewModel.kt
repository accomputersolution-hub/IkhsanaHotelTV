package `in`.pcncloud.hotel.ui.services

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.pcncloud.hotel.data.model.EmergencyContact
import `in`.pcncloud.hotel.data.repository.FirestoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CorporateServicesUiState(
    val isLoading: Boolean = true,
    val contacts: List<EmergencyContact> = emptyList(),
)

/**
 * Live [emergency_contacts] from Hotels/{pairedHotelId} for corporate Helpdesk TV UI.
 */
class CorporateServicesViewModel(
    private val repository: FirestoreRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CorporateServicesUiState())
    val uiState: StateFlow<CorporateServicesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeHotelBranding().collect { branding ->
                _uiState.update {
                    CorporateServicesUiState(
                        isLoading = false,
                        contacts = branding.emergencyContacts,
                    )
                }
            }
        }
    }
}
