package `in`.pcncloud.hotel.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.lang.ref.WeakReference

/**
 * Aggressive Chromecast / AirScreen handoff for hotel kiosk TVs.
 *
 * Allowlisting mediashell alone is often not enough under Lock Task: Cast connects
 * on the phone but the hotel UI stays on the TV. This monitor temporarily stops
 * Lock Task when Cast elevates, then restores the kiosk when Cast ends.
 */
object CastHandoffMonitor {

    private const val TAG = "CastHandoff"
    private const val POLL_MS_IDLE = 700L
    private const val POLL_MS_ARMED = 350L
    private const val POLL_MS_YIELDED = 1_000L
    private const val DEFAULT_ARM_MS = 15 * 60 * 1000L

    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var activityRef: WeakReference<Activity>? = null
    private var running = false

    @Volatile
    private var yieldedForCast = false

    @Volatile
    private var armedUntilElapsedMs = 0L

    @Volatile
    private var wasArmedLastTick = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            try {
                tick()
            } catch (t: Throwable) {
                Log.e(TAG, "Cast handoff tick failed", t)
            } finally {
                if (running) {
                    handler.postDelayed(this, nextPollMs())
                }
            }
        }
    }

    fun attachActivity(activity: Activity) {
        activityRef = WeakReference(activity)
        start(activity.applicationContext)
    }

    fun detachActivity(activity: Activity) {
        if (activityRef?.get() === activity) {
            activityRef = null
        }
    }

    fun start(context: Context) {
        appContext = context.applicationContext
        if (running) return
        running = true
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
        Log.i(TAG, "Cast handoff monitor started")
    }

    fun stop() {
        running = false
        handler.removeCallbacks(pollRunnable)
        Log.i(TAG, "Cast handoff monitor stopped")
    }

    fun isYieldingForCast(): Boolean = yieldedForCast

    fun isArmedForIncomingCast(): Boolean =
        SystemClock.elapsedRealtime() < armedUntilElapsedMs

    fun shouldSkipLockTaskPin(context: Context): Boolean {
        if (yieldedForCast) return true
        if (isArmedForIncomingCast()) return true
        if (isCastReceiverElevated(context)) return true
        return false
    }

    fun prepareForIncomingCast(activity: Activity, armMs: Long = DEFAULT_ARM_MS) {
        attachActivity(activity)
        armForIncomingCast(armMs)
        KioskLockTask.ensureChromecastAllowlisted(activity)
        KioskPolicy.suppressReclaimFor(armMs, "cast_armed_prepare")
        stopLockTaskOn(activity, "prepare_incoming_cast")
        Log.i(TAG, "Prepared for incoming Cast — Lock Task stopped, armed ${armMs}ms")
    }

    fun armForIncomingCast(armMs: Long = DEFAULT_ARM_MS) {
        val until = SystemClock.elapsedRealtime() + armMs.coerceAtLeast(1_000L)
        armedUntilElapsedMs = maxOf(armedUntilElapsedMs, until)
        Log.i(TAG, "Armed for incoming Cast until elapsed=$until")
    }

    fun cancelIncomingCastArm(activity: Activity? = null) {
        if (yieldedForCast || (appContext != null && isCastReceiverElevated(appContext!!))) {
            Log.i(TAG, "cancelIncomingCastArm skipped — Cast still active/yielding")
            return
        }
        armedUntilElapsedMs = 0L
        KioskPolicy.clearReclaimSuppression("cast_arm_cancelled")
        val act = activity ?: activityRef?.get()
        if (act != null && !act.isFinishing && KioskPolicy.isKioskModeEnabled(act)) {
            try {
                act.startLockTask()
                Log.i(TAG, "startLockTask() after Cast arm cancelled")
            } catch (t: Throwable) {
                Log.w(TAG, "startLockTask after Cast arm cancel failed", t)
            }
        }
    }

    fun isCastReceiverElevated(context: Context): Boolean {
        return isElevated(context, KioskLockTask.CHROMECAST_PACKAGE) ||
            isElevated(context, KioskLockTask.AIRSCREEN_PACKAGE)
    }

    private fun nextPollMs(): Long = when {
        yieldedForCast -> POLL_MS_YIELDED
        isArmedForIncomingCast() -> POLL_MS_ARMED
        else -> POLL_MS_IDLE
    }

    private fun isElevated(context: Context, packageName: String): Boolean {
        if (
            KioskPolicy.isPackageAtMostImportance(
                context,
                packageName,
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE,
            )
        ) {
            return true
        }
        if (
            isArmedForIncomingCast() &&
            KioskPolicy.isPackageAtMostImportance(
                context,
                packageName,
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE,
            )
        ) {
            return true
        }
        return isTopActivity(context, packageName)
    }

    @Suppress("DEPRECATION")
    private fun isTopActivity(context: Context, packageName: String): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return false
            val tasks = am.getRunningTasks(5) ?: return false
            tasks.any { task ->
                val top = task.topActivity?.packageName
                val base = task.baseActivity?.packageName
                top == packageName || base == packageName
            }
        } catch (t: Throwable) {
            Log.d(TAG, "isTopActivity($packageName) unavailable: ${t.message}")
            false
        }
    }

    private fun tick() {
        val context = appContext ?: return
        if (!KioskPolicy.isKioskModeEnabled(context)) {
            if (yieldedForCast) {
                Log.i(TAG, "Kiosk off while yielded — clearing Cast yield state")
                yieldedForCast = false
                armedUntilElapsedMs = 0L
                KioskPolicy.clearStaleCastSessionIfEnded(context)
            }
            return
        }

        KioskLockTask.ensureChromecastAllowlisted(context)

        val casting = isCastReceiverElevated(context)
        if (casting) {
            if (!yieldedForCast) {
                yieldToCast(context)
            } else {
                KioskPolicy.markCastSessionIfActive(context)
            }
        } else if (yieldedForCast || KioskPolicy.isCastExternalSession(context)) {
            resumeKioskAfterCast(context)
        } else if (!isArmedForIncomingCast() && wasArmedLastTick) {
            wasArmedLastTick = false
            restoreLockTaskAfterArmExpired(context)
        }
        wasArmedLastTick = isArmedForIncomingCast()
    }

    private fun restoreLockTaskAfterArmExpired(context: Context) {
        Log.i(TAG, "Cast arm expired without session — restoring Lock Task")
        KioskPolicy.clearReclaimSuppression("cast_arm_expired")
        val activity = activityRef?.get()
        if (activity != null && !activity.isFinishing && KioskPolicy.isKioskModeEnabled(activity)) {
            activity.runOnUiThread {
                try {
                    if (!shouldSkipLockTaskPin(activity)) {
                        activity.startLockTask()
                        Log.i(TAG, "startLockTask() after Cast arm expired")
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "startLockTask after Cast arm expired failed", t)
                }
            }
        } else {
            KioskPolicy.forceBringToFrontSafely(
                context = context,
                preferImmediateOptions = true,
                ignoreTimedSuppress = true,
            )
        }
    }

    private fun yieldToCast(context: Context) {
        yieldedForCast = true
        val pkg = KioskPolicy.markCastSessionIfActive(context)
            ?: KioskLockTask.CHROMECAST_PACKAGE
        KioskPolicy.markOttLaunched(context, pkg)
        KioskPolicy.suppressReclaimFor(4 * 60 * 60 * 1000L, "cast_handoff_yield")

        val activity = activityRef?.get()
        Log.i(
            TAG,
            "YIELD to Cast ($pkg) — stopLockTask + moveTaskToBack " +
                "(activity=${activity != null})",
        )

        if (activity != null && !activity.isFinishing) {
            stopLockTaskOn(activity, "yield_to_cast")
            try {
                activity.moveTaskToBack(true)
                Log.i(TAG, "moveTaskToBack(true) for Cast handoff")
            } catch (t: Throwable) {
                Log.w(TAG, "moveTaskToBack during Cast handoff failed", t)
            }
        } else {
            Log.w(TAG, "No Activity to unpin — Cast may still be blocked until resume")
        }
    }

    private fun stopLockTaskOn(activity: Activity, reason: String) {
        try {
            activity.stopLockTask()
            Log.i(TAG, "stopLockTask() for Cast ($reason)")
        } catch (t: Throwable) {
            Log.w(TAG, "stopLockTask during Cast ($reason) failed", t)
        }
    }

    private fun resumeKioskAfterCast(context: Context) {
        Log.i(TAG, "Cast ended — restoring hotel kiosk Lock Task")
        yieldedForCast = false
        armedUntilElapsedMs = 0L
        KioskPolicy.clearStaleCastSessionIfEnded(context)
        KioskPolicy.clearReclaimSuppression("cast_handoff_resume")
        KioskPolicy.clearExternalAppActive(context)
        KioskPolicy.clearOttLaunchState(context, suppressMs = 500L)

        KioskLockTask.ensureChromecastAllowlisted(context)

        val activity = activityRef?.get()
        if (activity != null && !activity.isFinishing) {
            try {
                KioskPolicy.forceBringToFrontSafely(
                    context = activity,
                    preferImmediateOptions = true,
                    ignoreTimedSuppress = true,
                )
            } catch (t: Throwable) {
                Log.w(TAG, "forceBringToFront after Cast failed", t)
            }
            activity.runOnUiThread {
                try {
                    if (
                        KioskPolicy.isKioskModeEnabled(activity) &&
                        !shouldSkipLockTaskPin(activity)
                    ) {
                        activity.startLockTask()
                        Log.i(TAG, "startLockTask() after Cast ended")
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "startLockTask after Cast failed", t)
                }
            }
        } else {
            KioskPolicy.forceBringToFrontSafely(
                context = context,
                preferImmediateOptions = true,
                ignoreTimedSuppress = true,
            )
        }
    }
}
