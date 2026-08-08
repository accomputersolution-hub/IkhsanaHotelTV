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
 * Live helpdesk contacts from Hotels/{pairedHotelId}/Emergency_Contacts.
 */
class CorporateServicesViewModel(
    private val repository: FirestoreRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CorporateServicesUiState())
    val uiState: StateFlow<CorporateServicesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeEmergencyContacts().collect { contacts ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        contacts = contacts, // always replace, including empty after last delete
                    )
                }
            }
        }
    }
}
