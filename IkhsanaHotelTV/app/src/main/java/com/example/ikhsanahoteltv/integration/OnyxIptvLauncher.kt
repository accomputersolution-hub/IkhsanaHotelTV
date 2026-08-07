package com.example.ikhsanahoteltv.integration

import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Launches GTPL DVB Player (Live TV) from the home screen card. */
object OnyxIptvLauncher {

    const val PACKAGE_NAME = "com.gtpl.DVBPlayer"

    fun launch(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
        if (intent != null) {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            Toast.makeText(
                context,
                "GTPL Live TV app is not installed",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
