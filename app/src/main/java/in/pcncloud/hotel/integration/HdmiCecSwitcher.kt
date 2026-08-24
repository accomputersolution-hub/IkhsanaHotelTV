package `in`.pcncloud.hotel.integration

import android.app.Instrumentation
import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.widget.Toast
import `in`.pcncloud.hotel.R
import java.util.concurrent.Executors

/**
 * Corporate Live TV: switch the Samsung TV HDMI input the same way the
 * physical remote Home button does (HDMI-CEC), instead of a custom broadcast
 * (`android.intent.action.HDMI_SWITCH`) which boxes ignore.
 *
 * Injects [KeyEvent.KEYCODE_WAKEUP] then [KeyEvent.KEYCODE_HOME] so HDMI-CEC
 * sees the same path as a physical remote Home press.
 */
object HdmiCecSwitcher {

    private const val TAG = "HdmiCecSwitcher"

    private val injectExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "hdmi-cec-key").apply { isDaemon = true }
    }

    fun switchToHdmi2(context: Context) {
        val appContext = context.applicationContext
        injectExecutor.execute {
            val wakeOk = injectKey(KeyEvent.KEYCODE_WAKEUP)
            val homeOk = injectKey(KeyEvent.KEYCODE_HOME)
            if (!wakeOk && !homeOk) {
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
     * Tries system-level injection so HDMI-CEC sees the same path as a
     * physical remote key. Activity.dispatchKeyEvent is not used — that only
     * reaches this process and would be swallowed by kiosk HOME handling.
     */
    private fun injectKey(keyCode: Int): Boolean {
        if (injectViaInstrumentation(keyCode)) return true
        if (injectViaInputManager(keyCode)) return true
        if (injectViaShell(keyCode)) return true
        Log.w(TAG, "All injectors failed for keyCode=$keyCode")
        return false
    }

    /** Must run off the main thread. */
    private fun injectViaInstrumentation(keyCode: Int): Boolean {
        return try {
            Instrumentation().sendKeyDownUpSync(keyCode)
            Log.i(TAG, "Injected keyCode=$keyCode via Instrumentation")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Instrumentation inject failed keyCode=$keyCode", t)
            false
        }
    }

    /**
     * Hidden [InputManager.injectInputEvent] — works on many Android TV boxes
     * when the app is foreground. Source is HDMI so CEC treats it like a remote.
     */
    private fun injectViaInputManager(keyCode: Int): Boolean {
        return try {
            val im = InputManager::class.java
                .getDeclaredMethod("getInstance")
                .invoke(null) as InputManager
            val inject = InputManager::class.java.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType,
            )
            val now = SystemClock.uptimeMillis()
            val down = buildRemoteKeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode)
            val up = buildRemoteKeyEvent(now, now, KeyEvent.ACTION_UP, keyCode)
            val mode = 0 // INJECT_INPUT_EVENT_MODE_ASYNC
            val downOk = inject.invoke(im, down, mode) as Boolean
            val upOk = inject.invoke(im, up, mode) as Boolean
            if (downOk && upOk) {
                Log.i(TAG, "Injected keyCode=$keyCode via InputManager (SOURCE_HDMI)")
                true
            } else {
                Log.w(TAG, "InputManager inject returned down=$downOk up=$upOk keyCode=$keyCode")
                false
            }
        } catch (t: Throwable) {
            Log.w(TAG, "InputManager inject failed keyCode=$keyCode", t)
            false
        }
    }

    private fun buildRemoteKeyEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        keyCode: Int,
    ): KeyEvent {
        return KeyEvent(
            downTime,
            eventTime,
            action,
            keyCode,
            0,
            0,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            0,
            KeyEvent.FLAG_FROM_SYSTEM,
            InputDevice.SOURCE_HDMI,
        )
    }

    /** Shell `input keyevent` — last resort on unlocked / debug boxes. */
    private fun injectViaShell(keyCode: Int): Boolean {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("input", "keyevent", keyCode.toString()))
            val code = proc.waitFor()
            if (code == 0) {
                Log.i(TAG, "Injected keyCode=$keyCode via input keyevent")
                true
            } else {
                Log.w(TAG, "input keyevent exited $code for keyCode=$keyCode")
                false
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Shell input keyevent failed keyCode=$keyCode", t)
            false
        }
    }
}
