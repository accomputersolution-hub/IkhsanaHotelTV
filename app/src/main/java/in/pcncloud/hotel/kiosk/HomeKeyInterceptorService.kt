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
 * Hardware HOME + dedicated remote hotkey interceptor for OEM Android TV.
 *
 * Hierarchy:
 * - Kiosk OFF → do NOT intercept (return false)
 * - Kiosk ON + HOME → Root Home / consume
 * - Kiosk ON + Netflix/YouTube/Prime/Apps hotkeys → consume (block escape)
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
        Log.i(
            TAG,
            "HomeKeyInterceptorService connected — filtering HOME + dedicated OTT hotkeys",
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Key filtering only — no UI / window event handling.
    }

    override fun onInterrupt() {
        Log.w(TAG, "HomeKeyInterceptorService interrupted")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!KioskPolicy.isKioskModeEnabled(this)) {
            return false
        }

        val keyCode = event.keyCode

        // Dedicated remote buttons (YouTube / Prime / Netflix / Apps) — always consume.
        if (KioskHotkeys.shouldBlockUnderKiosk(keyCode)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                Log.i(
                    TAG,
                    "Blocked remote hotkey ${KioskHotkeys.label(keyCode)} " +
                        "(keyCode=$keyCode) under kiosk",
                )
            }
            return true
        }

        if (keyCode != KeyEvent.KEYCODE_HOME) {
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
            Log.i(TAG, "HOME consumed — already on Root Home Screen")
            return
        }

        bringToRootHome()
    }

    private fun bringToRootHome() {
        try {
            KioskPolicy.clearUserMinimized(this)

            if (Build.VERSION.SDK_INT in 29..30) {
                KioskPolicy.forceBringToFrontSafely(this, navigateToHome = true)
                Log.i(TAG, "HOME → forceBringToFrontSafely (API 29/30)")
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

        /** Short form some OEMs accept in ENABLED_ACCESSIBILITY_SERVICES. */
        const val COMPONENT_SHORT =
            "in.pcncloud.hotel/.kiosk.HomeKeyInterceptorService"

        fun getComponentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, HomeKeyInterceptorService::class.java)

        fun isEnabled(context: Context): Boolean {
            return try {
                val enabled = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                ).orEmpty()
                val expected = getComponentName(context).flattenToString()
                enabled.split(':').any {
                    it.equals(expected, ignoreCase = true) ||
                        it.equals(COMPONENT_FLAT, ignoreCase = true) ||
                        it.equals(COMPONENT_SHORT, ignoreCase = true)
                } ||
                    enabled.contains(COMPONENT_FLAT, ignoreCase = true) ||
                    enabled.contains(COMPONENT_SHORT, ignoreCase = true)
            } catch (e: Exception) {
                Log.w(TAG, "isEnabled check failed", e)
                false
            }
        }

        /**
         * Re-enable this accessibility service when OEM HOME kills it.
         * Requires [android.Manifest.permission.WRITE_SECURE_SETTINGS]
         * (`adb shell pm grant … WRITE_SECURE_SETTINGS`) or Device Owner privilege.
         *
         * @return true if settings write succeeded (or service already enabled)
         */
        fun tryReenableAccessibility(context: Context): Boolean {
            if (isEnabled(context)) return true
            if (!KioskPolicy.isKioskModeEnabled(context)) return false

            return try {
                val cr = context.contentResolver
                val current = Settings.Secure.getString(
                    cr,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                ).orEmpty()

                val merged = mergeAccessibilityServices(current, COMPONENT_FLAT)
                val wroteServices = Settings.Secure.putString(
                    cr,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    merged,
                )
                val wroteFlag = Settings.Secure.putInt(
                    cr,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    1,
                )

                val ok = wroteServices && wroteFlag
                if (ok) {
                    Log.i(TAG, "Self-heal: re-enabled accessibility → $merged")
                } else {
                    Log.w(
                        TAG,
                        "Self-heal putString returned false " +
                            "(need WRITE_SECURE_SETTINGS grant?)",
                    )
                }
                ok && isEnabled(context)
            } catch (e: SecurityException) {
                Log.w(
                    TAG,
                    "Self-heal denied — grant WRITE_SECURE_SETTINGS: " +
                        "adb shell pm grant ${context.packageName} " +
                        "android.permission.WRITE_SECURE_SETTINGS",
                    e,
                )
                false
            } catch (e: Exception) {
                Log.e(TAG, "Self-heal failed", e)
                false
            }
        }

        /** Append [component] to colon-separated accessibility list if missing. */
        private fun mergeAccessibilityServices(current: String, component: String): String {
            if (current.isBlank()) return component
            val parts = current.split(':').filter { it.isNotBlank() }
            if (parts.any {
                    it.equals(component, ignoreCase = true) ||
                        it.equals(COMPONENT_SHORT, ignoreCase = true)
                }
            ) {
                return current
            }
            return "$current:$component"
        }

        fun adbEnableCommands(): List<String> = listOf(
            "adb shell pm grant in.pcncloud.hotel android.permission.WRITE_SECURE_SETTINGS",
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
                    "HOME / dedicated buttons will not be intercepted until accessibility is enabled",
                )
            }
        }
    }
}
