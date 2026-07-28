package `in`.pcncloud.hotel

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import `in`.pcncloud.hotel.kiosk.HomeKeyInterceptorService
import `in`.pcncloud.hotel.kiosk.KioskPolicy
import `in`.pcncloud.hotel.kiosk.KioskWatchdogService
import `in`.pcncloud.hotel.kiosk.MyDeviceAdminReceiver

/**
 * Process-wide lifecycle + crash bookkeeping for kiosk bring-to-front gating.
 */
class HotelTvApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        MyDeviceAdminReceiver.logProvisioningDiagnostics(this)
        HomeKeyInterceptorService.logStatus(this)
        KioskPolicy.onProcessStart(this)
        installCrashMarker()
        observeProcessLifecycle()

        if (KioskPolicy.isKioskModeEnabled(this)) {
            KioskWatchdogService.start(this)
        }
    }

    private fun installCrashMarker() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                KioskPolicy.markUnexpectedCrash(this)
            } catch (_: Exception) {
                // Best-effort only — never block the original crash path.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun observeProcessLifecycle() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    KioskPolicy.clearUserMinimized(this@HotelTvApplication)
                    // In-foreground session — if we die now without onStop, treat as unclean.
                    KioskPolicy.markSessionActive(this@HotelTvApplication)
                    Log.d(TAG, "Process ON_START")
                }

                override fun onStop(owner: LifecycleOwner) {
                    // Do not treat OTT viewing as "user minimized" — watchdog must stay quiet.
                    if (!KioskPolicy.isExternalAppActive(this@HotelTvApplication)) {
                        KioskPolicy.markUserMinimized(this@HotelTvApplication)
                    }
                    KioskPolicy.markCleanExit(this@HotelTvApplication)
                    Log.d(TAG, "Process ON_STOP (user may have minimized)")
                }
            },
        )
    }

    companion object {
        private const val TAG = "HotelTvApplication"
    }
}
