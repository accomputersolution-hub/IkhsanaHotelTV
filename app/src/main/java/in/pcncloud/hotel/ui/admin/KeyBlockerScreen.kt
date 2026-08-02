package `in`.pcncloud.hotel.ui.admin

import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import `in`.pcncloud.hotel.MainActivity
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.kiosk.BlockedKeysManager
import `in`.pcncloud.hotel.ui.theme.GoldLight
import `in`.pcncloud.hotel.ui.theme.GoldPrimary
import `in`.pcncloud.hotel.ui.theme.NavyDeep
import `in`.pcncloud.hotel.ui.theme.NavyMain
import `in`.pcncloud.hotel.ui.theme.SansBody
import `in`.pcncloud.hotel.ui.theme.SerifDisplay
import `in`.pcncloud.hotel.ui.theme.TextMuted
import `in`.pcncloud.hotel.ui.theme.TextPrimary

/**
 * Admin Key Blocker — list / remove blocked remote keyCodes and learn new ones
 * via [MainActivity] [android.app.Activity.dispatchKeyEvent] while listening.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun KeyBlockerScreen(
    onBack: () -> Unit,
    onRemoteActivity: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val appContext = context.applicationContext

    var blockedKeys by remember {
        mutableStateOf(BlockedKeysManager.getBlockedKeys(appContext).sorted())
    }
    var listening by remember { mutableStateOf(false) }
    var pendingKeyCode by remember { mutableIntStateOf(-1) }
    val backFocus = remember { FocusRequester() }

    fun refreshList() {
        blockedKeys = BlockedKeysManager.getBlockedKeys(appContext).sorted()
    }

    LaunchedEffect(Unit) {
        runCatching { backFocus.requestFocus() }
    }

    // Wire MainActivity.dispatchKeyEvent capture + Accessibility learning passthrough.
    DisposableEffect(listening) {
        if (listening) {
            BlockedKeysManager.setLearningMode(appContext, true)
            activity?.keyLearnListener = { keyCode ->
                onRemoteActivity()
                if (isNavOrSystemKey(keyCode)) {
                    false
                } else {
                    pendingKeyCode = keyCode
                    listening = false
                    true
                }
            }
        } else {
            BlockedKeysManager.setLearningMode(appContext, false)
            activity?.keyLearnListener = null
        }
        onDispose {
            BlockedKeysManager.setLearningMode(appContext, false)
            activity?.keyLearnListener = null
        }
    }

    Box(
        modifier = Modifier
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
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.admin_key_blocker_title),
                color = TextPrimary,
                fontFamily = SerifDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when {
                    listening -> stringResource(R.string.admin_key_blocker_listening)
                    pendingKeyCode >= 0 -> stringResource(
                        R.string.admin_key_blocker_confirm,
                        pendingKeyCode,
                    )
                    else -> stringResource(R.string.admin_key_blocker_subtitle)
                },
                color = if (listening) GoldLight else TextMuted,
                fontFamily = SansBody,
                fontSize = 14.sp,
            )

            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (blockedKeys.isEmpty()) {
                    Text(
                        text = stringResource(R.string.admin_key_blocker_empty),
                        color = TextMuted,
                        fontFamily = SansBody,
                        fontSize = 14.sp,
                    )
                } else {
                    blockedKeys.forEach { code ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.admin_key_blocker_item, code),
                                color = TextPrimary,
                                fontFamily = SansBody,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                            KeyBlockerChip(
                                label = stringResource(R.string.admin_key_blocker_remove),
                                onClick = {
                                    onRemoteActivity()
                                    BlockedKeysManager.removeBlockedKey(appContext, code)
                                    refreshList()
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.admin_key_blocker_removed,
                                            code,
                                        ),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            when {
                pendingKeyCode >= 0 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KeyBlockerChip(
                            label = stringResource(R.string.admin_key_blocker_save),
                            wide = true,
                            onClick = {
                                onRemoteActivity()
                                val code = pendingKeyCode
                                BlockedKeysManager.addBlockedKey(appContext, code)
                                pendingKeyCode = -1
                                refreshList()
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.admin_key_blocker_saved, code),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                        KeyBlockerChip(
                            label = stringResource(R.string.admin_cancel),
                            wide = true,
                            onClick = {
                                onRemoteActivity()
                                pendingKeyCode = -1
                            },
                        )
                    }
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KeyBlockerChip(
                            label = if (listening) {
                                stringResource(R.string.admin_key_blocker_cancel_listen)
                            } else {
                                stringResource(R.string.admin_key_blocker_learn)
                            },
                            wide = true,
                            onClick = {
                                onRemoteActivity()
                                if (listening) {
                                    listening = false
                                } else {
                                    pendingKeyCode = -1
                                    listening = true
                                }
                            },
                        )
                        KeyBlockerChip(
                            label = stringResource(R.string.admin_key_blocker_back),
                            wide = true,
                            modifier = Modifier.focusRequester(backFocus),
                            onClick = {
                                onRemoteActivity()
                                listening = false
                                pendingKeyCode = -1
                                onBack()
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
private fun KeyBlockerChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .then(if (wide) Modifier.widthIn(min = 140.dp) else Modifier.widthIn(min = 96.dp))
            .height(48.dp)
            .background(
                if (focused) GoldPrimary.copy(alpha = 0.22f)
                else NavyDeep.copy(alpha = 0.85f),
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
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (focused) GoldLight else TextPrimary,
            fontFamily = SansBody,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Keys that must keep working for D-pad navigation while learning. */
private fun isNavOrSystemKey(keyCode: Int): Boolean {
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_APP_SWITCH,
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE,
        -> true
        else -> false
    }
}
