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
 * Ultra-lightweight Home key eater + dynamic OTT key blocker for physical Android TV.
 *
 * Logs every remote key via [KeyLogger], silently drops admin-blocked keyCodes from
 * [BlockedKeysManager], and under kiosk swallows HOME / OEM 228 then posts a
 * main-thread [MainActivity] launch.
 */
class HomeKeyInterceptorService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var blockedKeys: Set<Int> = BlockedKeysManager.DEFAULT_BLOCKED_KEYS

    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == BlockedKeysManager.PREF_KEY_BLOCKED) {
                refreshBlockedKeys()
            }
        }

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
            BlockedKeysManager.registerChangeListener(this, prefsListener)
            Log.i(TAG, "HomeKeyInterceptor connected — blocked=$blockedKeys")
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

    /**
     * Log every key; swallow dynamic blocked OTT keys; Home / OEM Home under kiosk.
     * Entire body is try/catch — uncaught exceptions disable Accessibility services.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        return try {
            Log.e(
                "KeyLogger",
                ">>> KEY PRESSED: keyCode=${event.keyCode}, action=${event.action} <<<",
            )

            // Silent drop — admin-managed OTT / dedicated buttons (never reach OS),
            // unless Admin "Learn New Key" is active (must reach Activity).
            val learning = BlockedKeysManager.isLearningMode(this)
            if (!learning && event.keyCode in blockedKeys) {
                return true
            }

            if (!KioskPolicy.isKioskModeEnabled(this)) return false

            when (event.keyCode) {
                KeyEvent.KEYCODE_HOME,
                KEYCODE_OEM_HOME,
                -> {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        mainHandler.post { launchMainActivityFromHome() }
                    }
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "onKeyEvent failed — swallowing Home/OTT if applicable", e)
            try {
                event.keyCode == KeyEvent.KEYCODE_HOME ||
                    event.keyCode == KEYCODE_OEM_HOME ||
                    event.keyCode in blockedKeys
            } catch (_: Exception) {
                false
            }
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

    private fun launchMainActivityFromHome() {
        try {
            try {
                KioskPolicy.clearExternalAppActive(this)
                KioskPolicy.clearOttLaunchState(this)
                KioskPolicy.clearUserMinimized(this)
            } catch (_: Exception) {
            }

            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
                putExtra(MainActivity.EXTRA_NAVIGATE_TO_HOME, true)
            }
            startActivity(intent)
            Log.i(TAG, "Launched MainActivity from Home intercept (posted)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch MainActivity", e)
        }
    }

    companion object {
        private const val TAG = "KeyInterceptor"
        private const val KEYCODE_OEM_HOME = 228
    }
}
