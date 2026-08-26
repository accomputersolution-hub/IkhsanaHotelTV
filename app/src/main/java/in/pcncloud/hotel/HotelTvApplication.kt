package `in`.pcncloud.hotel

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import `in`.pcncloud.hotel.kiosk.KioskPolicy
import `in`.pcncloud.hotel.kiosk.MyDeviceAdminReceiver
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Process-wide lifecycle + crash bookkeeping for kiosk bring-to-front gating.
 *
 * Do **not** start [KioskWatchdogService] here — Android 12+ / 16 blocks
 * foreground-service starts from [Application.onCreate] (Background FGS
 * restrictions). Watchdog is started from [MainActivity.onResume] once UI is visible.
 */
class HotelTvApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        try {
            MyDeviceAdminReceiver.logProvisioningDiagnostics(this)
        } catch (e: Exception) {
            Log.w(TAG, "logProvisioningDiagnostics failed during Application.onCreate", e)
        }
        try {
            // Corporate Device Owner: pin Tailscale as Always-On VPN (no-op on hotel / non-DO).
            MyDeviceAdminReceiver.ensureAlwaysOnTailscaleVpn(this)
        } catch (e: Exception) {
            Log.w(TAG, "ensureAlwaysOnTailscaleVpn failed during Application.onCreate", e)
        }
        try {
            KioskPolicy.onProcessStart(this)
        } catch (e: Exception) {
            // Direct Boot / CE storage must never crash the process.
            Log.w(TAG, "KioskPolicy.onProcessStart failed during Application.onCreate", e)
        }
        installCrashMarker()
        observeProcessLifecycle()
    }

    /**
     * App-wide Coil loader.
     *
     * ImgBB (`i.ibb.co`) and Wikimedia 403 a bare OkHttp / custom app User-Agent,
     * so the TV kept the local gold flower even after Super Admin saved a logo URL.
     * Send a Chrome-on-Android-TV UA (and an ibb.co Referer) so those CDNs serve
     * the actual image bytes.
     */
    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val original = chain.request()
                val host = original.url.host.lowercase()
                val builder = original.newBuilder()
                    .header("User-Agent", CHROME_ANDROID_TV_UA)
                    .header(
                        "Accept",
                        "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
                    )
                    .header("Accept-Language", "en-US,en;q=0.9")
                if (host == "i.ibb.co" || host == "ibb.co" || host.endsWith(".ibb.co")) {
                    builder.header("Referer", "https://ibb.co/")
                }
                chain.proceed(builder.build())
            }
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (!response.isSuccessful) {
                    Log.w(
                        TAG,
                        "Image HTTP ${response.code} ${chain.request().url}",
                    )
                }
                response
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .components { add(SvgDecoder.Factory()) }
            .crossfade(true)
            .build()
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
                    // Under kiosk, brief Home task-switches must NOT stick as "user minimized"
                    // (that raced with reclaim skips and let Process ON_STOP escape).
                    if (KioskPolicy.isKioskModeEnabled(this@HotelTvApplication)) {
                        KioskPolicy.markCleanExit(this@HotelTvApplication)
                        Log.d(TAG, "Process ON_STOP — kiosk ON, skip markUserMinimized")
                        return
                    }
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
        private const val CHROME_ANDROID_TV_UA =
            "Mozilla/5.0 (Linux; Android 13; SHIELD Android TV) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/122.0.6261.64 Safari/537.36"
    }
}
