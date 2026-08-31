package `in`.pcncloud.hotel.kiosk

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import `in`.pcncloud.hotel.MainActivity

/**
 * Home key eater + dynamic OTT key blocker + Learn Mode shield.
 *
 * Learn Mode: pass D-pad / Enter / Back; swallow other keys on DOWN+UP,
 * broadcasting [BlockedKeysManager.ACTION_KEY_LEARNED] only on ACTION_UP
 * so the UI cannot disable Learn Mode before the release is eaten.
 */
class HomeKeyInterceptorService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var blockedKeys: Set<Int> = BlockedKeysManager.DEFAULT_BLOCKED_KEYS

    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                null,
                BlockedKeysManager.PREF_KEY_BLOCKED,
                -> refreshBlockedKeys()
                BlockedKeysManager.PREF_KEY_LEARN_MODE,
                -> {
                    learnModeCached = BlockedKeysManager.isLearnMode(this)
                    Log.i(TAG, "learnModeCached refreshed → $learnModeCached")
                }
            }
        }

    @Volatile
    private var learnModeCached: Boolean = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                notificationTimeout = 100
            }
            serviceInfo = info
            refreshBlockedKeys()
            learnModeCached = BlockedKeysManager.isLearnMode(this)
            BlockedKeysManager.registerChangeListener(this, prefsListener)
            Log.i(
                TAG,
                "HomeKeyInterceptor connected — blocked=$blockedKeys learnMode=$learnModeCached",
            )
        } catch (e: Exception) {
            Log.e(TAG, "onServiceConnected failed (ignored)", e)
        }
    }

    override fun onDestroy() {
        try {
            BlockedKeysManager.unregisterChangeListener(this, prefsListener)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty — key filtering only.
    }

    override fun onInterrupt() {
        // No-op.
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        return try {
            Log.e(
                "KeyLogger",
                ">>> KEY PRESSED: keyCode=${event.keyCode}, action=${event.action} <<<",
            )

            // Dynamic Learn Mode check on EVERY key (memory-first in BlockedKeysManager).
            val learnMode = BlockedKeysManager.isLearnMode(this) || learnModeCached
            if (learnMode) {
                learnModeCached = true
                return handleLearnModeKey(event)
            }
            learnModeCached = false

            // ——— Normal mode: swallow admin-blocked OTT keys ———
            if (event.keyCode in blockedKeys) {
                return true
            }

            if (!KioskPolicy.isKioskModeEnabled(this)) return false

            when (event.keyCode) {
                KeyEvent.KEYCODE_HOME,
                KEYCODE_OEM_HOME,
                -> {
                    // Swallow immediately. Do NOT Handler.post + startActivity —
                    // plain startActivity is throttled ~5s on Android TV while the
                    // stock launcher paints. PendingIntent reclaim races ahead.
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        reclaimHomeImmediately()
                    }
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "onKeyEvent failed — safe swallow when possible", e)
            try {
                if (BlockedKeysManager.isLearnMode(this) && !isEssentialNavKey(event.keyCode)) {
                    true
                } else {
                    event.keyCode == KeyEvent.KEYCODE_HOME ||
                        event.keyCode == KEYCODE_OEM_HOME ||
                        event.keyCode in blockedKeys
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Learn Mode (global Accessibility shield):
     * - DPAD / ENTER / BACK → pass through for Admin UI
     * - Other keys (OTT): swallow ACTION_DOWN silently; broadcast only on ACTION_UP
     *   so the UI does not turn Learn Mode off before the release is eaten.
     */
    private fun handleLearnModeKey(event: KeyEvent): Boolean {
        if (isEssentialNavKey(event.keyCode)) {
            Log.d(TAG, "Learn Mode — pass nav keyCode=${event.keyCode}")
            return false
        }
        // Swallow DOWN without notifying UI — Learn Mode must stay on for the matching UP.
        if (event.action == KeyEvent.ACTION_DOWN) {
            Log.d(TAG, "Learn Mode SHIELD swallow DOWN keyCode=${event.keyCode}")
            return true
        }
        if (event.action == KeyEvent.ACTION_UP) {
            Log.e(
                TAG,
                "Learn Mode SHIELD swallow UP keyCode=${event.keyCode} — broadcasting ACTION_KEY_LEARNED",
            )
            broadcastKeyLearned(event.keyCode)
            return true
        }
        return true
    }

    private fun broadcastKeyLearned(keyCode: Int) {
        try {
            val intent = Intent(BlockedKeysManager.ACTION_KEY_LEARNED).apply {
                setPackage(packageName)
                putExtra(BlockedKeysManager.EXTRA_KEY_CODE, keyCode)
            }
            sendBroadcast(intent)
            Log.i(TAG, "Learn Mode broadcast keyCode=$keyCode")
        } catch (e: Exception) {
            Log.e(TAG, "ACTION_KEY_LEARNED broadcast failed", e)
        }
    }

    private fun isEssentialNavKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BACK,
            -> true
            else -> false
        }
    }

    private fun refreshBlockedKeys() {
        try {
            blockedKeys = BlockedKeysManager.getBlockedKeys(this)
            Log.i(TAG, "blockedKeys refreshed → $blockedKeys")
        } catch (e: Exception) {
            Log.e(TAG, "refreshBlockedKeys failed — keeping previous set", e)
        }
    }

    /**
     * HOME reclaim for Accessibility filter — prefer Physical-TV urgent PendingIntent
     * (no ActivityManager startActivity throttle / GTPL flash).
     */
    private fun reclaimHomeImmediately() {
        try {
            KioskPolicy.clearExternalAppActive(this)
            KioskPolicy.clearOttLaunchState(this)
            KioskPolicy.clearUserMinimized(this)
        } catch (_: Exception) {
        }

        val navigateHome =
            !KioskPolicy.isStaffAdminUiActive() && !KioskPolicy.isExitingAppCleanly()

        // Prefer PendingIntent path (0ms race vs stock launcher).
        // Bypass timed suppress (Home-picker 180s) — guest Home must always reclaim.
        // Still blocked while Staff Secret Settings / clean exit are active.
        if (KioskPolicy.isReclaimSuppressed() &&
            !KioskPolicy.isStaffAdminUiActive() &&
            !KioskPolicy.isExitingAppCleanly()
        ) {
            KioskPolicy.clearReclaimSuppression("accessibility_home")
        } else if (KioskPolicy.isReclaimSuppressed()) {
            Log.d(
                TAG,
                "HOME — timed suppress kept (staff=${KioskPolicy.isStaffAdminUiActive()} " +
                    "exit=${KioskPolicy.isExitingAppCleanly()})",
            )
        }

        val skipLabel = KioskPolicy.reclaimSkipLabel(
            reason = "accessibility_home",
            context = this,
            ignoreTimedSuppress = true,
        )
        if (skipLabel != null) {
            Log.i(TAG, "HOME swallowed — skip reclaim ($skipLabel)")
            return
        }

        val sent = try {
            KioskPolicy.forceBringToFrontPhysicalTvUrgent(
                context = this,
                navigateToHome = navigateHome,
                bypassDuplicateGuard = true,
                ignoreTimedSuppress = true,
            )
        } catch (e: Exception) {
            Log.e(TAG, "forceBringToFrontPhysicalTvUrgent from Home failed", e)
            false
        }

        if (sent) {
            Log.i(TAG, "HOME swallowed — PendingIntent reclaim sent")
            return
        }

        // Last resort only if PendingIntent path refused (kiosk off / throttle).
        Log.w(TAG, "HOME PendingIntent reclaim skipped — fallback startActivity")
        launchMainActivityFromHomeFallback(navigateHome)
    }

    private fun launchMainActivityFromHomeFallback(navigateHome: Boolean) {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
                if (navigateHome) {
                    putExtra(MainActivity.EXTRA_NAVIGATE_TO_HOME, true)
                }
            }
            startActivity(intent)
            Log.i(TAG, "Launched MainActivity from Home fallback startActivity")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch MainActivity from Home", e)
        }
    }

    private fun launchMainActivityFromHome() {
        reclaimHomeImmediately()
    }

    companion object {
        private const val TAG = "KeyInterceptor"
        private const val KEYCODE_OEM_HOME = 228
    }
}
