package `in`.pcncloud.hotel.ui.entertainment

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.kiosk.CastHandoffMonitor
import `in`.pcncloud.hotel.kiosk.KioskLockTask
import `in`.pcncloud.hotel.kiosk.KioskPolicy
import android.app.Activity

/**
 * TV-friendly Screen Cast & Mirroring dialog.
 *
 * Split layout:
 * - Left: Android / built-in Chromecast instructions (no action button)
 * - Right: Apple AirPlay — launches AirScreen (`com.ionitech.airscreen`)
 *
 * Shown from Compose via [show] using an [android.app.Dialog]
 * (MainActivity is ComponentActivity, not a Fragment host).
 */
class ScreenCastMirroringDialog private constructor(
    context: Context,
) : Dialog(context, R.style.Theme_PcnCloudTv_CastDialog) {

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

        findViewById<Button>(R.id.btnCastClose)?.apply {
            setOnClickListener { dismiss() }
            // Default focus on Close — not AirPlay — so OK does not launch AirScreen.
            post { requestFocus() }
        }
        findViewById<Button>(R.id.btnStartAirPlay)?.setOnClickListener { launchAirScreen() }
        // Keep Cast arm after dismiss — guest usually closes the dialog then casts
        // from YouTube / Prime. Arm expires (~15 min) and restores Lock Task.
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            dismiss()
            return true
        }
        return super.onKeyDown(keyCode, event)
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
            // Prefer kiosk-safe launch (marks OTT session + Lock Task allowlist).
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

        /** Show the cast/mirroring dialog over the current Activity window. */
        @JvmStatic
        fun show(context: Context): ScreenCastMirroringDialog {
            // Unpin Lock Task now so YouTube / Prime Cast can paint when the phone
            // connects — allowlisting mediashell alone is not enough on many ATVs.
            val activity = context as? Activity
            if (activity != null) {
                CastHandoffMonitor.prepareForIncomingCast(activity)
            } else {
                CastHandoffMonitor.armForIncomingCast()
                CastHandoffMonitor.start(context.applicationContext)
                KioskLockTask.ensureChromecastAllowlisted(context)
            }
            return ScreenCastMirroringDialog(context).also { it.show() }
        }
    }
}
