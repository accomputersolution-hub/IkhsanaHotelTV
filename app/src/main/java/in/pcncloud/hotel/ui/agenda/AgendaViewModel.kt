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
import java.util.Calendar
import java.util.Locale

data class AgendaUiState(
    val isLoading: Boolean = true,
    /** Full live snapshot from Firestore (all days). */
    val allItems: List<AgendaItem> = emptyList(),
    /** Distinct date keys (ISO `yyyy-MM-dd` or raw label), sorted ascending. */
    val availableDates: List<String> = emptyList(),
    /** Currently selected date key. */
    val selectedDate: String = "",
    /** Sessions for [selectedDate]. */
    val items: List<AgendaItem> = emptyList(),
    /** Contacts / footnotes for the selected day. */
    val contactsFooter: String = "",
)

/**
 * Live multi-day agenda from Hotels/{pairedHotelId}/Daily_Agenda with date tabs.
 */
class AgendaViewModel(
    private val repository: FirestoreRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgendaUiState())
    val uiState: StateFlow<AgendaUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeDailyAgenda().collect { all ->
                applySnapshot(all)
            }
        }
    }

    fun selectDate(date: String) {
        val state = _uiState.value
        if (date.isBlank() || date == state.selectedDate) return
        if (date !in state.availableDates && state.availableDates.isNotEmpty()) return
        _uiState.update { current ->
            val filtered = filterByDate(current.allItems, date)
            current.copy(
                selectedDate = date,
                items = filtered,
                contactsFooter = contactsFooterFor(filtered),
            )
        }
    }

    private fun applySnapshot(all: List<AgendaItem>) {
        val dates = extractAvailableDates(all)
        val previous = _uiState.value.selectedDate
        val selected = when {
            previous.isNotBlank() && previous in dates -> previous
            else -> defaultSelectedDate(dates)
        }
        val filtered = filterByDate(all, selected)
        _uiState.update {
            it.copy(
                isLoading = false,
                allItems = all,
                availableDates = dates,
                selectedDate = selected,
                items = filtered,
                contactsFooter = contactsFooterFor(filtered),
            )
        }
    }

    companion object {
        /** Items with blank [AgendaItem.date] are grouped under this key. */
        const val UNDATED_KEY = ""

        fun todayIsoDate(): String {
            val cal = Calendar.getInstance()
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH) + 1
            val d = cal.get(Calendar.DAY_OF_MONTH)
            return "%04d-%02d-%02d".format(y, m, d)
        }

        fun extractAvailableDates(items: List<AgendaItem>): List<String> {
            if (items.isEmpty()) return emptyList()
            val keys = items.map { it.date.trim() }.distinct()
            val dated = keys.filter { it.isNotBlank() }.sorted()
            val hasUndated = keys.any { it.isBlank() }
            return if (hasUndated && dated.isEmpty()) {
                listOf(UNDATED_KEY)
            } else if (hasUndated) {
                dated + UNDATED_KEY
            } else {
                dated
            }
        }

        fun defaultSelectedDate(available: List<String>): String {
            if (available.isEmpty()) return UNDATED_KEY
            val today = todayIsoDate()
            if (today in available) return today
            return available.first()
        }

        fun filterByDate(items: List<AgendaItem>, dateKey: String): List<AgendaItem> {
            if (items.isEmpty()) return emptyList()
            return if (dateKey.isBlank()) {
                items.filter { it.date.isBlank() }.ifEmpty { items }
            } else {
                items.filter { it.date.trim() == dateKey || it.date.trim().contains(dateKey) }
            }
        }

        fun contactsFooterFor(items: List<AgendaItem>): String =
            items.map { it.notes }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" · ")

        /** Chip label: `2026-06-27` → `June 27, 2026`; blank → `Schedule`. */
        fun formatDateChipLabel(dateKey: String): String {
            if (dateKey.isBlank()) return "Schedule"
            val parts = dateKey.split("-")
            if (parts.size == 3) {
                val y = parts[0].toIntOrNull()
                val m = parts[1].toIntOrNull()
                val d = parts[2].toIntOrNull()
                if (y != null && m != null && d != null && m in 1..12) {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, y)
                        set(Calendar.MONTH, m - 1)
                        set(Calendar.DAY_OF_MONTH, d)
                    }
                    val month = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.US) ?: return dateKey
                    return "$month $d, $y"
                }
            }
            return dateKey
        }
    }
}
