package `in`.pcncloud.hotel.ui.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.pcncloud.hotel.data.model.AgendaItem
import `in`.pcncloud.hotel.data.repository.FirestoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AgendaUiState(
    val isLoading: Boolean = true,
    val items: List<AgendaItem> = emptyList(),
)

/**
 * Live daily agenda from Hotels/{pairedHotelId}/Daily_Agenda.
 */
class AgendaViewModel(
    private val repository: FirestoreRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgendaUiState())
    val uiState: StateFlow<AgendaUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeDailyAgenda().collect { items ->
                _uiState.update {
                    AgendaUiState(
                        isLoading = false,
                        items = items,
                    )
                }
            }
        }
    }
}
