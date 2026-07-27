package com.example.ikhsanahoteltv.ui.services

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LuxuryScreenHeader(
                    title = stringResource(R.string.services_title),
                    subtitle = stringResource(R.string.services_subtitle),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(viewModel.serviceOptions.withIndex().toList(), key = { it.value.serviceType }) { (index, option) ->
                ServiceButton(
                    option = option,
                    enabled = !uiState.isSubmitting && uiState.roomOccupied,
                    roomOccupied = uiState.roomOccupied,
                    modifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier,
                    onClick = { viewModel.requestService(option) },
                )
            }

            if (uiState.activeRequests.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.active_requests),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = SerifDisplay,
                        color = GoldLight,
                    )
                }
                items(uiState.activeRequests, key = { it.id }) { request ->
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
private fun ServiceButton(
    option: ServiceOption,
    enabled: Boolean,
    roomOccupied: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.03f else 1f,
        label = "serviceScale",
    )
    val shape = RoundedCornerShape(14.dp)
    val borderColor = when {
        !roomOccupied -> VacantRed.copy(alpha = 0.4f)
        focused -> GoldPrimary
        else -> Color.White.copy(alpha = 0.12f)
    }
    val bgColor = when {
        !roomOccupied -> VacantRed.copy(alpha = 0.06f)
        focused -> GoldPrimary.copy(alpha = 0.18f)
        else -> NavyDeep.copy(alpha = 0.5f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled = roomOccupied && !enabled.not())
            .background(bgColor, shape)
            .border(width = if (focused) 3.dp else 1.dp, color = borderColor, shape = shape)
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
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(text = if (roomOccupied) option.icon else "🔒", fontSize = 28.sp)
        Column {
            Text(
                text = option.label,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = SansBody,
                color = if (roomOccupied) TextPrimary else TextMuted.copy(alpha = 0.55f),
            )
            Text(
                text = if (roomOccupied) {
                    stringResource(R.string.service_tap_to_request)
                } else {
                    stringResource(R.string.vacant_room_cta_hint)
                },
                fontSize = 13.sp,
                fontFamily = SansBody,
                color = if (roomOccupied) TextMuted else VacantRed.copy(alpha = 0.8f),
            )
        }
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
                // Dismiss button
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
