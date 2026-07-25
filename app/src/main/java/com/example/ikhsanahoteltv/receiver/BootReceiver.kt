package com.example.ikhsanahoteltv.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ikhsanahoteltv.MainActivity

/**
 * Auto-launches the hotel TV app when the device powers on.
 * Requires RECEIVE_BOOT_COMPLETED permission (see AndroidManifest.xml).
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_REBOOT,
        )
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in BOOT_ACTIONS) return

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(launchIntent)
    }
}
