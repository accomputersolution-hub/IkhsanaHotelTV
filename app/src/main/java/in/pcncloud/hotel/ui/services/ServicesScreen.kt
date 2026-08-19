package `in`.pcncloud.hotel.ui.services

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import `in`.pcncloud.hotel.BuildConfig
import `in`.pcncloud.hotel.MainActivity
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.data.model.ServiceRequest
import `in`.pcncloud.hotel.ui.HotelViewModelFactory
import `in`.pcncloud.hotel.ui.components.BaseScreen
import `in`.pcncloud.hotel.ui.components.ServiceToast
import `in`.pcncloud.hotel.ui.components.luxuryGoldFocusChrome
import `in`.pcncloud.hotel.ui.theme.CorpGold
import `in`.pcncloud.hotel.ui.theme.CorpGoldBright
import `in`.pcncloud.hotel.ui.theme.FocusCyan
import `in`.pcncloud.hotel.ui.theme.FocusTeal
import `in`.pcncloud.hotel.ui.theme.GoldGlassBorder
import `in`.pcncloud.hotel.ui.theme.GoldGlassFill
import `in`.pcncloud.hotel.ui.theme.GoldLight
import `in`.pcncloud.hotel.ui.theme.GoldLuxury
import `in`.pcncloud.hotel.ui.theme.GoldPrimary
import `in`.pcncloud.hotel.ui.theme.NavyDeep
import `in`.pcncloud.hotel.ui.theme.NavySurface
import `in`.pcncloud.hotel.ui.theme.SansBody
import `in`.pcncloud.hotel.ui.theme.SerifDisplay
import `in`.pcncloud.hotel.ui.theme.TextMuted
import `in`.pcncloud.hotel.ui.theme.TextPrimary

private val VacantRed = Color(0xFFEF4444)
private val GlassCardFill = Color(0xCC0B1325)

private val CORPORATE_CONTACT_ICONS = listOf("💻", "☕", "📞", "🛠", "📋", "🆘")

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ServicesScreen(
    viewModelFactory: HotelViewModelFactory,
    onBack: () -> Unit,
    onOpenAdmin: () -> Unit = {},
    /** Hotel flavor: "housekeeping" | "concierge" | null (all). Ignored for corporate. */
    departmentFilter: String? = null,
) {
    if (BuildConfig.IS_CORPORATE) {
        CorporateServicesScreen(
            viewModelFactory = viewModelFactory,
            onBack = onBack,
            onOpenAdmin = onOpenAdmin,
        )
    } else {
        HotelServicesScreen(
            viewModelFactory = viewModelFactory,
            onBack = onBack,
            onOpenAdmin = onOpenAdmin,
            departmentFilter = departmentFilter,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CorporateServicesScreen(
    viewModelFactory: HotelViewModelFactory,
    onBack: () -> Unit,
    onOpenAdmin: () -> Unit = {},
) {
    val viewModel: CorporateServicesViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val firstItemFocus = remember { FocusRequester() }

    LaunchedEffect(uiState.isLoading, uiState.contacts.size) {
        if (!uiState.isLoading && uiState.contacts.isNotEmpty()) {
            runCatching { firstItemFocus.requestFocus() }
        }
    }

    val subtitle = if (uiState.isLoading) {
        "Loading contacts…"
    } else {
        "Internal extensions for IT, pantry, and front desk"
    }

    BaseScreen(
        viewModelFactory = viewModelFactory,
        onBack = onBack,
        onOpenAdmin = onOpenAdmin,
        title = "Emergency Contacts & Helpdesk",
        subtitle = subtitle,
    ) {
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AndroidView(
                            factory = { context ->
                                android.widget.ProgressBar(context).apply {
                                    isIndeterminate = true
                                }
                            },
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = "Loading emergency contacts…",
                            fontSize = 18.sp,
                            fontFamily = FontFamily.SansSerif,
                            color = TextMuted,
                        )
                    }
                }
            }

            uiState.contacts.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No emergency contacts configured",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        top = 28.dp,
                        end = 12.dp,
                        bottom = 32.dp,
                    ),
                ) {
                    itemsIndexed(
                        items = uiState.contacts,
                        key = { _, contact -> contact.id.ifBlank { contact.title } },
                    ) { index, contact ->
                        CorporateSupportCard(
                            title = contact.title,
                            extension = contact.extension,
                            icon = CORPORATE_CONTACT_ICONS[index % CORPORATE_CONTACT_ICONS.size],
                            modifier = if (index == 0) {
                                Modifier.focusRequester(firstItemFocus)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CorporateSupportCard(
    title: String,
    extension: String,
    icon: String,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(150),
        label = "corporateContactScale",
    )
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 168.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0.12f)
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .luxuryGoldFocusChrome(focused = focused, shape = shape)
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Clean icon — no gold circular badge.
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = icon,
                fontSize = 32.sp,
            )
        }

        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            color = if (focused) CorpGoldBright else CorpGold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 24.sp,
        )

        // Extension numbers — large, bold, high-contrast; single line for 10-foot UI.
        Text(
            text = extension,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            color = if (focused) CorpGoldBright else Color.White,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 26.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HotelServicesScreen(
    viewModelFactory: HotelViewModelFactory,
    onBack: () -> Unit,
    onOpenAdmin: () -> Unit = {},
    departmentFilter: String? = null,
) {
    val viewModel: ServicesViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val firstItemFocus = remember { FocusRequester() }
    val options = remember(departmentFilter, viewModel.serviceOptions) {
        val filter = departmentFilter?.trim()?.lowercase().orEmpty()
        if (filter.isBlank()) {
            viewModel.serviceOptions
        } else {
            viewModel.serviceOptions.filter { it.department.equals(filter, ignoreCase = true) }
        }
    }
    val screenTitle = when (departmentFilter?.lowercase()) {
        "housekeeping" -> stringResource(R.string.feature_housekeeping)
        "concierge" -> stringResource(R.string.feature_concierge)
        else -> stringResource(R.string.services_title)
    }
    val screenSubtitle = when (departmentFilter?.lowercase()) {
        "housekeeping" -> stringResource(R.string.feature_housekeeping_subtitle)
        "concierge" -> stringResource(R.string.feature_concierge_subtitle)
        else -> stringResource(R.string.services_subtitle)
    }

    val visibleRequests = remember(uiState.activeRequests, departmentFilter) {
        val filter = departmentFilter?.trim()?.lowercase().orEmpty()
        uiState.activeRequests.filter { request ->
            filter.isBlank() || request.department.equals(filter, ignoreCase = true)
        }
    }
    val pickerOpen = uiState.activeCategory != null || uiState.showVacantRoomDialog

    LaunchedEffect(options.size, uiState.roomOccupied, pickerOpen) {
        if (options.isEmpty() || pickerOpen) return@LaunchedEffect
        delay(50)
        runCatching { firstItemFocus.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BaseScreen(
            viewModelFactory = viewModelFactory,
            onBack = onBack,
            onOpenAdmin = onOpenAdmin,
            title = screenTitle,
            subtitle = screenSubtitle,
            showChromeHeader = false,
        ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 28.dp,
                end = 12.dp,
                bottom = 32.dp,
            ),
        ) {
            itemsIndexed(
                items = options,
                key = { index, option -> "option-${option.serviceType}-$index" },
            ) { index, option ->
                ServiceCard(
                    option = option,
                    enabled = !uiState.isSubmitting && uiState.roomOccupied && !pickerOpen,
                    roomOccupied = uiState.roomOccupied,
                    modifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier,
                    onClick = { viewModel.openCategory(option) },
                )
            }

            if (visibleRequests.isNotEmpty()) {
                item(
                    key = "active-requests-header",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.active_requests),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = SerifDisplay,
                            color = GoldLight,
                        )
                    }
                }
                itemsIndexed(
                    items = visibleRequests,
                    key = { index, request ->
                        val id = request.id.trim().ifBlank { "empty" }
                        "request-$id-$index"
                    },
                    span = { _, _ -> GridItemSpan(maxLineSpan) },
                ) { _, request ->
                    ActiveRequestRow(request = request)
                }
            }
        }
        }

        uiState.toastMessage?.let { message ->
            ServiceToast(
                message = message,
                type = uiState.toastType,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 36.dp, bottom = 36.dp),
            )
        }

        if (uiState.showVacantRoomDialog) {
            VacantRoomDialog(onDismiss = viewModel::dismissVacantRoomDialog)
        }

        uiState.activeCategory?.let { category ->
            SubServiceDialog(
                category = category,
                selections = uiState.subSelections,
                selectedLabels = uiState.selectedItemLabels,
                canSubmit = uiState.canSubmitSubRequest,
                isSubmitting = uiState.isSubmitting,
                onIncrement = viewModel::incrementSubItem,
                onDecrement = viewModel::decrementSubItem,
                onToggle = viewModel::toggleSubItem,
                onSelectChoice = viewModel::selectChoice,
                onSubmit = viewModel::submitSubRequest,
                onDismiss = viewModel::dismissSubDialog,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ServiceCard(
    option: ServiceOption,
    enabled: Boolean,
    roomOccupied: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && roomOccupied) 1.05f else 1f,
        animationSpec = tween(150),
        label = "serviceCardScale",
    )
    val elevation by animateFloatAsState(
        targetValue = if (focused && roomOccupied) 14f else 0f,
        animationSpec = tween(150),
        label = "serviceCardElevation",
    )
    val shape = RoundedCornerShape(16.dp)
    val borderColor = when {
        !roomOccupied -> VacantRed.copy(alpha = 0.35f)
        focused -> GoldLuxury
        else -> Color.White.copy(alpha = 0.10f)
    }
    val fillBrush = when {
        !roomOccupied -> Brush.verticalGradient(
            listOf(VacantRed.copy(alpha = 0.08f), GlassCardFill.copy(alpha = 0.7f)),
        )
        focused -> Brush.verticalGradient(
            listOf(
                GoldLuxury.copy(alpha = 0.22f),
                NavySurface.copy(alpha = 0.88f),
                NavyDeep.copy(alpha = 0.92f),
            ),
        )
        else -> Brush.verticalGradient(
            listOf(
                NavySurface.copy(alpha = 0.82f),
                GlassCardFill,
                NavyDeep.copy(alpha = 0.78f),
            ),
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 168.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = elevation.dp,
                shape = shape,
                ambientColor = GoldLuxury.copy(alpha = 0.4f),
                spotColor = GoldLuxury.copy(alpha = 0.55f),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled = enabled)
            .background(brush = fillBrush, shape = shape)
            .border(
                width = if (focused && roomOccupied) 2.dp else 1.dp,
                color = borderColor,
                shape = shape,
            )
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
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ServiceIconBadge(
            icon = if (roomOccupied) option.icon else "🔒",
            focused = focused && roomOccupied,
            disabled = !roomOccupied,
        )

        Text(
            text = option.label,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SansBody,
            color = if (roomOccupied) {
                if (focused) GoldLight else TextPrimary
            } else {
                TextMuted.copy(alpha = 0.55f)
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 24.sp,
        )

        Text(
            text = if (roomOccupied) {
                option.subtitle
            } else {
                stringResource(R.string.vacant_room_cta_hint)
            },
            fontSize = 13.sp,
            fontFamily = SansBody,
            color = if (roomOccupied) TextMuted else VacantRed.copy(alpha = 0.8f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp,
        )

        if (roomOccupied) {
            Text(
                text = stringResource(R.string.service_tap_to_request),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = SansBody,
                color = if (focused) GoldLuxury.copy(alpha = 0.9f) else TextMuted.copy(alpha = 0.65f),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ServiceIconBadge(
    icon: String,
    focused: Boolean,
    disabled: Boolean,
) {
    val badgeScale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(150),
        label = "serviceIconScale",
    )
    val borderColor = when {
        disabled -> VacantRed.copy(alpha = 0.35f)
        focused -> GoldLuxury.copy(alpha = 0.55f)
        else -> GoldGlassBorder
    }
    val fillBrush = when {
        disabled -> Brush.radialGradient(
            listOf(VacantRed.copy(alpha = 0.12f), Color.White.copy(alpha = 0.04f)),
        )
        focused -> Brush.radialGradient(
            listOf(
                GoldLuxury.copy(alpha = 0.28f),
                GoldGlassFill,
                GoldLuxury.copy(alpha = 0.08f),
            ),
        )
        else -> Brush.radialGradient(
            listOf(
                GoldLuxury.copy(alpha = 0.16f),
                GoldGlassFill,
            ),
        )
    }

    Box(
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = badgeScale
                scaleY = badgeScale
            }
            .background(brush = fillBrush, shape = CircleShape)
            .border(1.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = icon, fontSize = 26.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-service selection dialog
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SubServiceDialog(
    category: ServiceOption,
    selections: Map<String, SubItemSelection>,
    selectedLabels: List<String>,
    canSubmit: Boolean,
    isSubmitting: Boolean,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onToggle: (String) -> Unit,
    onSelectChoice: (String, String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    val usesQuantitySteppers = category.subItems.any { it.kind == SubItemKind.QUANTITY }
    val subtitle = if (usesQuantitySteppers) {
        stringResource(R.string.service_sub_hint_qty)
    } else {
        stringResource(R.string.service_sub_hint_select)
    }

    // In-composition overlay — Compose Dialog windows steal Activity focus on
    // physical TVs and kiosk reclaim dumps the guest back on Home.
    DismissOnKioskBack(onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
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
                    .widthIn(max = 500.dp)
                    .fillMaxWidth(0.92f)
                    .background(NavySurface, RoundedCornerShape(22.dp))
                    .border(2.dp, GoldLuxury.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 28.dp, vertical = 24.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(text = category.icon, fontSize = 28.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = category.label,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SerifDisplay,
                                color = GoldLight,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = subtitle,
                                fontSize = 13.sp,
                                fontFamily = SansBody,
                                color = TextMuted,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        category.subItems.forEachIndexed { index, item ->
                            val selection = selections[item.id] ?: SubItemSelection()
                            SubOptionRow(
                                item = item,
                                selection = selection,
                                modifier = if (index == 0) {
                                    Modifier.focusRequester(firstFocus)
                                } else {
                                    Modifier
                                },
                                onIncrement = { onIncrement(item.id) },
                                onDecrement = { onDecrement(item.id) },
                                onToggle = { onToggle(item.id) },
                                onSelectChoice = { choice -> onSelectChoice(item.id, choice) },
                            )
                        }
                    }

                    if (selectedLabels.isNotEmpty()) {
                        Text(
                            text = selectedLabels.joinToString(" · "),
                            fontSize = 12.sp,
                            fontFamily = SansBody,
                            color = GoldLuxury.copy(alpha = 0.9f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SubDialogButton(
                            text = stringResource(R.string.service_cancel),
                            highlighted = false,
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (category.subItems.isEmpty()) {
                                        Modifier.focusRequester(firstFocus)
                                    } else {
                                        Modifier
                                    },
                                ),
                            onClick = onDismiss,
                        )
                        SubDialogButton(
                            text = if (isSubmitting) {
                                stringResource(R.string.loading)
                            } else {
                                stringResource(R.string.service_submit_request)
                            },
                            highlighted = true,
                            enabled = canSubmit,
                            modifier = Modifier.weight(1.35f),
                            onClick = onSubmit,
                        )
                    }

                    LaunchedEffect(category.serviceType) {
                        delay(50)
                        runCatching { firstFocus.requestFocus() }
                    }
                }
            }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SubOptionRow(
    item: SubServiceItem,
    selection: SubItemSelection,
    modifier: Modifier = Modifier,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onToggle: () -> Unit,
    onSelectChoice: (String) -> Unit,
) {
    var rowFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (rowFocused) 1.05f else 1f,
        animationSpec = tween(150),
        label = "subOptionScale",
    )
    val shape = RoundedCornerShape(14.dp)
    val active = when (item.kind) {
        SubItemKind.QUANTITY -> selection.quantity > 0
        SubItemKind.TOGGLE, SubItemKind.CHOICE -> selection.selected
    }
    val borderColor = when {
        rowFocused -> GoldLuxury
        active -> GoldLuxury.copy(alpha = 0.55f)
        else -> Color.White.copy(alpha = 0.10f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { rowFocused = it.isFocused || it.hasFocus }
            .focusable()
            .onKeyEvent { event ->
                // Row OK activates toggle / selects default choice; qty rows leave OK to child steppers
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    when (item.kind) {
                        SubItemKind.TOGGLE -> {
                            onToggle(); true
                        }
                        SubItemKind.CHOICE -> {
                            val choice = selection.choice
                                ?: item.choices.firstOrNull()
                            if (choice != null) {
                                onSelectChoice(choice); true
                            } else {
                                false
                            }
                        }
                        SubItemKind.QUANTITY -> false
                    }
                } else {
                    false
                }
            }
            .background(
                when {
                    rowFocused -> GoldLuxury.copy(alpha = 0.16f)
                    active -> GoldLuxury.copy(alpha = 0.12f)
                    else -> NavyDeep.copy(alpha = 0.55f)
                },
                shape,
            )
            .border(
                width = if (rowFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = shape,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (item.kind) {
            SubItemKind.QUANTITY -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.label,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = SansBody,
                        color = if (rowFocused) GoldLight else TextPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    SubQuantityStepper(
                        quantity = selection.quantity,
                        onAdd = onIncrement,
                        onRemove = onDecrement,
                    )
                }
            }
            SubItemKind.TOGGLE -> {
                SubToggleRow(
                    label = item.label,
                    selected = selection.selected,
                    rowFocused = rowFocused,
                    onClick = onToggle,
                )
            }
            SubItemKind.CHOICE -> {
                Text(
                    text = item.label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = SansBody,
                    color = if (rowFocused) GoldLight else TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.choices.forEach { choice ->
                        val chosen = selection.selected && selection.choice == choice
                        ChoiceChip(
                            label = choice,
                            selected = chosen,
                            onClick = { onSelectChoice(choice) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SubToggleRow(
    label: String,
    selected: Boolean,
    rowFocused: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = SansBody,
            color = if (rowFocused || selected) GoldLight else TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val checkShape = RoundedCornerShape(8.dp)
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    if (selected) GoldLuxury.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f),
                    checkShape,
                )
                .border(
                    if (rowFocused || selected) 2.dp else 1.dp,
                    if (selected || rowFocused) GoldLuxury else Color.White.copy(alpha = 0.2f),
                    checkShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Text(text = "✓", fontSize = 14.sp, color = GoldLight, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onClick(); true
                } else false
            }
            .background(
                when {
                    selected -> GoldLuxury.copy(alpha = 0.28f)
                    focused -> GoldLuxury.copy(alpha = 0.14f)
                    else -> Color.White.copy(alpha = 0.06f)
                },
                shape,
            )
            .border(
                if (focused || selected) 2.dp else 1.dp,
                if (selected || focused) GoldLuxury else Color.White.copy(alpha = 0.14f),
                shape,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = SansBody,
            color = if (selected || focused) GoldLight else TextMuted,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SubQuantityStepper(
    quantity: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .background(GlassCardFill, shape)
            .border(1.dp, GoldGlassBorder, shape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SubQtyButton(label = "−", enabled = quantity > 0, onClick = onRemove)
        Text(
            text = quantity.toString(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SansBody,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp),
            maxLines = 1,
        )
        SubQtyButton(label = "+", enabled = true, onClick = onAdd)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SubQtyButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(34.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .onKeyEvent { event ->
                if (enabled && event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onClick(); true
                } else false
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
                if (focused) 2.dp else 1.dp,
                when {
                    focused -> GoldLuxury
                    enabled -> Color.White.copy(alpha = 0.14f)
                    else -> Color.White.copy(alpha = 0.06f)
                },
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) TextPrimary else TextMuted.copy(alpha = 0.4f),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SubDialogButton(
    text: String,
    highlighted: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val scale by animateFloatAsState(
        targetValue = if (focused && enabled) 1.03f else 1f,
        animationSpec = tween(150),
        label = "subDlgBtnScale",
    )
    val bgBrush = when {
        !highlighted -> Brush.verticalGradient(
            listOf(Color.White.copy(0.08f), Color.White.copy(0.04f)),
        )
        focused && enabled -> Brush.verticalGradient(listOf(GoldLight, GoldLuxury))
        enabled -> Brush.verticalGradient(
            listOf(GoldLuxury.copy(0.95f), GoldPrimary.copy(0.85f)),
        )
        else -> Brush.verticalGradient(
            listOf(Color.White.copy(0.08f), Color.White.copy(0.04f)),
        )
    }
    val borderColor = when {
        focused && highlighted && enabled -> GoldLight
        highlighted && enabled -> GoldLuxury.copy(0.7f)
        else -> Color.White.copy(0.15f)
    }
    val textColor = when {
        highlighted && enabled -> NavyDeep
        else -> TextMuted
    }

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(brush = bgBrush, shape = shape)
            .border(if (focused) 2.dp else 1.dp, borderColor, shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .onKeyEvent { event ->
                if (enabled && event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onClick(); true
                } else false
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SansBody,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ActiveRequestRow(request: ServiceRequest) {
    val status = request.status.trim().lowercase()
    val statusColor = when (status) {
        "in_progress" -> FocusCyan
        "completed" -> FocusTeal
        else -> Color(0xFFFBBF24)
    }
    val statusLabel = when (status) {
        "in_progress" -> stringResource(R.string.status_in_progress)
        "completed" -> stringResource(R.string.status_completed)
        else -> stringResource(R.string.status_pending)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavyDeep.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .border(1.dp, GoldPrimary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = request.serviceLabel,
            fontSize = 16.sp,
            fontFamily = SansBody,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = statusLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = statusColor,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VacantRoomDialog(onDismiss: () -> Unit) {
    val dismissFocus = remember { FocusRequester() }

    // In-composition overlay — Compose Dialog windows reclaim Home on physical TVs.
    DismissOnKioskBack(onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
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
                .border(2.dp, VacantRed.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text = "🚫", fontSize = 40.sp)
                Text(
                    text = stringResource(R.string.vacant_room_dialog_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SerifDisplay,
                    color = VacantRed,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.vacant_room_dialog_message),
                    fontSize = 14.sp,
                    fontFamily = SansBody,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                )
                var btnFocused by remember { mutableStateOf(false) }
                val btnScale by animateFloatAsState(
                    targetValue = if (btnFocused) 1.04f else 1f,
                    label = "vacantBtnScale",
                )
                val btnShape = RoundedCornerShape(12.dp)
                val btnBrush = if (btnFocused) {
                    Brush.verticalGradient(listOf(GoldLight, GoldLuxury))
                } else {
                    Brush.verticalGradient(
                        listOf(GoldLuxury.copy(0.95f), GoldPrimary.copy(0.85f)),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .graphicsLayer { scaleX = btnScale; scaleY = btnScale }
                        .background(brush = btnBrush, shape = btnShape)
                        .border(
                            if (btnFocused) 2.dp else 1.dp,
                            if (btnFocused) GoldLight else GoldLuxury.copy(0.7f),
                            btnShape,
                        )
                        .focusRequester(dismissFocus)
                        .onFocusChanged { btnFocused = it.isFocused }
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                (event.key == Key.Enter || event.key == Key.DirectionCenter)
                            ) {
                                onDismiss(); true
                            } else false
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.vacant_room_dialog_cta),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SansBody,
                        color = NavyDeep,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LaunchedEffect(Unit) {
                    delay(50)
                    runCatching { dismissFocus.requestFocus() }
                }
            }
        }
    }
}

@Composable
private fun DismissOnKioskBack(onDismiss: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onDismiss)
    DisposableEffect(onDismiss) {
        val main = context as? MainActivity
        val previous = main?.nestedAdminBackHandler
        main?.nestedAdminBackHandler = {
            onDismiss()
            true
        }
        onDispose {
            main?.nestedAdminBackHandler = previous
        }
    }
}
