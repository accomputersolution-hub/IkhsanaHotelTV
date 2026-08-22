package `in`.pcncloud.hotel.integration

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import `in`.pcncloud.hotel.R

/**
 * Switches the TV to HDMI 2 via HDMI-CEC / vendor intents.
 *
 * Used by the Corporate flavor (L&T) instead of launching Onyx IPTV when the guest taps Live TV.
 */
object HdmiCecSwitcher {

    private const val TAG = "HdmiCecSwitcher"

    private const val ACTION_HDMI_SWITCH = "android.intent.action.HDMI_SWITCH"
    private const val EXTRA_HDMI_PORT = "hdmi_port"
    private const val HDMI_PORT_2 = 2

    private const val AML_HDMI_PACKAGE = "com.amlogic.tv.hdmi"
    private const val AML_HDMI_ACTIVITY = "com.amlogic.tv.hdmi.HdmiSwitchActivity"
    private const val EXTRA_PORT = "port"

    fun switchToHdmi2(context: Context) {
        val appContext = context.applicationContext

        try {
            val intent = Intent(ACTION_HDMI_SWITCH).apply {
                putExtra(EXTRA_HDMI_PORT, HDMI_PORT_2)
            }
            appContext.sendBroadcast(intent)
            Log.i(TAG, "Sent HDMI switch broadcast → port $HDMI_PORT_2")
        } catch (e: Exception) {
            Log.w(TAG, "Standard HDMI switch broadcast failed", e)
            try {
                val amlogicIntent = Intent(Intent.ACTION_VIEW).apply {
                    setClassName(AML_HDMI_PACKAGE, AML_HDMI_ACTIVITY)
                    putExtra(EXTRA_PORT, HDMI_PORT_2)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(amlogicIntent)
                Log.i(TAG, "Launched Amlogic HdmiSwitchActivity → port $HDMI_PORT_2")
            } catch (ex: Exception) {
                Log.e(TAG, "Amlogic HDMI switch fallback failed", ex)
                Toast.makeText(
                    appContext,
                    appContext.getString(R.string.hdmi_switch_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}
