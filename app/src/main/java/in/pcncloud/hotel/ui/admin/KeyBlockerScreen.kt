package `in`.pcncloud.hotel.ui.admin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.core.content.ContextCompat
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val TAG = "KeyBlockerUI"

/**
 * Admin Key Blocker — nested under Staff Settings.
 *
 * OTT / dedicated remote keys are learned **only** via
 * [HomeKeyInterceptorService] Learn Mode shield + [BlockedKeysManager.ACTION_KEY_LEARNED].
 * Do not use Activity.dispatchKeyEvent for learning — the OS intercepts those keys first.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun KeyBlockerScreen(
    onBack: () -> Unit,
    onRemoteActivity: () -> Unit = {},
) {
    val context = LocalContext.current
    val appContext = context.applicationContext

    // Default empty while loading — never null (avoids NPE during first frame).
    var blockedKeys by remember { mutableStateOf<List<Int>>(emptyList()) }
    var keysLoaded by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var pendingKeyCode by remember { mutableIntStateOf(-1) }
    val learnFocus = remember { FocusRequester() }

    fun refreshList() {
        try {
            blockedKeys = BlockedKeysManager.getBlockedKeys(appContext).sorted()
        } catch (e: Exception) {
            Log.e(TAG, "refreshList failed", e)
            blockedKeys = emptyList()
        }
    }

    fun stopLearnMode() {
        listening = false
        BlockedKeysManager.setLearnMode(appContext, false)
    }

    fun returnToStaffSettings() {
        Log.i(TAG, "Back → Staff Settings (not Guest Home)")
        stopLearnMode()
        pendingKeyCode = -1
        onRemoteActivity()
        onBack()
    }

    // Physical / system Back → pop Key Blocker only (Staff Settings stays open).
    BackHandler(enabled = true) {
        returnToStaffSettings()
    }

    // Load prefs off the composition critical path.
    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            try {
                BlockedKeysManager.getBlockedKeys(appContext).sorted()
            } catch (e: Exception) {
                Log.e(TAG, "initial key load failed", e)
                emptyList()
            }
        }
        blockedKeys = loaded
        keysLoaded = true
    }

    // Request focus only after layout is attached (prevents TV focus crashes).
    LaunchedEffect(keysLoaded) {
        if (!keysLoaded) return@LaunchedEffect
        delay(50)
        runCatching { learnFocus.requestFocus() }
            .onFailure { Log.w(TAG, "learnFocus.requestFocus deferred/failed", it) }
    }

    DisposableEffect(Unit) {
        onDispose {
            BlockedKeysManager.setLearnMode(appContext, false)
        }
    }

    /**
     * Learn Mode is driven ONLY by Accessibility [HomeKeyInterceptorService] +
     * [BlockedKeysManager.ACTION_KEY_LEARNED] broadcast — never Activity.dispatchKeyEvent
     * (OEM OTT keys never reach the Activity).
     *
     * Register the receiver FIRST, then enable the shield so no key is missed.
     */
    DisposableEffect(listening) {
        if (!listening) {
            BlockedKeysManager.setLearnMode(appContext, false)
            return@DisposableEffect onDispose { }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != BlockedKeysManager.ACTION_KEY_LEARNED) return
                val code = intent.getIntExtra(BlockedKeysManager.EXTRA_KEY_CODE, -1)
                if (code < 0) return
                Log.i(TAG, "ACTION_KEY_LEARNED received keyCode=$code")
                onRemoteActivity()
                // Turn shield off immediately, then show Save/Cancel.
                BlockedKeysManager.setLearnMode(appContext, false)
                try {
                    context.unregisterReceiver(this)
                } catch (_: Exception) {
                }
                pendingKeyCode = code
                listening = false
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BlockedKeysManager.ACTION_KEY_LEARNED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Enable global Accessibility shield only after receiver is live.
        BlockedKeysManager.setLearnMode(appContext, true)
        Log.i(TAG, "Learn Mode shield ON — waiting for Accessibility broadcast")

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
            BlockedKeysManager.setLearnMode(appContext, false)
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

            Spacer(modifier = Modifier.height(16.dp))

            // —— Learn New Key ALWAYS above the key list (D-pad focusable) ——
            if (pendingKeyCode >= 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KeyBlockerChip(
                        label = stringResource(R.string.admin_key_blocker_save),
                        wide = true,
                        modifier = Modifier.focusRequester(learnFocus),
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
            } else {
                KeyBlockerChip(
                    label = if (listening) {
                        stringResource(R.string.admin_key_blocker_cancel_listen)
                    } else {
                        stringResource(R.string.admin_key_blocker_learn)
                    },
                    wide = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(learnFocus),
                    onClick = {
                        onRemoteActivity()
                        if (listening) {
                            stopLearnMode()
                        } else {
                            pendingKeyCode = -1
                            // Only flip UI state — DisposableEffect registers receiver
                            // then calls setLearnMode(true) (Accessibility shield).
                            listening = true
                            Log.i(TAG, "Learn New Key pressed — enabling Accessibility shield")
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // —— Scrollable blocked key list ——
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!keysLoaded) {
                    Text(
                        text = "Loading…",
                        color = TextMuted,
                        fontFamily = SansBody,
                        fontSize = 14.sp,
                    )
                } else if (blockedKeys.isEmpty()) {
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

            Spacer(modifier = Modifier.height(16.dp))

            KeyBlockerChip(
                label = stringResource(R.string.admin_key_blocker_back),
                wide = true,
                onClick = { returnToStaffSettings() },
            )
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
            .then(if (wide) Modifier.widthIn(min = 160.dp) else Modifier.widthIn(min = 96.dp))
            .height(52.dp)
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
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
