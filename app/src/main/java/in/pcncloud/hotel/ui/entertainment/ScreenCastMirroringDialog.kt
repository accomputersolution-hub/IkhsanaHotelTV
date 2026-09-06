package `in`.pcncloud.hotel.ui.entertainment

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.kiosk.CastHandoffMonitor
import `in`.pcncloud.hotel.kiosk.KioskLockTask
import `in`.pcncloud.hotel.kiosk.KioskPolicy

/**
 * TV-friendly Screen Cast & Mirroring dialog.
 *
 * Left: Android Cast — explicit **Enable Android Cast** unpins Lock Task / screen pin
 * so YouTube / Prime can paint (required when the TV is not Device Owner).
 * Right: Apple AirPlay — launches AirScreen.
 */
class ScreenCastMirroringDialog private constructor(
    context: Context,
) : Dialog(context, R.style.Theme_PcnCloudTv_CastDialog) {

    private var statusView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_screen_cast_mirroring)
        setCancelable(true)
        setCanceledOnTouchOutside(true)

        window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
            setBackgroundDrawableResource(android.R.color.transparent)
            clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }

        statusView = findViewById(R.id.txtAndroidCastStatus)

        findViewById<Button>(R.id.btnCastClose)?.setOnClickListener { dismiss() }

        findViewById<Button>(R.id.btnEnableAndroidCast)?.apply {
            setOnClickListener { enableAndroidCast() }
            // Default focus here — guest must unpin before phone Cast works on non-DO.
            post { requestFocus() }
        }

        findViewById<Button>(R.id.btnStartAirPlay)?.setOnClickListener { launchAirScreen() }

        // If dialog was opened already armed, show ready state.
        if (CastHandoffMonitor.isArmedForIncomingCast() ||
            CastHandoffMonitor.isYieldingForCast()
        ) {
            showAndroidCastReady()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            dismiss()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /** Unpin kiosk so phone Chromecast / Cast can take the TV screen. */
    private fun enableAndroidCast() {
        val activity = context as? Activity
        if (activity != null) {
            CastHandoffMonitor.prepareForIncomingCast(activity)
            // Extra stop in case prepare raced with a reclaim re-pin.
            runCatching { activity.stopLockTask() }
        } else {
            CastHandoffMonitor.armForIncomingCast()
            CastHandoffMonitor.start(context.applicationContext)
        }

        showAndroidCastReady()
        Toast.makeText(
            context.applicationContext,
            context.getString(R.string.cast_android_ready),
            Toast.LENGTH_LONG,
        ).show()
        Log.i(TAG, "Enable Android Cast — pin removed / armed for phone Cast")
    }

    private fun showAndroidCastReady() {
        statusView?.apply {
            text = context.getString(R.string.cast_android_ready_short)
            visibility = View.VISIBLE
        }
        findViewById<TextView>(R.id.txtCastSubtitle)?.text =
            context.getString(R.string.cast_android_ready_short)
    }

    private fun launchAirScreen() {
        val packageName = KioskLockTask.AIRSCREEN_PACKAGE
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            Toast.makeText(
                context.applicationContext,
                context.getString(R.string.cast_airplay_not_installed),
                Toast.LENGTH_SHORT,
            ).show()
            Log.w(TAG, "AirScreen not installed → $packageName")
            return
        }

        try {
            val launched = KioskLockTask.launchAllowlistedPackage(context, packageName)
            if (!launched) {
                KioskPolicy.markOttLaunched(context, packageName)
                KioskLockTask.applyLockTaskForLaunch(context, packageName)
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            }
            Log.i(TAG, "Launched AirScreen for AirPlay → $packageName")
            dismiss()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to launch AirScreen", t)
            KioskPolicy.clearOttLaunchState(context)
            Toast.makeText(
                context.applicationContext,
                context.getString(R.string.cast_airplay_not_installed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    companion object {
        private const val TAG = "ScreenCastDialog"

        @JvmStatic
        fun show(context: Context): ScreenCastMirroringDialog {
            // Pre-arm when dialog opens so Close → phone Cast also works.
            val activity = context as? Activity
            if (activity != null) {
                CastHandoffMonitor.prepareForIncomingCast(activity)
            } else {
                CastHandoffMonitor.armForIncomingCast()
                CastHandoffMonitor.start(context.applicationContext)
            }
            return ScreenCastMirroringDialog(context).also { it.show() }
        }
    }
}
