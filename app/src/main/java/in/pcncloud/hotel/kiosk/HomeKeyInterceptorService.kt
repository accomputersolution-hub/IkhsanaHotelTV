package `in`.pcncloud.hotel.kiosk

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import `in`.pcncloud.hotel.MainActivity

/**
 * App-level kiosk interceptor (Solution 1 — no system package disables).
 *
 * When kiosk ON:
 * - [onKeyEvent]: swallow HOME + dedicated OTT remote buttons
 * - [onAccessibilityEvent]: reclaim when GTPL / stock launcher / setup gains focus
 * - 800ms foreground poll: one PendingIntent reclaim when MainActivity is backgrounded
 *   (avoids ActivityManager throttle from rapid-fire startActivity)
 *
 * When kiosk OFF: all paths no-op so `com.gtpl.customgooglelauncher` can focus.
 */
class HomeKeyInterceptorService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var lastReclaimedPackage: String = ""

    @Volatile
    private var lastReclaimedAtMs: Long = 0L

    @Volatile
    private var lastForegroundPollReclaimAtMs: Long = 0L

    @Volatile
    private var foregroundPollActive: Boolean = false

    private val foregroundPollRunnable = object : Runnable {
        override fun run() {
            try {
                if (!KioskPolicy.isKioskModeEnabled(this@HomeKeyInterceptorService)) {
                    stopForegroundPoll()
                    return
                }
                if (KioskPolicy.isExternalAppActive(this@HomeKeyInterceptorService)) {
                    // Guest intentionally in YouTube / OTT — do not reclaim.
                    handler.postDelayed(this, FOREGROUND_POLL_MS)
                    return
                }
                if (KioskPolicy.isReclaimSuppressed()) {
                    // Home launcher picker / Settings open — do not steal focus.
                    handler.postDelayed(this, FOREGROUND_POLL_MS)
                    return
                }
                // Accidental background (HOME leaked to OS) — PendingIntent reclaim at most
                // once every FOREGROUND_POLL_MS so ActivityManager never throttles.
                if (!KioskPolicy.isMainActivityForeground(this@HomeKeyInterceptorService)) {
                    val now = System.currentTimeMillis()
                    if (now - lastForegroundPollReclaimAtMs < FOREGROUND_POLL_MS) {
                        Log.d(
                            TAG,
                            "Foreground poll — skip reclaim " +
                                "(${now - lastForegroundPollReclaimAtMs}ms < ${FOREGROUND_POLL_MS}ms)",
                        )
                    } else {
                        Log.w(TAG, "Foreground poll — MainActivity not foreground, PendingIntent reclaim")
                        tryReenableAccessibility(applicationContext)
                        KioskPolicy.clearUserMinimized(this@HomeKeyInterceptorService)
                        lastForegroundPollReclaimAtMs = now
                        if (KioskPolicy.needsPhysicalTvFallback(this@HomeKeyInterceptorService)) {
                            KioskPolicy.forceBringToFrontPhysicalTvUrgent(
                                context = applicationContext,
                                navigateToHome = true,
                            )
                        } else {
                            KioskPolicy.forceBringToFrontSafely(
                                context = applicationContext,
                                navigateToHome = true,
                                preferImmediateOptions = true,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Foreground poll error", e)
            }
            if (foregroundPollActive) {
                handler.postDelayed(this, FOREGROUND_POLL_MS)
            }
        }
    }

    private val kioskModeListener: (Boolean) -> Unit = { enabled ->
        if (enabled) {
            startForegroundPoll()
        } else {
            stopForegroundPoll()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 100
        }
        serviceInfo = info
        KioskPolicy.addKioskModeChangedListener(kioskModeListener)
        if (KioskPolicy.isKioskModeEnabled(this)) {
            startForegroundPoll()
        }
        Log.i(
            TAG,
            "HomeKeyInterceptor connected — HOME/OTT keys + GTPL reclaim + ${FOREGROUND_POLL_MS}ms poll",
        )
    }

    override fun onDestroy() {
        stopForegroundPoll()
        KioskPolicy.removeKioskModeChangedListener(kioskModeListener)
        super.onDestroy()
    }

    private fun startForegroundPoll() {
        if (foregroundPollActive) return
        foregroundPollActive = true
        handler.removeCallbacks(foregroundPollRunnable)
        handler.postDelayed(foregroundPollRunnable, FOREGROUND_POLL_MS)
        Log.i(TAG, "Foreground poll STARTED (${FOREGROUND_POLL_MS}ms)")
    }

    private fun stopForegroundPoll() {
        foregroundPollActive = false
        handler.removeCallbacks(foregroundPollRunnable)
        Log.i(TAG, "Foreground poll STOPPED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!KioskPolicy.isKioskModeEnabled(this)) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        // Intentional Entertainment OTT — do not fight YouTube / Netflix.
        if (KioskPolicy.isExternalAppActive(this)) return
        if (KioskPolicy.isReclaimSuppressed()) return

        val packageName = event.packageName?.toString()?.trim().orEmpty()
        if (packageName.isEmpty()) return
        if (packageName.equals(applicationContext.packageName, ignoreCase = true)) return
        if (!KioskPolicy.isStockLauncherOrSetupPackage(packageName)) return

        val now = System.currentTimeMillis()
        if (packageName.equals(lastReclaimedPackage, ignoreCase = true) &&
            now - lastReclaimedAtMs < PACKAGE_RECLAIM_DEBOUNCE_MS
        ) {
            return
        }
        lastReclaimedPackage = packageName
        lastReclaimedAtMs = now

        Log.w(TAG, "Kiosk ON — reclaim from stock launcher/setup: $packageName")
        KioskPolicy.clearUserMinimized(this)
        if (KioskPolicy.needsPhysicalTvFallback(this)) {
            KioskPolicy.forceBringToFrontPhysicalTvUrgent(
                context = applicationContext,
                navigateToHome = true,
            )
        } else {
            KioskPolicy.forceBringToFrontSafely(
                context = applicationContext,
                navigateToHome = true,
                preferImmediateOptions = true,
            )
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "HomeKeyInterceptorService interrupted")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Kiosk OFF → never consume; GTPL / OEM handle HOME & OTT buttons.
        if (!KioskPolicy.isKioskModeEnabled(this)) {
            return false
        }

        val keyCode = event.keyCode

        if (KioskHotkeys.shouldBlockUnderKiosk(keyCode) ||
            keyCode == 228 || keyCode == 247 ||
            keyCode == 288 || keyCode == 289
        ) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                Log.i(
                    TAG,
                    "Blocked remote key ${KioskHotkeys.label(keyCode)} (keyCode=$keyCode)",
                )
            }
            return true
        }

        // BACK is NOT consumed here — MainActivity handles sub-menu → Home / Home lock.
        // HOME remains fully secured below (unchanged).
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return false
        }

        if (keyCode != KeyEvent.KEYCODE_HOME) {
            return false
        }

        // Consume BOTH DOWN and UP so OS never gets HOME.
        if (event.action == KeyEvent.ACTION_DOWN) {
            handleHomeKey()
        }
        return true
    }

    private fun handleHomeKey() {
        val onRoot = KioskPolicy.isOnRootHomeScreen(this)
        Log.i(
            TAG,
            "HOME pressed (kiosk ON) → onRoot=$onRoot " +
                "foreground=${KioskPolicy.isMainActivityForeground(this)} " +
                "ott=${KioskPolicy.isExternalAppActive(this)}",
        )

        if (onRoot && KioskPolicy.isMainActivityForeground(this)) {
            Log.i(TAG, "HOME consumed — already on Root Home")
            return
        }

        KioskPolicy.clearUserMinimized(this)
        KioskPolicy.clearOttLaunchState(this)
        val ok = if (KioskPolicy.needsPhysicalTvFallback(this)) {
            KioskPolicy.forceBringToFrontPhysicalTvUrgent(
                context = applicationContext,
                navigateToHome = true,
            )
        } else {
            KioskPolicy.forceBringToFrontSafely(
                context = applicationContext,
                navigateToHome = true,
                preferImmediateOptions = true,
            )
        }
        if (!ok) {
            bringToRootHomeFallback()
        }
        Log.i(TAG, "HOME → reclaim ok=$ok")
    }

    private fun bringToRootHomeFallback() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
                putExtra(MainActivity.EXTRA_NAVIGATE_TO_HOME, true)
                action = Intent.ACTION_MAIN
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "HOME fallback startActivity failed", e)
        }
    }

    companion object {
        private const val TAG = "HomeKeyInterceptor"
        private const val PACKAGE_RECLAIM_DEBOUNCE_MS = 100L
        /** PendingIntent reclaim at most once per 800ms — avoids ActivityManager throttle. */
        private const val FOREGROUND_POLL_MS = 800L

        const val COMPONENT_FLAT =
            "in.pcncloud.hotel/in.pcncloud.hotel.kiosk.HomeKeyInterceptorService"

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
         * Best-effort silent re-enable via [Settings.Secure] when the app has
         * WRITE_SECURE_SETTINGS (adb grant) or is Device Owner.
         * Never throws into the Activity lifecycle — SecurityException is swallowed.
         */
        fun tryReenableAccessibility(context: Context): Boolean {
            if (isEnabled(context)) return true
            if (!KioskPolicy.isKioskModeEnabled(context)) return false

            if (!hasWriteSecureSettings(context)) {
                Log.w(
                    TAG,
                    "Self-heal skipped — WRITE_SECURE_SETTINGS not granted. " +
                        "Grant once: adb shell pm grant ${context.packageName} " +
                        "android.permission.WRITE_SECURE_SETTINGS",
                )
                return false
            }

            return try {
                val cr = context.contentResolver
                val current = try {
                    Settings.Secure.getString(
                        cr,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    ).orEmpty()
                } catch (e: SecurityException) {
                    Log.w(TAG, "Self-heal: cannot read ENABLED_ACCESSIBILITY_SERVICES", e)
                    return false
                }

                val merged = mergeAccessibilityServices(current, COMPONENT_FLAT)
                val wroteServices = try {
                    Settings.Secure.putString(
                        cr,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        merged,
                    )
                } catch (e: SecurityException) {
                    Log.w(
                        TAG,
                        "Self-heal SecurityException on putString(ENABLED_ACCESSIBILITY_SERVICES) — fallback",
                        e,
                    )
                    return false
                }

                val wroteFlag = try {
                    Settings.Secure.putInt(
                        cr,
                        Settings.Secure.ACCESSIBILITY_ENABLED,
                        1,
                    )
                } catch (e: SecurityException) {
                    Log.w(
                        TAG,
                        "Self-heal SecurityException on putInt(ACCESSIBILITY_ENABLED) — fallback",
                        e,
                    )
                    return false
                }

                val ok = wroteServices && wroteFlag && isEnabled(context)
                if (ok) {
                    Log.i(TAG, "Self-heal: re-enabled accessibility → $merged")
                } else {
                    Log.w(
                        TAG,
                        "Self-heal putString/putInt returned false (permission may be incomplete)",
                    )
                }
                ok
            } catch (e: SecurityException) {
                Log.w(TAG, "Self-heal SecurityException — graceful fallback", e)
                false
            } catch (e: Exception) {
                Log.e(TAG, "Self-heal failed — graceful fallback", e)
                false
            }
        }

        /** True when [android.Manifest.permission.WRITE_SECURE_SETTINGS] is granted to this process. */
        fun hasWriteSecureSettings(context: Context): Boolean {
            return try {
                context.checkCallingOrSelfPermission(
                    android.Manifest.permission.WRITE_SECURE_SETTINGS,
                ) == PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                Log.w(TAG, "hasWriteSecureSettings check failed", e)
                false
            }
        }

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
                Log.w(TAG, "HOME / GTPL reclaim inactive until accessibility is enabled")
            }
        }
    }
}
