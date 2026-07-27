package `in`.pcncloud.hotel.kiosk

import android.content.Context
import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings

/**
 * Syncs [KioskPolicy] with Firebase Remote Config key [REMOTE_KEY].
 *
 * Local SharedPreferences is the runtime source of truth (fast, works offline).
 * Remote Config refreshes prefs on launch unless a technician set an admin override.
 */
object KioskRemoteConfig {

    private const val TAG = "KioskRemoteConfig"

    /** Firebase Remote Config boolean — matches product key name. */
    const val REMOTE_KEY = "is_kiosk_mode_enabled"

    /**
     * Fetch & activate Remote Config, then apply [REMOTE_KEY] to [KioskPolicy]
     * when there is no sticky admin override.
     */
    fun syncOnLaunch(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        val appContext = context.applicationContext
        val remoteConfig = FirebaseRemoteConfig.getInstance()

        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings {
                // 0 = fetch on every sync (testing only; raise for production).
                minimumFetchIntervalInSeconds = 0
            },
        )
        remoteConfig.setDefaultsAsync(
            mapOf(REMOTE_KEY to true),
        )

        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Remote Config fetch failed — using SharedPreferences", task.exception)
                    onComplete?.invoke(KioskPolicy.isKioskModeEnabled(appContext))
                    return@addOnCompleteListener
                }

                if (KioskPolicy.hasAdminOverride(appContext)) {
                    val local = KioskPolicy.isKioskModeEnabled(appContext)
                    Log.i(TAG, "Admin override active — keeping local kiosk=$local")
                    onComplete?.invoke(local)
                    return@addOnCompleteListener
                }

                val enabled = remoteConfig.getBoolean(REMOTE_KEY)
                KioskPolicy.setKioskModeEnabled(
                    context = appContext,
                    enabled = enabled,
                    source = KioskPolicy.KioskSource.REMOTE_CONFIG,
                )
                Log.i(TAG, "Applied Remote Config $REMOTE_KEY=$enabled")
                onComplete?.invoke(enabled)
            }
    }

    /** Clear admin override and re-apply the cloud value (technician action). */
    fun followRemoteConfig(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        KioskPolicy.clearAdminOverride(context)
        syncOnLaunch(context, onComplete)
    }
}
