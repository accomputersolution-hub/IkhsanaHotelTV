package `in`.pcncloud.hotel.ui.alerts

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.data.model.HotelAlert
import `in`.pcncloud.hotel.ui.HotelViewModelFactory
import `in`.pcncloud.hotel.ui.components.LuxuryScreenBackground
import `in`.pcncloud.hotel.ui.components.LuxuryScreenHeader
import `in`.pcncloud.hotel.ui.components.luxuryBackHandler
import `in`.pcncloud.hotel.ui.home.HomeViewModel
import `in`.pcncloud.hotel.ui.theme.GoldPrimary
import `in`.pcncloud.hotel.ui.theme.NavyDeep
import `in`.pcncloud.hotel.ui.theme.SansBody
import `in`.pcncloud.hotel.ui.theme.SerifDisplay
import `in`.pcncloud.hotel.ui.theme.TextMuted
import `in`.pcncloud.hotel.ui.theme.TextPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AlertsScreen(
    viewModelFactory: HotelViewModelFactory,
    onBack: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .luxuryBackHandler(onBack),
    ) {
        LuxuryScreenBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
        ) {
            LuxuryScreenHeader(title = stringResource(R.string.alerts_title))

            Spacer(modifier = Modifier.height(20.dp))

            if (uiState.alerts.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_alerts),
                    fontSize = 18.sp,
                    fontFamily = SansBody,
                    color = TextMuted,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.alerts, key = { it.id }) { alert ->
                        AlertRow(alert = alert)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AlertRow(alert: HotelAlert) {
    var focused by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .background(
                if (alert.read) NavyDeep.copy(alpha = 0.45f) else GoldPrimary.copy(alpha = 0.12f),
                RoundedCornerShape(12.dp),
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) GoldPrimary else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(20.dp),
    ) {
        Column {
            RowBetween(
                title = alert.title,
                timestamp = if (alert.timestamp > 0) dateFormat.format(Date(alert.timestamp)) else "",
                isUnread = !alert.read,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = alert.message,
                fontSize = 15.sp,
                fontFamily = SansBody,
                color = TextPrimary.copy(alpha = 0.85f),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RowBetween(title: String, timestamp: String, isUnread: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (isUnread) "● $title" else title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = SerifDisplay,
            color = if (isUnread) GoldPrimary else TextPrimary,
        )
        if (timestamp.isNotBlank()) {
            Text(
                text = timestamp,
                fontSize = 13.sp,
                fontFamily = SansBody,
                color = TextMuted,
            )
        }
    }
}
