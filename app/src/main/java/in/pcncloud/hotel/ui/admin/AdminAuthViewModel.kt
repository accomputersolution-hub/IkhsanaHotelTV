package `in`.pcncloud.hotel.ui.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import `in`.pcncloud.hotel.admin.AdminSession

/**
 * Staff Settings / Master PIN UI state.
 *
 * Must not leak across overlay opens (Dining / Agenda / Emergency room-badge).
 * Call [resetSession] on every enter and exit; [onCleared] also wipes auth.
 */
class AdminAuthViewModel : ViewModel() {

    var pinDigits by mutableStateOf("")
        private set
    var pinError by mutableStateOf(false)
        private set
    var isAuthenticated by mutableStateOf(false)
        private set
    var showKeyBlocker by mutableStateOf(false)
        private set

    fun appendPinDigit(digit: Char, maxLength: Int) {
        pinError = false
        if (pinDigits.length >= maxLength) return
        pinDigits += digit
    }

    fun backspacePin() {
        pinError = false
        if (pinDigits.isNotEmpty()) {
            pinDigits = pinDigits.dropLast(1)
        }
    }

    fun markPinIncorrect() {
        pinError = true
        pinDigits = ""
        isAuthenticated = false
        showKeyBlocker = false
        AdminSession.clear()
    }

    fun markPinAccepted(verifiedPin: String) {
        AdminSession.unlock(verifiedPin)
        pinDigits = ""
        pinError = false
        isAuthenticated = true
        showKeyBlocker = false
    }

    fun openKeyBlocker() {
        if (!isAuthenticated) return
        showKeyBlocker = true
    }

    fun closeKeyBlocker() {
        showKeyBlocker = false
    }

    /** Full wipe — PIN, errors, auth flag, nested Key Blocker, in-memory session. */
    fun resetSession() {
        pinDigits = ""
        pinError = false
        isAuthenticated = false
        showKeyBlocker = false
        AdminSession.clear()
    }

    override fun onCleared() {
        resetSession()
        super.onCleared()
    }
}
