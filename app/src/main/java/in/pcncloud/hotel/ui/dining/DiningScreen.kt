package `in`.pcncloud.hotel.ui.dining

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as menuGridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import `in`.pcncloud.hotel.BuildConfig
import `in`.pcncloud.hotel.R
import androidx.compose.ui.window.Dialog
import `in`.pcncloud.hotel.data.model.CartItem
import `in`.pcncloud.hotel.data.model.LiveOrder
import `in`.pcncloud.hotel.data.model.MenuCategory
import `in`.pcncloud.hotel.data.model.MenuItem
import `in`.pcncloud.hotel.data.model.OrderStatus
import `in`.pcncloud.hotel.data.model.PaymentMethod
import `in`.pcncloud.hotel.ui.HotelViewModelFactory
import `in`.pcncloud.hotel.ui.components.BaseScreen
import `in`.pcncloud.hotel.ui.components.hotelImageRequest
import `in`.pcncloud.hotel.ui.components.luxuryGoldFocusChrome
import `in`.pcncloud.hotel.ui.theme.CorpCardBg
import `in`.pcncloud.hotel.ui.theme.CorpGold
import `in`.pcncloud.hotel.ui.theme.CorpGoldBorderIdle
import `in`.pcncloud.hotel.ui.theme.CorpGoldBright
import `in`.pcncloud.hotel.ui.theme.GoldGlassBorder
import `in`.pcncloud.hotel.ui.theme.GoldLight
import `in`.pcncloud.hotel.ui.theme.GoldLuxury
import `in`.pcncloud.hotel.ui.theme.GoldPrimary
import `in`.pcncloud.hotel.ui.theme.NavyDeep
import `in`.pcncloud.hotel.ui.theme.NavySurface
import `in`.pcncloud.hotel.ui.theme.SansBody
import `in`.pcncloud.hotel.ui.theme.SerifDisplay
import `in`.pcncloud.hotel.ui.theme.TextMuted
import `in`.pcncloud.hotel.ui.theme.TextPrimary
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage

private val VegGreen = Color(0xFF22C55E)
private val NonVegRed = Color(0xFFEF4444)
private val GlassDark = Color(0xCC0B1325)
/** Champagne gold — hotel dining focus / CTA accent (#D4AF37). */
private val ChampagneGold = Color(0xFFD4AF37)
private val ChampagneGoldBright = Color(0xFFE8C96A)
private val CartPanelBg = Color(0xE61A1A1A)
private val QtyStepperBg = Color(0x66000000)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DiningScreen(
    viewModelFactory: HotelViewModelFactory,
    onBack: () -> Unit,
    onOpenAdmin: () -> Unit = {},
) {
    val viewModel: DiningViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

    val categoryFocus = remember { FocusRequester() }
    val orderFocus = remember { FocusRequester() }
    val menuGridState = rememberLazyGridState()

    // Local QR placeholder only — never navigates Home / finishReturnFromExternalApp.
    var showQrDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        categoryFocus.requestFocus()
    }

    LaunchedEffect(uiState.selectedCategory) {
        runCatching { menuGridState.scrollToItem(0) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BaseScreen(
            viewModelFactory = viewModelFactory,
            onBack = {
                if (showQrDialog) {
                    showQrDialog = false
                } else {
                    onBack()
                }
            },
            onOpenAdmin = onOpenAdmin,
            title = if (BuildConfig.IS_CORPORATE) {
                "Today's Menu"
            } else {
                stringResource(R.string.dining_title)
            },
            subtitle = if (BuildConfig.IS_CORPORATE) {
                stringResource(R.string.dining_subtitle_corporate)
            } else {
                stringResource(R.string.dining_subtitle)
            },
            showChromeHeader = BuildConfig.IS_CORPORATE,
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            ) {
                itemsIndexed(
                    items = MenuCategory.entries.toList(),
                    key = { _, category -> category.name },
                ) { _, category ->
                    CategoryTab(
                        label = category.displayName,
                        isSelected = uiState.selectedCategory == category,
                        modifier = if (category == uiState.selectedCategory) {
                            Modifier.focusRequester(categoryFocus)
                        } else {
                            Modifier
                        },
                        onClick = { viewModel.selectCategory(category) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(if (BuildConfig.IS_CORPORATE) 1f else 0.68f)
                        .fillMaxHeight(),
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (BuildConfig.IS_CORPORATE) 2 else 3),
                        state = menuGridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(
                            start = 4.dp,
                            top = 12.dp,
                            end = 10.dp,
                            bottom = 40.dp,
                        ),
                    ) {
                        if (uiState.filteredItems.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = stringResource(R.string.menu_empty_category),
                                    fontSize = 20.sp,
                                    fontFamily = SansBody,
                                    color = TextMuted,
                                    modifier = Modifier.padding(28.dp),
                                )
                            }
                        } else {
                            menuGridItems(
                                items = uiState.filteredItems,
                                key = { _, item -> item.id },
                            ) { index, item ->
                                val qty = uiState.cart.find { it.menuItem.id == item.id }?.quantity ?: 0
                                val columns = if (BuildConfig.IS_CORPORATE) 2 else 3
                                MenuItemCard(
                                    item = item,
                                    quantity = qty,
                                    canOrder = !BuildConfig.IS_CORPORATE,
                                    upFocus = if (index < columns) categoryFocus else null,
                                    onAdd = { viewModel.addToCart(item) },
                                    onRemove = { viewModel.removeFromCart(item) },
                                )
                            }
                        }
                    }
                    GoldMenuScrollbar(
                        gridState = menuGridState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = 12.dp),
                    )
                }

                // Hotel in-room dining keeps cart + place-order. Corporate is browse-only.
                if (!BuildConfig.IS_CORPORATE) {
                    OrderSummaryPanel(
                        cart = uiState.cart,
                        cartTotal = uiState.cartTotal,
                        roomOrders = uiState.roomOrders,
                        orderMessage = uiState.orderMessage,
                        isPlacingOrder = uiState.isPlacingOrder,
                        roomOccupied = uiState.roomOccupied,
                        selectedPayment = uiState.selectedPayment,
                        onSelectPayment = viewModel::selectPayment,
                        onPayNow = {
                            // ONLY open QR placeholder — no Home navigation, no intents.
                            viewModel.selectPayment(PaymentMethod.PAID_ONLINE)
                            showQrDialog = true
                        },
                        orderFocus = orderFocus,
                        onPlaceOrder = {
                            when (uiState.selectedPayment) {
                                PaymentMethod.PAID_ONLINE -> showQrDialog = true
                                PaymentMethod.PAY_AT_CHECKOUT -> viewModel.requestPlaceOrder()
                            }
                        },
                        modifier = Modifier
                            .weight(0.32f)
                            .fillMaxHeight(),
                    )
                }
            }
        }

        if (showQrDialog) {
            QrPlaceholderOverlay(
                onDismiss = { showQrDialog = false },
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

    Box(
        modifier = modifier
            .wrapContentWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0.15f)
            }
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
            .luxuryGoldFocusChrome(focused = isFocused || isSelected, shape = shape)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
            fontFamily = SansBody,
            color = if (isSelected || isFocused) CorpGoldBright else CorpGold,
            maxLines = 1,
        )
    }
}

@Composable
private fun GoldMenuScrollbar(
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
) {
    val info = gridState.layoutInfo
    val visible = info.visibleItemsInfo
    val total = info.totalItemsCount
    if (total < 6 || visible.isEmpty()) return

    val spacing = info.mainAxisItemSpacing.toFloat()
    val avgSize = visible.map { it.size.height }.average().toFloat().coerceAtLeast(1f)
    val rowCount = ((total + 1) / 2).coerceAtLeast(1)
    val contentPx = avgSize * rowCount + spacing * (rowCount - 1).coerceAtLeast(0)
    val viewportPx = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    if (contentPx <= viewportPx + 16f) return

    val firstRow = gridState.firstVisibleItemIndex / 2
    val scrollPx = firstRow * (avgSize + spacing) +
        gridState.firstVisibleItemScrollOffset
    val scrollable = (contentPx - viewportPx).coerceAtLeast(1f)
    val progress = (scrollPx / scrollable).coerceIn(0f, 1f)
    val thumbFraction = (viewportPx / contentPx).coerceIn(0.14f, 0.72f)

    Canvas(
        modifier = modifier.width(8.dp),
    ) {
        val trackW = size.width
        val radius = trackW / 2f
        drawRoundRect(
            color = CorpGold.copy(alpha = 0.22f),
            cornerRadius = CornerRadius(radius, radius),
        )
        val thumbH = size.height * thumbFraction
        val thumbTop = (size.height - thumbH) * progress
        drawRoundRect(
            color = CorpGoldBright,
            topLeft = Offset(0f, thumbTop),
            size = Size(trackW, thumbH),
            cornerRadius = CornerRadius(radius, radius),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MenuItemCard(
    item: MenuItem,
    quantity: Int,
    canOrder: Boolean,
    upFocus: FocusRequester? = null,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    var rowFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (rowFocused) 1.03f else 1f,
        animationSpec = tween(150),
        label = "cardScale",
    )
    val shape = RoundedCornerShape(14.dp)
    val context = LocalContext.current
    val imageUrl = item.imageUrl.trim()
    val subtitle = if (canOrder) {
        "₹${item.price.toInt()}"
    } else {
        item.description.trim()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(136.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0.12f)
            }
            .onFocusChanged { rowFocused = it.hasFocus }
            .then(if (canOrder) Modifier else Modifier.focusable())
            .clip(shape)
            .border(
                width = if (rowFocused) 2.dp else 1.dp,
                color = if (rowFocused) ChampagneGold else Color.White.copy(alpha = 0.10f),
                shape = shape,
            ),
    ) {
        if (imageUrl.isNotEmpty()) {
            AsyncImage(
                model = hotelImageRequest(context, imageUrl, "DiningFood"),
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2A2218), Color(0xFF0F1218)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "🍽", fontSize = 28.sp)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.15f),
                        0.40f to Color.Black.copy(alpha = 0.35f),
                        1f to Color.Black.copy(alpha = 0.82f),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            VegBadge(isVeg = item.isVeg)

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = item.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = SansBody,
                color = if (rowFocused) ChampagneGoldBright else TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 22.sp,
            )

            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    fontWeight = if (canOrder) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = SansBody,
                    color = if (canOrder) ChampagneGold else TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (canOrder) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    QuantityStepper(
                        quantity = quantity,
                        onAdd = onAdd,
                        onRemove = onRemove,
                        compact = false,
                        upFocus = upFocus,
                    )
                }
            }
        }
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
    compact: Boolean = false,
    upFocus: FocusRequester? = null,
) {
    val shape = RoundedCornerShape(if (compact) 8.dp else 12.dp)
    val qtyWidth = if (compact) 22.dp else 28.dp
    val qtySize = if (compact) 14.sp else 17.sp
    Row(
        modifier = Modifier
            .background(QtyStepperBg, shape)
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
            .padding(
                horizontal = if (compact) 4.dp else 6.dp,
                vertical = if (compact) 2.dp else 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
    ) {
        QuantityButton(
            label = "−",
            onClick = onRemove,
            enabled = quantity > 0,
            compact = compact,
            upFocus = upFocus,
        )
        Text(
            text = quantity.toString(),
            fontSize = qtySize,
            fontWeight = FontWeight.Bold,
            fontFamily = SansBody,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(qtyWidth)
                .padding(horizontal = 1.dp),
            maxLines = 1,
        )
        QuantityButton(
            label = "+",
            onClick = onAdd,
            compact = compact,
            upFocus = upFocus,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuantityButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    compact: Boolean = false,
    upFocus: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (compact) 6.dp else 8.dp)
    val buttonSize = if (compact) 36.dp else 44.dp

    Box(
        modifier = Modifier
            .size(buttonSize)
            .then(
                if (upFocus != null) {
                    Modifier.focusProperties { up = upFocus }
                } else {
                    Modifier
                },
            )
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
                    focused -> CorpGoldBright.copy(alpha = 0.35f)
                    else -> Color.White.copy(alpha = 0.08f)
                },
                shape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> CorpGoldBright
                    enabled -> CorpGoldBorderIdle
                    else -> Color.White.copy(alpha = 0.06f)
                },
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = if (compact) 15.sp else 18.sp,
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
    onPayNow: () -> Unit,
    orderFocus: FocusRequester,
    onPlaceOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var footerHeight by remember { mutableStateOf(168.dp) }
    val hasItems = cart.isNotEmpty()
    val ctaText = when {
        !roomOccupied -> stringResource(R.string.vacant_room_cta_hint)
        isPlacingOrder -> stringResource(R.string.loading)
        !hasItems -> stringResource(R.string.cart_add_items_cta)
        selectedPayment == PaymentMethod.PAID_ONLINE ->
            stringResource(R.string.pay_now) + " · ₹${cartTotal.toInt()}"
        else -> stringResource(R.string.cart_confirm_cta, cartTotal)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CartPanelBg, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = footerHeight),
        ) {
            Text(
                text = stringResource(R.string.your_order),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SerifDisplay,
                color = ChampagneGold,
            )
            Text(
                text = stringResource(R.string.order_review),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = SansBody,
                color = TextMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
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
                        fontSize = 15.sp,
                        fontFamily = SansBody,
                        color = TextMuted,
                    )
                } else {
                    cart.forEach { cartItem ->
                        key(cartItem.menuItem.id) {
                            CartLineRow(cartItem = cartItem)
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(CartPanelBg)
                .padding(top = 8.dp)
                .onSizeChanged { size ->
                    footerHeight = with(density) { size.height.toDp() }
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (hasItems) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
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

            PaymentToggle(
                selected = selectedPayment,
                onSelectCheckout = { onSelectPayment(PaymentMethod.PAY_AT_CHECKOUT) },
                onPayNow = onPayNow,
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
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .background(NavyDeep.copy(alpha = 0.45f), shape)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) CorpGoldBright else CorpGoldBorderIdle,
                shape = shape,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cartItem.menuItem.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = SansBody,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "× ${cartItem.quantity}",
                fontSize = 14.sp,
                fontFamily = SansBody,
                color = GoldLuxury,
            )
        }
        Text(
            text = "₹${cartItem.lineTotal.toInt()}",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
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
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(160),
        label = "ctaScale",
    )

    val background = when {
        !highlighted -> Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.05f)),
        )
        focused -> Brush.verticalGradient(
            listOf(ChampagneGoldBright, ChampagneGold),
        )
        else -> Brush.verticalGradient(
            listOf(ChampagneGold, Color(0xFFB8962E)),
        )
    }
    val borderColor = when {
        focused -> ChampagneGoldBright
        highlighted -> ChampagneGold.copy(alpha = 0.85f)
        else -> Color.White.copy(alpha = 0.14f)
    }
    val textColor = when {
        !highlighted -> TextMuted
        else -> Color(0xFF1A1208)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = 44.dp)
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
                width = if (focused) 2.5.dp else 1.dp,
                color = borderColor,
                shape = shape,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .background(NavyDeep.copy(alpha = 0.5f), shape)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) CorpGoldBright else CorpGoldBorderIdle,
                shape = shape,
            )
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
    onSelectCheckout: () -> Unit,
    onPayNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.payment_method_title),
            fontSize = 12.sp,
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
                onClick = onSelectCheckout,
            )
            PaymentCard(
                icon = "▦",
                title = stringResource(R.string.pay_now),
                subtitle = stringResource(R.string.pay_now_sub),
                isSelected = selected == PaymentMethod.PAID_ONLINE,
                modifier = Modifier.weight(1f),
                // ONLY showQrDialog via parent — never navigate Home.
                onClick = onPayNow,
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
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(150),
        label = "payScale",
    )
    val borderColor = when {
        focused -> ChampagneGold
        isSelected -> ChampagneGold.copy(alpha = 0.85f)
        else -> Color.White.copy(alpha = 0.10f)
    }
    val bgColor = when {
        focused -> ChampagneGold.copy(alpha = 0.28f)
        isSelected -> ChampagneGold.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.06f)
    }

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(bgColor, shape)
            .border(
                width = if (isSelected || focused) 2.5.dp else 1.dp,
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
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = icon, fontSize = 16.sp, color = ChampagneGold)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SansBody,
                    color = if (isSelected || focused) ChampagneGoldBright else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    fontSize = 9.sp,
                    fontFamily = SansBody,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// QR Placeholder Overlay (in-composition — no Dialog window / no Home reclaim)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QrPlaceholderOverlay(
    onDismiss: () -> Unit,
) {
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { closeFocus.requestFocus() }
    }

    // Full-screen overlay inside DiningScreen — does NOT use Compose Dialog
    // (Dialog windows can fire onUserLeaveHint → Physical TV Home reclaim).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
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
                // Blank placeholder box — future Razorpay QR code container.
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(2.dp, GoldLuxury.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "QR",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = NavyDeep.copy(alpha = 0.35f),
                    )
                }
                Text(
                    text = stringResource(R.string.qr_dialog_hint),
                    fontSize = 13.sp,
                    fontFamily = SansBody,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                )
                QrDialogButton(
                    text = stringResource(R.string.qr_dialog_cancel),
                    highlighted = true,
                    modifier = Modifier.focusRequester(closeFocus),
                    onClick = onDismiss,
                )
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
