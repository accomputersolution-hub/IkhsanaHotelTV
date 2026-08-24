package `in`.pcncloud.hotel.ui.agenda

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.data.model.AgendaItem
import `in`.pcncloud.hotel.ui.HotelViewModelFactory
import `in`.pcncloud.hotel.ui.components.BaseScreen
import `in`.pcncloud.hotel.ui.components.luxuryGoldFocusChrome
import `in`.pcncloud.hotel.ui.theme.CorpGold
import `in`.pcncloud.hotel.ui.theme.CorpGoldBright
import `in`.pcncloud.hotel.ui.theme.TextMuted
import `in`.pcncloud.hotel.ui.theme.TextPrimary

/**
 * Corporate multi-day Agenda — date chips + live sessions from Daily_Agenda.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AgendaScreen(
    viewModelFactory: HotelViewModelFactory,
    onBack: () -> Unit,
    onOpenAdmin: () -> Unit = {},
) {
    val viewModel: AgendaViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val dateChipFocus = remember { FocusRequester() }
    val firstItemFocus = remember { FocusRequester() }

    LaunchedEffect(uiState.isLoading, uiState.availableDates) {
        if (!uiState.isLoading && uiState.availableDates.isNotEmpty()) {
            runCatching { dateChipFocus.requestFocus() }
        }
    }

    val subtitle = when {
        uiState.isLoading -> stringResource(R.string.agenda_loading_subtitle)
        uiState.allItems.isEmpty() -> stringResource(R.string.agenda_subtitle)
        uiState.selectedDate.isNotBlank() -> {
            val label = AgendaViewModel.formatDateChipLabel(uiState.selectedDate)
            stringResource(R.string.agenda_subtitle_day, label, uiState.items.size)
        }
        else -> stringResource(R.string.agenda_subtitle_live, uiState.items.size)
    }

    BaseScreen(
        viewModelFactory = viewModelFactory,
        onBack = onBack,
        onOpenAdmin = onOpenAdmin,
        title = stringResource(R.string.agenda_title),
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
                            text = stringResource(R.string.agenda_loading),
                            fontSize = 18.sp,
                            fontFamily = FontFamily.SansSerif,
                            color = TextMuted,
                        )
                    }
                }
            }

            uiState.allItems.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.agenda_empty),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    if (uiState.availableDates.isNotEmpty()) {
                        AgendaDateChipRow(
                            dates = uiState.availableDates,
                            selectedDate = uiState.selectedDate,
                            onSelectDate = viewModel::selectDate,
                            firstChipFocus = dateChipFocus,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 12.dp),
                        )
                    }

                    if (uiState.items.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.agenda_empty_day),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif,
                                color = TextMuted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                        ) {
                            itemsIndexed(
                                items = uiState.items,
                                key = { _, item -> item.id.ifBlank { "${item.time}_${item.title}" } },
                            ) { index, item ->
                                TodayAgendaSessionCard(
                                    item = item,
                                    modifier = if (index == 0) {
                                        Modifier.focusRequester(firstItemFocus)
                                    } else {
                                        Modifier
                                    },
                                )
                            }
                        }
                    }

                    if (uiState.contactsFooter.isNotBlank()) {
                        Text(
                            text = uiState.contactsFooter,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif,
                            color = TextMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AgendaDateChipRow(
    dates: List<String>,
    selectedDate: String,
    onSelectDate: (String) -> Unit,
    firstChipFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(dates, key = { _, d -> d.ifBlank { "_undated" } }) { index, dateKey ->
            AgendaDateChip(
                label = AgendaViewModel.formatDateChipLabel(dateKey),
                selected = dateKey == selectedDate,
                onClick = { onSelectDate(dateKey) },
                modifier = if (index == 0) {
                    Modifier.focusRequester(firstChipFocus)
                } else {
                    Modifier
                },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AgendaDateChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(140),
        label = "agendaDateChipScale",
    )
    val shape = RoundedCornerShape(28.dp)
    val bg = when {
        selected && focused -> CorpGoldBright.copy(alpha = 0.35f)
        selected -> CorpGold.copy(alpha = 0.28f)
        focused -> Color.White.copy(alpha = 0.08f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyUp &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .luxuryGoldFocusChrome(focused = focused, shape = shape)
            .background(bg, shape)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 18.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            color = when {
                focused -> Color.White
                selected -> CorpGoldBright
                else -> TextMuted
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Single schedule row for the TV timeline. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TodayAgendaSessionCard(
    item: AgendaItem,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = tween(150),
        label = "agendaRowScale",
    )
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0.12f)
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .luxuryGoldFocusChrome(focused = focused, shape = shape)
            .padding(horizontal = 28.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text(
            text = item.time.ifBlank { "—" },
            modifier = Modifier.widthIn(min = 220.dp, max = 280.dp),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            color = if (focused) CorpGoldBright else CorpGold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 28.sp,
        )

        Box(
            modifier = Modifier
                .width(3.dp)
                .height(48.dp)
                .background(
                    if (focused) CorpGoldBright else CorpGold.copy(alpha = 0.45f),
                    RoundedCornerShape(2.dp),
                ),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = item.title.ifBlank { "Untitled" },
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                color = if (focused) Color.White else TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 32.sp,
            )
            if (item.location.isNotBlank()) {
                Text(
                    text = item.location,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.SansSerif,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
