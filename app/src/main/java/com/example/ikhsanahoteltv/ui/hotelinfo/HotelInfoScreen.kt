package com.example.ikhsanahoteltv.ui.hotelinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.ikhsanahoteltv.R
import com.example.ikhsanahoteltv.ui.HotelViewModelFactory
import com.example.ikhsanahoteltv.ui.components.LuxuryGlassPanel
import com.example.ikhsanahoteltv.ui.components.LuxuryScreenBackground
import com.example.ikhsanahoteltv.ui.components.LuxuryScreenHeader
import com.example.ikhsanahoteltv.ui.components.luxuryBackHandler
import com.example.ikhsanahoteltv.ui.home.HomeViewModel
import com.example.ikhsanahoteltv.ui.theme.GoldLight
import com.example.ikhsanahoteltv.ui.theme.GoldPrimary
import com.example.ikhsanahoteltv.ui.theme.NavyDeep
import com.example.ikhsanahoteltv.ui.theme.SansBody
import com.example.ikhsanahoteltv.ui.theme.SerifDisplay
import com.example.ikhsanahoteltv.ui.theme.TextMuted
import com.example.ikhsanahoteltv.ui.theme.TextPrimary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HotelInfoScreen(
    viewModelFactory: HotelViewModelFactory,
    onBack: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val profile = uiState.guestProfile

    Box(
        modifier = Modifier
            .fillMaxSize()
            .luxuryBackHandler(onBack),
    ) {
        LuxuryScreenBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LuxuryScreenHeader(title = stringResource(R.string.hotel_info_title))

            Spacer(modifier = Modifier.height(4.dp))

            InfoCard(title = "Hotel", value = profile.hotelName.ifBlank { "Ikhsana Hotel" })
            InfoCard(title = "Guest", value = profile.guestName)
            InfoCard(title = "Room", value = profile.roomNumber)

            if (profile.checkInDate.isNotBlank()) {
                InfoCard(title = "Check-In", value = profile.checkInDate)
            }
            if (profile.checkOutDate.isNotBlank()) {
                InfoCard(title = "Check-Out", value = profile.checkOutDate)
            }

            Spacer(modifier = Modifier.height(8.dp))

            LuxuryGlassPanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = profile.hotelInfo.ifBlank {
                        "Welcome to our hotel!\n\n" +
                            "• 24/7 Room Service\n" +
                            "• Free Wi-Fi\n" +
                            "• Swimming Pool (6 AM – 10 PM)\n" +
                            "• Spa & Wellness Center\n" +
                            "• Concierge Desk\n\n" +
                            "Press Back on your remote to return home."
                    },
                    fontSize = 16.sp,
                    fontFamily = SansBody,
                    color = TextPrimary.copy(alpha = 0.85f),
                    lineHeight = 26.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun InfoCard(title: String, value: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavyDeep.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .border(1.dp, GoldPrimary.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontFamily = SansBody,
                color = GoldLight,
            )
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = SerifDisplay,
                color = TextPrimary,
            )
        }
    }
}
