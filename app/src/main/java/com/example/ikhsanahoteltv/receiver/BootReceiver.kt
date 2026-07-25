package com.example.ikhsanahoteltv.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Auto-launches the hotel TV app when the device powers on.
 * Requires RECEIVE_BOOT_COMPLETED permission (see AndroidManifest.xml).
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"

        private val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in BOOT_ACTIONS) return

        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

        if (launchIntent == null) {
            Log.e(TAG, "No launch intent found for package ${context.packageName}")
            return
        }

        try {
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app after boot (${intent?.action})", e)
        }
    }
}
