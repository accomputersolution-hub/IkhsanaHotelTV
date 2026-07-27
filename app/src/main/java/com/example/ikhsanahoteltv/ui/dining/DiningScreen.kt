package com.example.ikhsanahoteltv.ui.dining

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.ikhsanahoteltv.R
import androidx.compose.ui.window.Dialog
import com.example.ikhsanahoteltv.data.model.CartItem
import com.example.ikhsanahoteltv.data.model.LiveOrder
import com.example.ikhsanahoteltv.data.model.MenuCategory
import com.example.ikhsanahoteltv.data.model.MenuItem
import com.example.ikhsanahoteltv.data.model.OrderStatus
import com.example.ikhsanahoteltv.data.model.PaymentMethod
import com.example.ikhsanahoteltv.ui.HotelViewModelFactory
import com.example.ikhsanahoteltv.ui.components.LuxuryGlassPanel
import com.example.ikhsanahoteltv.ui.components.LuxuryScreenBackground
import com.example.ikhsanahoteltv.ui.components.LuxuryScreenHeader
import com.example.ikhsanahoteltv.ui.components.luxuryBackHandler
import com.example.ikhsanahoteltv.ui.theme.GoldGlassBorder
import com.example.ikhsanahoteltv.ui.theme.GoldGlassFill
import com.example.ikhsanahoteltv.ui.theme.GoldLight
import com.example.ikhsanahoteltv.ui.theme.GoldLuxury
import com.example.ikhsanahoteltv.ui.theme.GoldPrimary
import com.example.ikhsanahoteltv.ui.theme.NavyDeep
import com.example.ikhsanahoteltv.ui.theme.NavySurface
import com.example.ikhsanahoteltv.ui.theme.SansBody
import com.example.ikhsanahoteltv.ui.theme.SerifDisplay
import com.example.ikhsanahoteltv.ui.theme.TextMuted
import com.example.ikhsanahoteltv.ui.theme.TextPrimary

private val VegGreen = Color(0xFF22C55E)
private val NonVegRed = Color(0xFFEF4444)
private val GlassDark = Color(0xCC0B1325)

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
                .padding(horizontal = 32.dp, vertical = 28.dp),
        ) {
            LuxuryScreenHeader(
                title = stringResource(R.string.dining_title),
                subtitle = stringResource(R.string.dining_subtitle),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── Top category bar (horizontal) ─────────────────────────────
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(
                    items = MenuCategory.entries.toList(),
                    key = { _, category -> category.name },
                ) { index, category ->
                    CategoryTab(
                        label = category.displayName,
                        isSelected = uiState.selectedCategory == category,
                        modifier = if (index == 0) {
                            Modifier.focusRequester(categoryFocus)
                        } else {
                            Modifier
                        },
                        onClick = { viewModel.selectCategory(category) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Content: full-width menu list + order panel ───────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (uiState.filteredItems.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.menu_empty_category),
                                fontSize = 15.sp,
                                fontFamily = SansBody,
                                color = TextMuted,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    } else {
                        items(uiState.filteredItems, key = { it.id }) { item ->
                            val qty = uiState.cart.find { it.menuItem.id == item.id }?.quantity ?: 0
                            MenuItemCard(
                                item = item,
                                quantity = qty,
                                onAdd = { viewModel.addToCart(item) },
                                onRemove = { viewModel.removeFromCart(item) },
                            )
                        }
                    }
                }

                OrderSummaryPanel(
                    cart = uiState.cart,
                    cartTotal = uiState.cartTotal,
                    roomOrders = uiState.roomOrders,
                    orderMessage = uiState.orderMessage,
                    isPlacingOrder = uiState.isPlacingOrder,
                    roomOccupied = uiState.roomOccupied,
                    selectedPayment = uiState.selectedPayment,
                    onSelectPayment = viewModel::selectPayment,
                    orderFocus = orderFocus,
                    onPlaceOrder = viewModel::requestPlaceOrder,
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight(),
                )
            }
        }

        // ── Overlays (outside content row so focus/layout stay clean) ────
        uiState.pendingQrTotal?.let { total ->
            QrPaymentDialog(
                total = total,
                onConfirm = viewModel::confirmQrPayment,
                onDismiss = viewModel::dismissQrDialog,
            )
        }
        if (uiState.showVacantRoomDialog) {
            VacantRoomDialog(onDismiss = viewModel::dismissVacantRoomDialog)
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
        animationSpec = tween(150),
        label = "tabScale",
    )
    val shape = RoundedCornerShape(999.dp)
    val borderColor = when {
        isFocused -> GoldLuxury
        isSelected -> GoldLuxury.copy(alpha = 0.85f)
        else -> Color.White.copy(alpha = 0.12f)
    }
    val fill = when {
        isSelected -> Brush.horizontalGradient(
            listOf(GoldLuxury.copy(alpha = 0.28f), GoldGlassFill),
        )
        isFocused -> Brush.horizontalGradient(
            listOf(GoldLuxury.copy(alpha = 0.14f), NavyDeep.copy(alpha = 0.55f)),
        )
        else -> Brush.horizontalGradient(
            listOf(NavyDeep.copy(alpha = 0.55f), NavyDeep.copy(alpha = 0.45f)),
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .wrapContentWidth()
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
            .background(brush = fill, shape = shape)
            .border(
                width = if (isFocused || isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = shape,
            )
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
            fontFamily = SansBody,
            color = if (isSelected || isFocused) GoldLight else TextPrimary,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MenuItemCard(
    item: MenuItem,
    quantity: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    var rowFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (rowFocused) 1.015f else 1f,
        animationSpec = tween(150),
        label = "cardScale",
    )
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { rowFocused = it.isFocused }
            .focusable()
            .background(
                brush = Brush.horizontalGradient(
                    listOf(
                        NavySurface.copy(alpha = if (rowFocused) 0.92f else 0.78f),
                        NavyDeep.copy(alpha = if (rowFocused) 0.88f else 0.70f),
                    ),
                ),
                shape = shape,
            )
            .border(
                width = if (rowFocused) 2.dp else 1.dp,
                color = if (rowFocused) GoldLuxury else Color.White.copy(alpha = 0.10f),
                shape = shape,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Food image with veg badge overlay
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NavyDeep.copy(alpha = 0.85f)),
        ) {
            if (item.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    GoldLuxury.copy(alpha = 0.12f),
                                    NavyDeep.copy(alpha = 0.9f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "🍽", fontSize = 28.sp)
                }
            }

            VegBadge(
                isVeg = item.isVeg,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            )
        }

        // Title / description / price — full width, no title truncation
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = item.name,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = SansBody,
                color = TextPrimary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
            )
            if (item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    fontSize = 14.sp,
                    fontFamily = SansBody,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                )
            }
            Text(
                text = "₹${item.price.toInt()}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SansBody,
                color = GoldLuxury,
                maxLines = 1,
            )
        }

        // Quantity stepper pinned to the far right
        QuantityStepper(
            quantity = quantity,
            onAdd = onAdd,
            onRemove = onRemove,
        )
    }
}

@Composable
private fun VegBadge(
    isVeg: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = if (isVeg) VegGreen else NonVegRed
    Box(
        modifier = modifier
            .size(18.dp)
            .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(3.dp))
            .border(1.5.dp, accent, RoundedCornerShape(3.dp))
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(accent, RoundedCornerShape(1.dp)),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuantityStepper(
    quantity: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .background(GlassDark, shape)
            .border(1.dp, GoldGlassBorder, shape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        QuantityButton(label = "−", onClick = onRemove, enabled = quantity > 0)
        Text(
            text = quantity.toString(),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SansBody,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(28.dp)
                .padding(horizontal = 2.dp),
            maxLines = 1,
        )
        QuantityButton(label = "+", onClick = onAdd)
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
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = Modifier
            .size(36.dp)
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
                when {
                    !enabled -> Color.White.copy(alpha = 0.04f)
                    focused -> GoldLuxury.copy(alpha = 0.35f)
                    else -> Color.White.copy(alpha = 0.08f)
                },
                shape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> GoldLuxury
                    enabled -> Color.White.copy(alpha = 0.14f)
                    else -> Color.White.copy(alpha = 0.06f)
                },
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) TextPrimary else TextMuted.copy(alpha = 0.45f),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun OrderSummaryPanel(
    cart: List<CartItem>,
    cartTotal: Double,
    roomOrders: List<LiveOrder>,
    orderMessage: String?,
    isPlacingOrder: Boolean,
    roomOccupied: Boolean,
    selectedPayment: PaymentMethod,
    onSelectPayment: (PaymentMethod) -> Unit,
    orderFocus: FocusRequester,
    onPlaceOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LuxuryGlassPanel(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.your_order),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SerifDisplay,
                color = GoldLight,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (cart.isEmpty()) {
                    Text(
                        text = stringResource(R.string.cart_empty),
                        fontSize = 14.sp,
                        fontFamily = SansBody,
                        color = TextMuted,
                    )
                } else {
                    cart.forEach { cartItem ->
                        CartLineRow(cartItem = cartItem)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.order_total),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = SansBody,
                            color = TextPrimary,
                        )
                        Text(
                            text = "₹${cartTotal.toInt()}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldLuxury,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.order_history),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SerifDisplay,
                    color = TextPrimary,
                )

                if (roomOrders.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_orders_yet),
                        fontSize = 13.sp,
                        fontFamily = SansBody,
                        color = TextMuted,
                    )
                } else {
                    roomOrders.take(4).forEach { order ->
                        OrderHistoryCard(order = order)
                    }
                }
            }

            // ── Payment method toggle ─────────────────────────────────
            PaymentToggle(
                selected = selectedPayment,
                onSelect = onSelectPayment,
            )

            if (orderMessage == "success") {
                Text(
                    text = stringResource(R.string.order_placed),
                    fontSize = 13.sp,
                    color = VegGreen,
                )
            } else if (orderMessage == "error") {
                Text(
                    text = stringResource(R.string.order_failed),
                    fontSize = 13.sp,
                    color = NonVegRed,
                )
            }

            val hasItems = cart.isNotEmpty()
            val ctaText = when {
                !roomOccupied -> stringResource(R.string.vacant_room_cta_hint)
                isPlacingOrder -> stringResource(R.string.loading)
                !hasItems -> stringResource(R.string.cart_add_items_cta)
                selectedPayment == PaymentMethod.PAID_ONLINE ->
                    stringResource(R.string.pay_now) + " · ₹${cartTotal.toInt()}"
                else -> stringResource(R.string.cart_confirm_cta, cartTotal)
            }

            PlaceOrderCta(
                text = ctaText,
                enabled = hasItems && !isPlacingOrder && roomOccupied,
                highlighted = hasItems && roomOccupied,
                modifier = Modifier.focusRequester(orderFocus),
                onClick = onPlaceOrder,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CartLineRow(cartItem: CartItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavyDeep.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cartItem.menuItem.name,
                fontSize = 13.sp,
                fontFamily = SansBody,
                color = TextPrimary.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "× ${cartItem.quantity}",
                fontSize = 12.sp,
                fontFamily = SansBody,
                color = TextMuted,
            )
        }
        Text(
            text = "₹${cartItem.lineTotal.toInt()}",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = GoldLuxury,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaceOrderCta(
    text: String,
    enabled: Boolean,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val scale by animateFloatAsState(
        targetValue = if (focused && enabled) 1.03f else 1f,
        animationSpec = tween(160),
        label = "ctaScale",
    )

    val background = when {
        !highlighted -> Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.04f)),
        )
        focused -> Brush.verticalGradient(
            listOf(GoldLight, GoldLuxury, GoldPrimary),
        )
        else -> Brush.verticalGradient(
            listOf(GoldLuxury.copy(alpha = 0.95f), GoldPrimary.copy(alpha = 0.85f)),
        )
    }
    val borderColor = when {
        focused && highlighted -> GoldLight
        highlighted -> GoldLuxury.copy(alpha = 0.7f)
        else -> Color.White.copy(alpha = 0.12f)
    }
    val textColor = when {
        !highlighted -> TextMuted
        focused -> NavyDeep
        else -> NavyDeep.copy(alpha = 0.92f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = 52.dp)
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
            .background(brush = background, shape = shape)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = borderColor,
                shape = shape,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = if (highlighted) 14.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SansBody,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
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
                color = GoldLuxury,
                maxLines = 1,
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
                overflow = TextOverflow.Ellipsis,
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
            overflow = TextOverflow.Clip,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Vacant Room Blocking Dialog
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VacantRoomDialog(onDismiss: () -> Unit) {
    val dismissFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { dismissFocus.requestFocus() }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .background(NavySurface, RoundedCornerShape(24.dp))
                .border(2.dp, NonVegRed.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Warning icon
                Text(text = "🚫", fontSize = 40.sp)

                Text(
                    text = stringResource(R.string.vacant_room_dialog_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SerifDisplay,
                    color = NonVegRed,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.vacant_room_dialog_message),
                    fontSize = 14.sp,
                    fontFamily = SansBody,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                )

                // Dismiss CTA — reuses QrDialogButton style
                QrDialogButton(
                    text = stringResource(R.string.vacant_room_dialog_cta),
                    highlighted = true,
                    modifier = Modifier
                        .focusRequester(dismissFocus)
                        .fillMaxWidth(0.6f),
                    onClick = onDismiss,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Payment Toggle
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PaymentToggle(
    selected: PaymentMethod,
    onSelect: (PaymentMethod) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.payment_method_title),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = SansBody,
            color = TextMuted,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PaymentCard(
                icon = "🛏",
                title = stringResource(R.string.pay_at_checkout),
                subtitle = stringResource(R.string.pay_at_checkout_sub),
                isSelected = selected == PaymentMethod.PAY_AT_CHECKOUT,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(PaymentMethod.PAY_AT_CHECKOUT) },
            )
            PaymentCard(
                icon = "📲",
                title = stringResource(R.string.pay_now),
                subtitle = stringResource(R.string.pay_now_sub),
                isSelected = selected == PaymentMethod.PAID_ONLINE,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(PaymentMethod.PAID_ONLINE) },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PaymentCard(
    icon: String,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(150),
        label = "payScale",
    )
    val borderColor = when {
        isSelected -> GoldLuxury
        focused -> GoldLuxury.copy(alpha = 0.7f)
        else -> Color.White.copy(alpha = 0.10f)
    }
    val bgAlpha = if (isSelected) 0.22f else if (focused) 0.12f else 0.06f

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(GoldLuxury.copy(alpha = bgAlpha), shape)
            .border(
                width = if (isSelected || focused) 2.dp else 1.dp,
                color = borderColor,
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onClick(); true
                } else false
            }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = icon, fontSize = 18.sp)
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SansBody,
                color = if (isSelected) GoldLuxury else TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                fontFamily = SansBody,
                color = TextMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// QR Payment Dialog
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QrPaymentDialog(
    total: Double,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { confirmFocus.requestFocus() }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .background(NavySurface, RoundedCornerShape(24.dp))
                .border(2.dp, GoldLuxury.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.qr_dialog_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SerifDisplay,
                    color = GoldLight,
                )
                Text(
                    text = stringResource(R.string.qr_dialog_amount, total),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldLuxury,
                )
                // QR code placeholder — replace with a real QR library if needed
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(2.dp, GoldLuxury.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "QR",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = NavyDeep,
                    )
                }
                Text(
                    text = stringResource(R.string.qr_dialog_hint),
                    fontSize = 13.sp,
                    fontFamily = SansBody,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Cancel
                    QrDialogButton(
                        text = stringResource(R.string.qr_dialog_cancel),
                        highlighted = false,
                        onClick = onDismiss,
                    )
                    // Confirm
                    QrDialogButton(
                        text = stringResource(R.string.qr_dialog_confirm),
                        highlighted = true,
                        modifier = Modifier.focusRequester(confirmFocus),
                        onClick = onConfirm,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QrDialogButton(
    text: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(150),
        label = "qrBtnScale",
    )
    val bgBrush = if (highlighted) {
        if (focused) Brush.verticalGradient(listOf(GoldLight, GoldLuxury))
        else Brush.verticalGradient(listOf(GoldLuxury.copy(0.95f), GoldPrimary.copy(0.85f)))
    } else {
        Brush.verticalGradient(listOf(Color.White.copy(0.08f), Color.White.copy(0.04f)))
    }
    val borderColor = if (focused && highlighted) GoldLight
    else if (highlighted) GoldLuxury.copy(0.7f)
    else Color.White.copy(0.15f)
    val textColor = if (highlighted) (if (focused) NavyDeep else NavyDeep.copy(0.92f)) else TextMuted

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(brush = bgBrush, shape = shape)
            .border(if (focused) 2.dp else 1.dp, borderColor, shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onClick(); true
                } else false
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SansBody,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
