package com.example.ikhsanahoteltv.ui.dining

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.ikhsanahoteltv.R
import com.example.ikhsanahoteltv.data.model.LiveOrder
import com.example.ikhsanahoteltv.data.model.MenuCategory
import com.example.ikhsanahoteltv.data.model.MenuItem
import com.example.ikhsanahoteltv.data.model.OrderStatus
import com.example.ikhsanahoteltv.ui.HotelViewModelFactory
import com.example.ikhsanahoteltv.ui.components.LuxuryGlassPanel
import com.example.ikhsanahoteltv.ui.components.LuxuryScreenBackground
import com.example.ikhsanahoteltv.ui.components.LuxuryScreenHeader
import com.example.ikhsanahoteltv.ui.components.luxuryBackHandler
import com.example.ikhsanahoteltv.ui.theme.FocusTeal
import com.example.ikhsanahoteltv.ui.theme.GoldLight
import com.example.ikhsanahoteltv.ui.theme.GoldPrimary
import com.example.ikhsanahoteltv.ui.theme.NavyDeep
import com.example.ikhsanahoteltv.ui.theme.SansBody
import com.example.ikhsanahoteltv.ui.theme.SerifDisplay
import com.example.ikhsanahoteltv.ui.theme.TextMuted
import com.example.ikhsanahoteltv.ui.theme.TextPrimary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DiningScreen(
    viewModelFactory: HotelViewModelFactory,
    onBack: () -> Unit,
) {
    val viewModel: DiningViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

    val categoryFocus = remember { FocusRequester() }
    val orderFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        categoryFocus.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .luxuryBackHandler(onBack),
    ) {
        LuxuryScreenBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(36.dp),
        ) {
            LuxuryScreenHeader(title = stringResource(R.string.dining_title))

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .width(200.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MenuCategory.entries.forEachIndexed { index, category ->
                        CategoryTab(
                            label = category.displayName,
                            isSelected = uiState.selectedCategory == category,
                            modifier = if (index == 0) Modifier.focusRequester(categoryFocus) else Modifier,
                            onClick = { viewModel.selectCategory(category) },
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.filteredItems, key = { it.id }) { item ->
                        val qty = uiState.cart.find { it.menuItem.id == item.id }?.quantity ?: 0
                        MenuItemRow(
                            item = item,
                            quantity = qty,
                            onAdd = { viewModel.addToCart(item) },
                            onRemove = { viewModel.removeFromCart(item) },
                        )
                    }
                }

                LuxuryGlassPanel(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = "Your Order",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SerifDisplay,
                                color = GoldLight,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (uiState.cart.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.cart_empty),
                                    fontSize = 14.sp,
                                    fontFamily = SansBody,
                                    color = TextMuted,
                                )
                            } else {
                                uiState.cart.forEach { cartItem ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = "${cartItem.quantity}x ${cartItem.menuItem.name}",
                                            fontSize = 14.sp,
                                            fontFamily = SansBody,
                                            color = TextPrimary.copy(alpha = 0.85f),
                                        )
                                        Text(
                                            text = "₹${cartItem.lineTotal.toInt()}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = FocusTeal,
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = stringResource(R.string.order_history),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SerifDisplay,
                                color = TextPrimary,
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (uiState.roomOrders.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.no_orders_yet),
                                    fontSize = 13.sp,
                                    fontFamily = SansBody,
                                    color = TextMuted,
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .heightIn(max = 220.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    uiState.roomOrders.take(5).forEach { order ->
                                        OrderHistoryCard(order = order)
                                    }
                                }
                            }
                        }

                        Column {
                            if (uiState.orderMessage == "success") {
                                Text(
                                    text = stringResource(R.string.order_placed),
                                    fontSize = 14.sp,
                                    color = FocusTeal,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            } else if (uiState.orderMessage == "error") {
                                Text(
                                    text = stringResource(R.string.order_failed),
                                    fontSize = 14.sp,
                                    color = Color(0xFFFB7185),
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }

                            val cartLabel = if (uiState.cart.isEmpty()) {
                                stringResource(R.string.cart_empty)
                            } else {
                                stringResource(
                                    R.string.cart_items,
                                    uiState.cartCount,
                                    uiState.cartTotal,
                                )
                            }

                            OrderButton(
                                text = if (uiState.isPlacingOrder) {
                                    stringResource(R.string.loading)
                                } else {
                                    "${stringResource(R.string.place_order)} — $cartLabel"
                                },
                                enabled = uiState.cart.isNotEmpty() && !uiState.isPlacingOrder,
                                modifier = Modifier.focusRequester(orderFocus),
                                onClick = viewModel::placeOrder,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryTab(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        label = "tabScale",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .background(
                if (isSelected) GoldPrimary.copy(alpha = 0.22f) else NavyDeep.copy(alpha = 0.55f),
                RoundedCornerShape(12.dp),
            )
            .border(
                width = if (isFocused || isSelected) 2.dp else 1.dp,
                color = if (isFocused || isSelected) GoldPrimary else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontFamily = SansBody,
            color = if (isSelected) GoldLight else TextPrimary,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MenuItemRow(
    item: MenuItem,
    quantity: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    var rowFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { rowFocused = it.isFocused }
            .focusable()
            .background(
                NavyDeep.copy(alpha = if (rowFocused) 0.75f else 0.45f),
                RoundedCornerShape(12.dp),
            )
            .border(
                width = if (rowFocused) 2.dp else 1.dp,
                color = if (rowFocused) GoldPrimary else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = SansBody,
                color = TextPrimary,
                maxLines = 1,
                softWrap = false,
            )
            if (item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    fontSize = 13.sp,
                    fontFamily = SansBody,
                    color = TextMuted,
                    maxLines = 2,
                )
            }
        }

        Text(
            text = "₹${item.price.toInt()}",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = FocusTeal,
            modifier = Modifier.padding(horizontal = 16.dp),
            maxLines = 1,
            softWrap = false,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuantityButton(label = "−", onClick = onRemove, enabled = quantity > 0)
            Text(
                text = quantity.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SansBody,
                color = TextPrimary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .align(Alignment.CenterVertically),
            )
            QuantityButton(label = "+", onClick = onAdd)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuantityButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .onKeyEvent { event ->
                if (enabled && event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .background(
                if (focused) GoldPrimary.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(8.dp),
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) GoldPrimary else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 18.sp, color = TextPrimary)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun OrderButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .onKeyEvent { event ->
                if (enabled && event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .background(
                if (enabled) FocusTeal.copy(alpha = if (focused) 0.45f else 0.3f)
                else Color.Gray.copy(alpha = 0.25f),
                RoundedCornerShape(12.dp),
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) FocusTeal else GoldPrimary.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SansBody,
            color = TextPrimary,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun OrderHistoryCard(order: LiveOrder) {
    val status = OrderStatus.fromKey(order.status)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavyDeep.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "₹${order.totalAmount.toInt()}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = FocusTeal,
                maxLines = 1,
                softWrap = false,
            )
            OrderStatusBadge(status = status)
        }
        Spacer(modifier = Modifier.height(4.dp))
        order.items.forEach { line ->
            Text(
                text = "${line.quantity}x ${line.name}",
                fontSize = 12.sp,
                fontFamily = SansBody,
                color = TextMuted,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun OrderStatusBadge(status: OrderStatus) {
    val (bg, label) = when (status) {
        OrderStatus.PENDING -> Color(0xFFFBBF24) to stringResource(R.string.status_pending)
        OrderStatus.PREPARING -> Color(0xFF38BDF8) to stringResource(R.string.status_preparing)
        OrderStatus.DELIVERED -> Color(0xFF34D399) to stringResource(R.string.status_delivered)
    }
    val pillShape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .wrapContentWidth()
            .background(bg.copy(alpha = 0.22f), pillShape)
            .border(1.dp, bg.copy(alpha = 0.6f), pillShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = SansBody,
            color = bg,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}
