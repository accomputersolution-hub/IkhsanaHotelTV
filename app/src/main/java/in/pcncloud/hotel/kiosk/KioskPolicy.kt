package `in`.pcncloud.hotel.kiosk

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Central gate for any code path that might call [Context.startActivity] to bring
 * the hotel TV UI to the foreground (BootReceiver, Splash delayed nav, Watchdog FGS).
 *
 * Rules:
 * - Regular Home / minimize → do **not** relaunch.
 * - Bring-to-front only when **kiosk mode is ON** or a prior **crash/kill** needs recovery.
 * - Never launch if the process already has a visible / created task.
 */
object KioskPolicy {

    private const val TAG = "KioskPolicy"
    private const val PREFS = "hotel_tv_kiosk"
    private const val KEY_KIOSK_ENABLED = "kiosk_mode_enabled"
    private const val KEY_USER_MINIMIZED = "user_minimized"
    private const val KEY_EXITED_CLEANLY = "exited_cleanly"
    private const val KEY_PENDING_CRASH_RECOVERY = "pending_crash_recovery"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Hotel kiosk lock — default OFF so staff can minimize without forced relaunch. */
    fun isKioskModeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_KIOSK_ENABLED, false)

    fun setKioskModeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_KIOSK_ENABLED, enabled).apply()
        Log.i(TAG, "kiosk_mode_enabled=$enabled")
    }

    fun markUserMinimized(context: Context) {
        prefs(context).edit().putBoolean(KEY_USER_MINIMIZED, true).apply()
        Log.d(TAG, "markUserMinimized")
    }

    fun clearUserMinimized(context: Context) {
        prefs(context).edit().putBoolean(KEY_USER_MINIMIZED, false).apply()
    }

    fun wasUserMinimized(context: Context): Boolean =
        prefs(context).getBoolean(KEY_USER_MINIMIZED, false)

    /**
     * Call from [android.app.Application.onCreate] before UI starts.
     * If the previous process did not exit cleanly, arm crash recovery once.
     */
    fun onProcessStart(context: Context) {
        val p = prefs(context)
        val exitedCleanly = p.getBoolean(KEY_EXITED_CLEANLY, true)
        if (!exitedCleanly) {
            p.edit()
                .putBoolean(KEY_PENDING_CRASH_RECOVERY, true)
                .apply()
            Log.w(TAG, "Previous process exited uncleanly — crash recovery armed")
        }
        // New session starts unclean until ProcessLifecycle ON_STOP or explicit clean exit.
        p.edit().putBoolean(KEY_EXITED_CLEANLY, false).apply()
    }

    /** Foreground session active — dying now without ON_STOP counts as unclean. */
    fun markSessionActive(context: Context) {
        prefs(context).edit().putBoolean(KEY_EXITED_CLEANLY, false).apply()
    }

    fun markCleanExit(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_EXITED_CLEANLY, true)
            .apply()
    }

    fun markUnexpectedCrash(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_EXITED_CLEANLY, false)
            .putBoolean(KEY_PENDING_CRASH_RECOVERY, true)
            .apply()
        Log.e(TAG, "markUnexpectedCrash")
    }

    fun consumeCrashRecovery(context: Context): Boolean {
        val p = prefs(context)
        if (!p.getBoolean(KEY_PENDING_CRASH_RECOVERY, false)) return false
        p.edit().putBoolean(KEY_PENDING_CRASH_RECOVERY, false).apply()
        return true
    }

    fun hasPendingCrashRecovery(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PENDING_CRASH_RECOVERY, false)

    /** True when this process's UI is at least started (visible or partially visible). */
    fun isProcessLifecycleStarted(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(Lifecycle.State.STARTED)

    /**
     * True if the package already has a non-empty task (activity created / in recents).
     * Used to avoid duplicate startActivity when the app is merely backgrounded.
     */
    fun hasExistingTask(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.appTasks.any { task ->
                val info = task.taskInfo
                val num = if (Build.VERSION.SDK_INT >= 23) {
                    info.numActivities
                } else {
                    @Suppress("DEPRECATION")
                    info.numActivities
                }
                num > 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "hasExistingTask failed", e)
            false
        }
    }

    /**
     * Whether a background component may bring the hotel UI to the front.
     *
     * Allowed only when:
     * - Kiosk mode is explicitly ON, or
     * - A crash/kill recovery is pending
     *
     * Blocked when:
     * - User explicitly minimized (Home), or
     * - Process lifecycle is already STARTED, or
     * - An existing task is already created (unless crash recovery / kiosk needs reorder)
     */
    fun shouldBringAppToFront(context: Context, allowReorderIfKiosk: Boolean = true): Boolean {
        if (wasUserMinimized(context) && !isKioskModeEnabled(context)) {
            Log.d(TAG, "shouldBringAppToFront=false (user minimized, kiosk off)")
            return false
        }

        val kiosk = isKioskModeEnabled(context)
        val crash = hasPendingCrashRecovery(context)
        if (!kiosk && !crash) {
            Log.d(TAG, "shouldBringAppToFront=false (kiosk off, no crash recovery)")
            return false
        }

        if (isProcessLifecycleStarted()) {
            Log.d(TAG, "shouldBringAppToFront=false (process already STARTED)")
            return false
        }

        if (hasExistingTask(context) && !(allowReorderIfKiosk && kiosk)) {
            Log.d(TAG, "shouldBringAppToFront=false (task already exists)")
            return false
        }

        Log.i(TAG, "shouldBringAppToFront=true (kiosk=$kiosk crash=$crash)")
        return true
    }

    /**
     * Safe startActivity for services / receivers. Returns false if blocked.
     * Always adds NEW_TASK when starting from non-Activity context.
     */
    fun startActivityIfAllowed(
        context: Context,
        intent: Intent,
        reason: String,
    ): Boolean {
        if (!shouldBringAppToFront(context)) {
            Log.i(TAG, "Blocked startActivity ($reason)")
            return false
        }
        return try {
            val launch = Intent(intent).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            context.startActivity(launch)
            if (hasPendingCrashRecovery(context)) {
                consumeCrashRecovery(context)
            }
            clearUserMinimized(context)
            Log.i(TAG, "startActivity allowed ($reason)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "startActivity failed ($reason)", e)
            false
        }
    }

    /** Activity may navigate forward only while at least STARTED (not stopped/minimized). */
    fun canActivityNavigate(lifecycle: Lifecycle): Boolean =
        lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
}
