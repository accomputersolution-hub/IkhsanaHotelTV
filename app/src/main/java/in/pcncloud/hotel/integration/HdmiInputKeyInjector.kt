package `in`.pcncloud.hotel.integration

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import `in`.pcncloud.hotel.R
import java.util.concurrent.Executors

/**
 * Corporate Live TV: inject KEYCODE_TV_INPUT_HDMI_2 (244) via
 * `input keyevent` so the box switches HDMI 2 the same way a hardware key would.
 *
 * Panasonic network control is unused — that API is blocked on this TV.
 */
object HdmiInputKeyInjector {

    private const val TAG = "HdmiInputKeyInjector"

    /** [KeyEvent.KEYCODE_TV_INPUT_HDMI_2] */
    const val KEYCODE_TV_INPUT_HDMI_2 = 244

    private val ioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "hdmi2-keyevent").apply { isDaemon = true }
    }

    /**
     * Call from the Live TV click listener. Shell exec runs off the main thread.
     */
    fun switchToHdmi2(context: Context) {
        val appContext = context.applicationContext
        ioExecutor.execute {
            val ok = injectHdmi2()
            if (!ok) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        appContext,
                        appContext.getString(R.string.hdmi_switch_failed),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    /**
     * `input keyevent 244` — [KEYCODE_TV_INPUT_HDMI_2].
     * Must not run on the UI thread.
     */
    fun injectHdmi2(): Boolean {
        return try {
            val proc = Runtime.getRuntime().exec(
                arrayOf("input", "keyevent", KEYCODE_TV_INPUT_HDMI_2.toString()),
            )
            val code = proc.waitFor()
            if (code == 0) {
                Log.i(TAG, "Injected KEYCODE_TV_INPUT_HDMI_2 ($KEYCODE_TV_INPUT_HDMI_2)")
                true
            } else {
                Log.w(TAG, "input keyevent $KEYCODE_TV_INPUT_HDMI_2 exited $code")
                false
            }
        } catch (t: Throwable) {
            Log.e(TAG, "input keyevent $KEYCODE_TV_INPUT_HDMI_2 failed", t)
            false
        }
    }
}
