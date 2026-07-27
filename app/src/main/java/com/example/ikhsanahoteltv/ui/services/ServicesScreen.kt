package com.example.ikhsanahoteltv.ui.services

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.ikhsanahoteltv.R
import com.example.ikhsanahoteltv.data.model.ServiceRequest
import com.example.ikhsanahoteltv.ui.HotelViewModelFactory
import com.example.ikhsanahoteltv.ui.components.LuxuryScreenBackground
import com.example.ikhsanahoteltv.ui.components.LuxuryScreenHeader
import com.example.ikhsanahoteltv.ui.components.ServiceToast
import com.example.ikhsanahoteltv.ui.components.luxuryBackHandler
import com.example.ikhsanahoteltv.ui.theme.FocusCyan
import com.example.ikhsanahoteltv.ui.theme.FocusTeal
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

private val VacantRed = Color(0xFFEF4444)
private val GlassCardFill = Color(0xCC0B1325)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ServicesScreen(
    viewModelFactory: HotelViewModelFactory,
    onBack: () -> Unit,
) {
    val viewModel: ServicesViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val firstItemFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstItemFocus.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .luxuryBackHandler(onBack),
    ) {
        LuxuryScreenBackground(modifier = Modifier.fillMaxSize())

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    LuxuryScreenHeader(
                        title = stringResource(R.string.services_title),
                        subtitle = stringResource(R.string.services_subtitle),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            itemsIndexed(
                items = viewModel.serviceOptions,
                key = { _, option -> option.serviceType },
            ) { index, option ->
                ServiceCard(
                    option = option,
                    enabled = !uiState.isSubmitting && uiState.roomOccupied,
                    roomOccupied = uiState.roomOccupied,
                    modifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier,
                    onClick = { viewModel.requestService(option) },
                )
            }

            if (uiState.activeRequests.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
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
                    items = uiState.activeRequests,
                    key = { _, request -> request.id },
                    span = { _, _ -> GridItemSpan(maxLineSpan) },
                ) { _, request ->
                    ActiveRequestRow(request = request)
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
            .focusable(enabled = roomOccupied)
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ActiveRequestRow(request: ServiceRequest) {
    val statusColor = when (request.status) {
        "in_progress" -> FocusCyan
        "completed" -> FocusTeal
        else -> Color(0xFFFBBF24)
    }
    val statusLabel = when (request.status) {
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
    LaunchedEffect(Unit) { dismissFocus.requestFocus() }

    Dialog(onDismissRequest = onDismiss) {
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
            }
        }
    }
}
