package `in`.pcncloud.hotel.ui.hotelinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import `in`.pcncloud.hotel.BuildConfig
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.data.RoomIds
import `in`.pcncloud.hotel.ui.HotelViewModelFactory
import `in`.pcncloud.hotel.ui.components.BaseScreen
import `in`.pcncloud.hotel.ui.components.LuxuryGlassPanel
import `in`.pcncloud.hotel.ui.home.HomeViewModel
import `in`.pcncloud.hotel.ui.theme.CorpCardBg
import `in`.pcncloud.hotel.ui.theme.CorpGold
import `in`.pcncloud.hotel.ui.theme.CorpGoldBorderIdle
import `in`.pcncloud.hotel.ui.theme.SansBody
import `in`.pcncloud.hotel.ui.theme.SerifDisplay
import `in`.pcncloud.hotel.ui.theme.TextPrimary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HotelInfoScreen(
    viewModelFactory: HotelViewModelFactory,
    onBack: () -> Unit,
    onOpenAdmin: () -> Unit = {},
) {
    val viewModel: HomeViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val profile = uiState.guestProfile

    BaseScreen(
        viewModelFactory = viewModelFactory,
        onBack = onBack,
        onOpenAdmin = onOpenAdmin,
        title = stringResource(R.string.hotel_info_title),
        showChromeHeader = BuildConfig.IS_CORPORATE,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InfoCard(title = "Hotel", value = profile.hotelName.ifBlank { "Hotel" })
            InfoCard(title = "Guest", value = profile.guestName)
            InfoCard(
                title = if (RoomIds.isNumericId(profile.roomNumber)) "Room" else "Location",
                value = profile.roomNumber,
            )

            if (profile.checkInDate.isNotBlank()) {
                InfoCard(title = "Check-In", value = profile.checkInDate)
            }
            if (profile.checkOutDate.isNotBlank()) {
                InfoCard(title = "Check-Out", value = profile.checkOutDate)
            }

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
            .background(CorpCardBg, RoundedCornerShape(12.dp))
            .border(1.dp, CorpGoldBorderIdle, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontFamily = SansBody,
                color = CorpGold,
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
