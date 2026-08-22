package `in`.pcncloud.hotel.integration

import android.content.Context
import android.content.Intent
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import `in`.pcncloud.hotel.R

/**
 * Switches the TV to HDMI 2 via the platform input framework (Amlogic / Droidlogic boxes).
 *
 * Used by the Corporate flavor (L&T) instead of launching Onyx IPTV when the guest taps Live TV.
 */
object HdmiCecSwitcher {

    private const val TAG = "HdmiCecSwitcher"

    /** Standard Amlogic HDMI 2 TvInput id (HW6). */
    private const val AML_HDMI2_INPUT_ID =
        "com.droidlogic.tvinput/.services.Hdmi2InputService/HW6"

    private const val AML_HDMI2_PASSTHROUGH_URI =
        "content://android.media.tv/passthrough/com.droidlogic.tvinput/.services.Hdmi2InputService/HW6"

    /** Vendor broadcast action seen on some Amlogic firmware builds. */
    private const val ACTION_HDMI_SWITCH = "com.droidlogic.action.HDMI_SWITCH"
    private const val EXTRA_HDMI_PORT = "hdmi_port"
    private const val HDMI_PORT_2 = 2

    /**
     * Attempts to switch the display to HDMI 2.
     * Tries Amlogic [TvInputManager] setup first, then passthrough VIEW, then a vendor broadcast.
     */
    fun switchToHdmi2(context: Context) {
        val appContext = context.applicationContext

        if (tryAmlogicSetupInputs(appContext)) {
            Log.i(TAG, "Switched to HDMI 2 via TvInputManager.ACTION_SETUP_INPUTS")
            return
        }
        if (tryPassthroughIntent(appContext)) {
            Log.i(TAG, "Switched to HDMI 2 via passthrough ACTION_VIEW")
            return
        }
        if (tryHdmiSwitchBroadcast(appContext)) {
            Log.i(TAG, "Sent HDMI 2 switch broadcast ($ACTION_HDMI_SWITCH)")
            return
        }

        Log.e(TAG, "All HDMI 2 switch methods failed")
        Toast.makeText(
            appContext,
            appContext.getString(R.string.hdmi_switch_failed),
            Toast.LENGTH_LONG,
        ).show()
    }

    /** Primary path for Amlogic Android TV boxes. */
    private fun tryAmlogicSetupInputs(context: Context): Boolean {
        return try {
            val intent = Intent(TvInputManager.ACTION_SETUP_INPUTS).apply {
                putExtra("from_tv_source", true)
                putExtra(TvInputInfo.EXTRA_INPUT_ID, AML_HDMI2_INPUT_ID)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Amlogic setup inputs failed", t)
            false
        }
    }

    /** Fallback: direct passthrough URI (works on some firmware when setup-inputs is blocked). */
    private fun tryPassthroughIntent(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(AML_HDMI2_PASSTHROUGH_URI)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Passthrough intent failed", t)
            false
        }
    }

    /** Last-resort vendor broadcast for boxes that expose HDMI routing only via broadcast. */
    private fun tryHdmiSwitchBroadcast(context: Context): Boolean {
        return try {
            val intent = Intent(ACTION_HDMI_SWITCH).apply {
                putExtra(EXTRA_HDMI_PORT, HDMI_PORT_2)
            }
            context.sendBroadcast(intent)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "HDMI switch broadcast failed", t)
            false
        }
    }
}
