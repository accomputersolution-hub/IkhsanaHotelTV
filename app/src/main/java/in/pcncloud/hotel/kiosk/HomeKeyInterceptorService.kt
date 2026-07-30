package `in`.pcncloud.hotel.kiosk

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import `in`.pcncloud.hotel.MainActivity

/**
 * Hardware HOME key interceptor for OEM ROMs that drop the Home intent
 * while Lock Task / kiosk is active.
 *
 * Hierarchy:
 * - Kiosk OFF → do NOT intercept (return false); [MainActivity.onKeyDown] launches system Home
 * - Kiosk ON + Root Home showing → consume HOME (no Intent / no flicker)
 * - Kiosk ON + sub-screen / OTT → bring [MainActivity] and reset to Root Home
 */
class HomeKeyInterceptorService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo?.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            eventTypes = 0
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        } ?: AccessibilityServiceInfo().apply {
            flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            eventTypes = 0
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 0
        }
        Log.i(TAG, "HomeKeyInterceptorService connected — filtering KEYCODE_HOME when kiosk ON")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Key filtering only — no UI / window event handling.
    }

    override fun onInterrupt() {
        Log.w(TAG, "HomeKeyInterceptorService interrupted")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) {
            return false
        }

        // Kiosk OFF → do NOT consume; let Activity / OS handle (MainActivity.onKeyDown).
        if (!KioskPolicy.isKioskModeEnabled(this)) {
            Log.d(TAG, "HOME not intercepted — isKioskModeEnabled=false")
            return false
        }

        if (event.action != KeyEvent.ACTION_DOWN) {
            // Consume UP as well so OEM does not forward a partial Home sequence.
            return true
        }

        handleHomeKey()
        return true
    }

    private fun handleHomeKey() {
        val onRoot = KioskPolicy.isOnRootHomeScreen(this)
        Log.i(
            TAG,
            "HOME pressed (kiosk ON) → isOnRootHomeScreen=$onRoot " +
                "foreground=${KioskPolicy.isMainActivityForeground(this)} " +
                "guestHome=${KioskPolicy.isOnGuestHomeScreen(this)} " +
                "ottSession=${KioskPolicy.isExternalAppActive(this)}",
        )

        if (onRoot) {
            // Already on Root Home — consume only (prevents blue flash).
            Log.i(TAG, "HOME consumed — already on Root Home Screen")
            return
        }

        // Sub-section (Dining / Entertainment / …) OR external app (YouTube):
        // bring MainActivity and reset navigation to Root Home.
        bringToRootHome()
    }

    private fun bringToRootHome() {
        try {
            KioskPolicy.clearUserMinimized(this)

            // API 29–30: use shared ActivityOptions reclaim (HOME slip fix).
            // API 31+ / others: keep direct startActivity (working path).
            if (Build.VERSION.SDK_INT in 29..30) {
                KioskPolicy.forceBringToFront(this, navigateToHome = true)
                Log.i(TAG, "HOME → forceBringToFront (API 29/30)")
                return
            }

            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
                putExtra(MainActivity.EXTRA_NAVIGATE_TO_HOME, true)
                action = Intent.ACTION_MAIN
            }
            startActivity(intent)
            Log.i(TAG, "HOME → MainActivity NEW_TASK|SINGLE_TOP|NO_ANIMATION + NAVIGATE_TO_HOME")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to Root Home after HOME", e)
        }
    }

    companion object {
        private const val TAG = "HomeKeyInterceptor"

        /** Component string for secure settings / ADB. */
        const val COMPONENT_FLAT =
            "in.pcncloud.hotel/in.pcncloud.hotel.kiosk.HomeKeyInterceptorService"

        fun getComponentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, HomeKeyInterceptorService::class.java)

        fun isEnabled(context: Context): Boolean {
            return try {
                val enabled = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                ).orEmpty()
                val expected = getComponentName(context).flattenToString()
                enabled.split(':').any { it.equals(expected, ignoreCase = true) } ||
                    enabled.contains(COMPONENT_FLAT, ignoreCase = true)
            } catch (e: Exception) {
                Log.w(TAG, "isEnabled check failed", e)
                false
            }
        }

        fun adbEnableCommands(): List<String> = listOf(
            "adb shell settings put secure enabled_accessibility_services $COMPONENT_FLAT",
            "adb shell settings put secure accessibility_enabled 1",
        )

        fun openAccessibilitySettings(context: Context) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK,
                    ),
                )
            } catch (e: Exception) {
                Log.e(TAG, "Could not open Accessibility settings", e)
            }
        }

        fun logStatus(context: Context) {
            val on = isEnabled(context)
            Log.i(
                TAG,
                "HomeKeyInterceptor enabled=$on component=$COMPONENT_FLAT " +
                    "adb=${adbEnableCommands().joinToString(" && ")}",
            )
            if (!on) {
                Log.w(
                    TAG,
                    "HOME hardware key will not be intercepted until accessibility is enabled",
                )
            }
        }
    }
}
