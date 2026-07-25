package com.example.ikhsanahoteltv.ui.dining

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ikhsanahoteltv.config.HotelConfig
import com.example.ikhsanahoteltv.data.model.CartItem
import com.example.ikhsanahoteltv.data.model.GuestProfile
import com.example.ikhsanahoteltv.data.model.LiveOrder
import com.example.ikhsanahoteltv.data.model.MenuCategory
import com.example.ikhsanahoteltv.data.model.MenuItem
import com.example.ikhsanahoteltv.data.model.OrderLineItem
import com.example.ikhsanahoteltv.data.model.OrderStatus
import com.example.ikhsanahoteltv.data.repository.FirestoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiningUiState(
    val menuItems: List<MenuItem> = emptyList(),
    val selectedCategory: MenuCategory = MenuCategory.STARTERS,
    val cart: List<CartItem> = emptyList(),
    val guestName: String = "Guest",
    val roomOrders: List<LiveOrder> = emptyList(),
    val isPlacingOrder: Boolean = false,
    val orderMessage: String? = null,
) {
    val filteredItems: List<MenuItem>
        get() = menuItems.filter { it.category == selectedCategory && it.available }

    val cartTotal: Double
        get() = cart.sumOf { it.lineTotal }

    val cartCount: Int
        get() = cart.sumOf { it.quantity }

    /** Active orders (not yet delivered) for order history display. */
    val activeOrders: List<LiveOrder>
        get() = roomOrders.filter { OrderStatus.fromKey(it.status) != OrderStatus.DELIVERED }
}

class DiningViewModel(
    private val repository: FirestoreRepository,
    private val config: HotelConfig,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiningUiState())
    val uiState: StateFlow<DiningUiState> = _uiState.asStateFlow()

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
            repository.observeMenuItems().collect { items ->
                _uiState.update { it.copy(menuItems = items) }
            }
        }
        viewModelScope.launch {
            repository.observeRoomOrders().collect { orders ->
                _uiState.update { it.copy(roomOrders = orders) }
            }
        }
    }

    fun selectCategory(category: MenuCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun addToCart(item: MenuItem) {
        _uiState.update { state ->
            val existing = state.cart.find { it.menuItem.id == item.id }
            val updatedCart = if (existing != null) {
                state.cart.map {
                    if (it.menuItem.id == item.id) it.copy(quantity = it.quantity + 1) else it
                }
            } else {
                state.cart + CartItem(item)
            }
            state.copy(cart = updatedCart, orderMessage = null)
        }
    }

    fun removeFromCart(item: MenuItem) {
        _uiState.update { state ->
            val existing = state.cart.find { it.menuItem.id == item.id } ?: return@update state
            val updatedCart = if (existing.quantity <= 1) {
                state.cart.filter { it.menuItem.id != item.id }
            } else {
                state.cart.map {
                    if (it.menuItem.id == item.id) it.copy(quantity = it.quantity - 1) else it
                }
            }
            state.copy(cart = updatedCart)
        }
    }

    fun placeOrder() {
        val state = _uiState.value
        if (state.cart.isEmpty() || state.isPlacingOrder) return

        viewModelScope.launch {
            _uiState.update { it.copy(isPlacingOrder = true, orderMessage = null) }
            val order = LiveOrder(
                hotelId = HotelConfig.normalizeHotelId(config.getHotelId()),
                roomNumber = config.roomNumber,
                guestName = state.guestName,
                items = state.cart.map { cartItem ->
                    OrderLineItem(
                        itemId = cartItem.menuItem.id,
                        name = cartItem.menuItem.name,
                        quantity = cartItem.quantity,
                        unitPrice = cartItem.menuItem.price,
                    )
                },
                totalAmount = state.cartTotal,
            )
            repository.placeOrder(order)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            cart = emptyList(),
                            isPlacingOrder = false,
                            orderMessage = "success",
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isPlacingOrder = false, orderMessage = "error")
                    }
                }
        }
    }

    fun clearOrderMessage() {
        _uiState.update { it.copy(orderMessage = null) }
    }

    private fun resetForNewSession(profile: GuestProfile) {
        trackedSessionKey = profile.sessionKey
        _uiState.update {
            it.copy(
                guestName = profile.guestName,
                cart = emptyList(),
                roomOrders = emptyList(),
                selectedCategory = MenuCategory.STARTERS,
                orderMessage = null,
                isPlacingOrder = false,
            )
        }
    }
}
