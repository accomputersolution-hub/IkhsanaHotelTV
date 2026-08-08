package `in`.pcncloud.hotel.ui.admin

import android.app.Activity
import android.util.Log
import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import `in`.pcncloud.hotel.BuildConfig
import `in`.pcncloud.hotel.MainActivity
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.config.HotelConfig
import `in`.pcncloud.hotel.kiosk.BlockedKeysManager
import `in`.pcncloud.hotel.kiosk.HotelSessionManager
import `in`.pcncloud.hotel.kiosk.KioskPolicy
import `in`.pcncloud.hotel.kiosk.KioskRemoteConfig
import `in`.pcncloud.hotel.ui.components.LuxuryScreenBackground
import `in`.pcncloud.hotel.ui.theme.GoldLight
import `in`.pcncloud.hotel.ui.theme.GoldPrimary
import `in`.pcncloud.hotel.ui.theme.NavyDeep
import `in`.pcncloud.hotel.ui.theme.NavyMain
import `in`.pcncloud.hotel.ui.theme.NavySurface
import `in`.pcncloud.hotel.ui.theme.SansBody
import `in`.pcncloud.hotel.ui.theme.SerifDisplay
import `in`.pcncloud.hotel.ui.theme.TextMuted
import `in`.pcncloud.hotel.ui.theme.TextPrimary
import kotlinx.coroutines.delay

private const val TAG = "AdminSettings"
private const val ADMIN_IDLE_TIMEOUT_MS = 3 * 60 * 1000L
private const val PIN_LENGTH = 4

/**
 * Staff Admin Mode: Master PIN gate + settings panel.
 * Auto-dismisses after [ADMIN_IDLE_TIMEOUT_MS] with no remote key activity,
 * clears [AdminSession], and returns to the guest home screen via [onExitToHome].
 *
 * [sessionEpoch] must change on every open so an Activity-scoped ViewModel cannot
 * reuse a previous PIN / authenticated flag across Dining / Agenda / Emergency.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AdminSettingsScreen(
    onExitToHome: () -> Unit,
    sessionEpoch: Int = 0,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val hotelConfig = remember { HotelConfig(context.applicationContext) }
    val authVm: AdminAuthViewModel = viewModel(key = "staff-admin-$sessionEpoch")

    var kioskEnabled by remember {
        mutableStateOf(KioskPolicy.isKioskModeEnabled(context))
    }
    var lastInteractionAt by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val pinDigits = authVm.pinDigits
    val pinError = authVm.pinError
    val authenticated = authVm.isAuthenticated
    val showKeyBlocker = authVm.showKeyBlocker

    DisposableEffect(sessionEpoch) {
        // Always start locked when Staff Settings is shown.
        authVm.resetSession()
        onDispose {
            BlockedKeysManager.setLearnMode(context.applicationContext, false)
            (activity as? MainActivity)?.nestedAdminBackHandler = null
            authVm.resetSession()
        }
    }

    fun exitAdmin(reason: String) {
        Log.i(TAG, "Exiting Admin Mode — $reason")
        authVm.resetSession()
        BlockedKeysManager.setLearnMode(context.applicationContext, false)
        (activity as? MainActivity)?.nestedAdminBackHandler = null
        onExitToHome()
    }

    LaunchedEffect(lastInteractionAt) {
        delay(ADMIN_IDLE_TIMEOUT_MS)
        exitAdmin("inactivity ${ADMIN_IDLE_TIMEOUT_MS}ms")
    }

    fun onRemoteActivity() {
        lastInteractionAt = System.currentTimeMillis()
    }

    // Physical Back on PIN / settings → full session wipe + Guest Home.
    // Key Blocker keeps its own BackHandler (pop to settings only).
    BackHandler(enabled = !showKeyBlocker) {
        exitAdmin("back")
    }

    // Key Blocker Back → Staff Settings only (never Guest Home / kiosk reclaim).
    DisposableEffect(showKeyBlocker, authenticated) {
        val main = activity as? MainActivity
        if (showKeyBlocker && authenticated) {
            main?.nestedAdminBackHandler = {
                onRemoteActivity()
                BlockedKeysManager.setLearnMode(context.applicationContext, false)
                authVm.closeKeyBlocker()
                true
            }
        } else {
            main?.nestedAdminBackHandler = null
        }
        onDispose {
            main?.nestedAdminBackHandler = null
        }
    }

    fun submitPin() {
        onRemoteActivity()
        if (authVm.pinDigits == BuildConfig.DEFAULT_MASTER_PIN) {
            authVm.markPinAccepted(authVm.pinDigits)
            Log.i(TAG, "Master PIN accepted — Admin Mode unlocked")
        } else {
            authVm.markPinIncorrect()
            Log.w(TAG, "Master PIN rejected")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    onRemoteActivity()
                }
                false
            },
    ) {
        LuxuryScreenBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 36.dp),
        ) {
            Text(
                text = stringResource(R.string.admin_title),
                color = TextPrimary,
                fontFamily = SerifDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                letterSpacing = 1.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (authenticated) {
                    stringResource(R.string.admin_subtitle_settings)
                } else {
                    stringResource(R.string.admin_subtitle_pin)
                },
                color = TextMuted,
                fontFamily = SansBody,
                fontSize = 15.sp,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Box(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                NavyDeep.copy(alpha = 0.94f),
                                NavyMain.copy(alpha = 0.96f),
                            ),
                        ),
                        shape = RoundedCornerShape(20.dp),
                    )
                    .border(1.dp, GoldPrimary.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 36.dp, vertical = 32.dp),
            ) {
                if (!authenticated) {
                    PinEntryPanel(
                        pinDigits = pinDigits,
                        pinError = pinError,
                        onDigit = { digit ->
                            onRemoteActivity()
                            if (authVm.pinDigits.length < PIN_LENGTH) {
                                authVm.appendPinDigit(digit, PIN_LENGTH)
                                if (authVm.pinDigits.length == PIN_LENGTH) {
                                    submitPin()
                                }
                            }
                        },
                        onBackspace = {
                            onRemoteActivity()
                            authVm.backspacePin()
                        },
                        onCancel = { exitAdmin("cancel_pin") },
                    )
                } else if (showKeyBlocker) {
                    KeyBlockerScreen(
                        onBack = {
                            onRemoteActivity()
                            authVm.closeKeyBlocker()
                        },
                        onRemoteActivity = { onRemoteActivity() },
                    )
                } else {
                    SettingsPanel(
                        hotelId = hotelConfig.getHotelId().orEmpty(),
                        roomNumber = hotelConfig.roomNumber,
                        kioskEnabled = kioskEnabled,
                        adminOverride = KioskPolicy.hasAdminOverride(context),
                        onToggleKiosk = {
                            onRemoteActivity()
                            val next = !kioskEnabled
                            val host = activity
                            if (!next && host != null) {
                                // Instant OFF: stopLockTask + clear interceptors/OTT/Watchdog.
                                KioskPolicy.disableKioskMode(
                                    activity = host,
                                    source = KioskPolicy.KioskSource.LOCAL_ADMIN,
                                    persistFlag = true,
                                )
                            } else {
                                KioskPolicy.setKioskModeEnabled(
                                    context = context,
                                    enabled = next,
                                    source = KioskPolicy.KioskSource.LOCAL_ADMIN,
                                )
                            }
                            // Sync MainActivity memory immediately (listener also fires).
                            (host as? MainActivity)
                                ?.applyKioskModeChangedLocally(next, "AdminSettings.toggle")
                            kioskEnabled = next
                            Toast.makeText(
                                context,
                                if (next) {
                                    context.getString(R.string.admin_kiosk_on)
                                } else {
                                    context.getString(R.string.admin_kiosk_off)
                                },
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        onFollowRemote = {
                            onRemoteActivity()
                            KioskRemoteConfig.followRemoteConfig(context) { enabled ->
                                kioskEnabled = enabled
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.admin_kiosk_synced_remote),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        onExitToAndroidTv = {
                            onRemoteActivity()
                            // Leave guest lock; never start GTPL intents (black screen) —
                            // moveTaskToBack lets default launcher gain focus naturally.
                            val host = activity
                            if (kioskEnabled) {
                                if (host != null) {
                                    KioskPolicy.disableKioskMode(
                                        activity = host,
                                        source = KioskPolicy.KioskSource.LOCAL_ADMIN,
                                        persistFlag = true,
                                    )
                                } else {
                                    KioskPolicy.setKioskModeEnabled(
                                        context = context,
                                        enabled = false,
                                        source = KioskPolicy.KioskSource.LOCAL_ADMIN,
                                    )
                                }
                                (host as? MainActivity)
                                    ?.applyKioskModeChangedLocally(false, "AdminSettings.exitTv")
                                kioskEnabled = false
                            }
                            authVm.resetSession()
                            if (host != null) {
                                KioskPolicy.launchSystemDefaultLauncher(host)
                            } else {
                                KioskPolicy.launchSystemDefaultLauncher(context)
                            }
                            Log.i(TAG, "Technician exit → disableKioskMode + moveTaskToBack")
                            exitAdmin("exit_android_tv")
                        },
                        onUnpair = {
                            onRemoteActivity()
                            Log.i(TAG, "Admin Unpair Room / Logout — wiping session → PairingActivity")
                            authVm.resetSession()
                            val host = activity
                            if (host != null) {
                                HotelSessionManager.performLogout(host, reason = "admin_pin_unpair")
                            } else {
                                hotelConfig.clearPairingSession()
                                KioskPolicy.clearTenantKioskCache(context.applicationContext)
                                HotelSessionManager.openPairingScreen(context)
                            }
                        },
                        onOpenKeyBlocker = {
                            onRemoteActivity()
                            authVm.openKeyBlocker()
                        },
                        onClose = { exitAdmin("close") },
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = stringResource(R.string.admin_idle_hint),
                color = TextMuted.copy(alpha = 0.75f),
                fontFamily = SansBody,
                fontSize = 13.sp,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PinEntryPanel(
    pinDigits: String,
    pinError: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onCancel: () -> Unit,
) {
    val firstKeyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { firstKeyFocus.requestFocus() }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.admin_enter_pin),
            color = GoldLight,
            fontFamily = SansBody,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            letterSpacing = 1.sp,
        )
        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(PIN_LENGTH) { index ->
                val filled = index < pinDigits.length
                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 56.dp)
                        .background(NavySurface, RoundedCornerShape(10.dp))
                        .border(
                            1.dp,
                            if (pinError) Color(0xFFEF4444).copy(alpha = 0.8f)
                            else GoldPrimary.copy(alpha = 0.4f),
                            RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (filled) "•" else "",
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (pinError) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.admin_pin_incorrect),
                color = Color(0xFFFCA5A5),
                fontFamily = SansBody,
                fontSize = 14.sp,
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        val rows = listOf(
            listOf('1', '2', '3'),
            listOf('4', '5', '6'),
            listOf('7', '8', '9'),
        )
        rows.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { colIndex, digit ->
                    AdminActionChip(
                        label = digit.toString(),
                        modifier = if (rowIndex == 0 && colIndex == 0) {
                            Modifier.focusRequester(firstKeyFocus)
                        } else {
                            Modifier
                        },
                        onClick = { onDigit(digit) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AdminActionChip(label = "⌫", onClick = onBackspace)
            AdminActionChip(label = "0", onClick = { onDigit('0') })
            AdminActionChip(
                label = stringResource(R.string.admin_cancel),
                wide = true,
                onClick = onCancel,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsPanel(
    hotelId: String,
    roomNumber: String,
    kioskEnabled: Boolean,
    adminOverride: Boolean,
    onToggleKiosk: () -> Unit,
    onFollowRemote: () -> Unit,
    onExitToAndroidTv: () -> Unit,
    onUnpair: () -> Unit,
    onOpenKeyBlocker: () -> Unit,
    onClose: () -> Unit,
) {
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { closeFocus.requestFocus() }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        InfoRow(
            label = stringResource(R.string.admin_hotel_id),
            value = hotelId.ifBlank { "—" },
        )
        Spacer(modifier = Modifier.height(12.dp))
        InfoRow(
            label = stringResource(R.string.admin_room),
            value = roomNumber,
        )
        Spacer(modifier = Modifier.height(12.dp))
        InfoRow(
            label = stringResource(R.string.admin_kiosk),
            value = if (kioskEnabled) {
                stringResource(R.string.admin_enabled)
            } else {
                stringResource(R.string.admin_disabled)
            },
        )
        if (adminOverride) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.admin_kiosk_override_hint),
                color = GoldLight,
                fontFamily = SansBody,
                fontSize = 12.sp,
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AdminActionChip(
                label = if (kioskEnabled) {
                    stringResource(R.string.admin_disable_kiosk)
                } else {
                    stringResource(R.string.admin_enable_kiosk)
                },
                wide = true,
                onClick = onToggleKiosk,
            )
            AdminActionChip(
                label = stringResource(R.string.admin_unpair),
                wide = true,
                onClick = onUnpair,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AdminActionChip(
                label = stringResource(R.string.admin_follow_remote_kiosk),
                wide = true,
                onClick = onFollowRemote,
            )
            AdminActionChip(
                label = stringResource(R.string.admin_exit_android_tv),
                wide = true,
                onClick = onExitToAndroidTv,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AdminActionChip(
                label = stringResource(R.string.admin_key_blocker_open),
                wide = true,
                onClick = onOpenKeyBlocker,
            )
            AdminActionChip(
                label = stringResource(R.string.admin_close),
                wide = true,
                modifier = Modifier.focusRequester(closeFocus),
                onClick = onClose,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = TextMuted,
            fontFamily = SansBody,
            fontSize = 14.sp,
        )
        Text(
            text = value,
            color = TextPrimary,
            fontFamily = SansBody,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            textAlign = TextAlign.End,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AdminActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .then(if (wide) Modifier.widthIn(min = 140.dp) else Modifier.size(64.dp))
            .height(52.dp)
            .background(
                if (focused) GoldPrimary.copy(alpha = 0.22f) else NavySurface,
                RoundedCornerShape(12.dp),
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) GoldPrimary else GoldPrimary.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
            )
            .onFocusChanged { focused = it.isFocused }
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
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (focused) GoldLight else TextPrimary,
            fontFamily = SansBody,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
