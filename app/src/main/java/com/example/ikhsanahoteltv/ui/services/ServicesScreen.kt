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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.ikhsanahoteltv.ui.theme.GoldPrimary
import com.example.ikhsanahoteltv.ui.theme.NavyDeep
import com.example.ikhsanahoteltv.ui.theme.SansBody
import com.example.ikhsanahoteltv.ui.theme.SerifDisplay
import com.example.ikhsanahoteltv.ui.theme.TextMuted
import com.example.ikhsanahoteltv.ui.theme.TextPrimary

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
                    enabled = !uiState.isSubmitting,
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.03f else 1f,
        label = "serviceScale",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled = enabled)
            .background(
                if (focused) GoldPrimary.copy(alpha = 0.18f) else NavyDeep.copy(alpha = 0.5f),
                RoundedCornerShape(14.dp),
            )
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) GoldPrimary else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
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
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(text = option.icon, fontSize = 28.sp)
        Column {
            Text(
                text = option.label,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = SansBody,
                color = TextPrimary,
            )
            Text(
                text = stringResource(R.string.service_tap_to_request),
                fontSize = 13.sp,
                fontFamily = SansBody,
                color = TextMuted,
            )
        }
    }
}
