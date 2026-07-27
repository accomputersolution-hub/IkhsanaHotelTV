package `in`.pcncloud.hotel.update

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import `in`.pcncloud.hotel.BuildConfig
import `in`.pcncloud.hotel.R
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import java.io.File

/**
 * Firebase Remote Config driven in-app updater.
 *
 * Remote Config keys:
 * - latest_version_code (Long)
 * - latest_version_name (String)
 * - apk_url (String)
 * - force_update (Boolean) — when true, dialog cannot be dismissed without updating
 */
object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val KEY_LATEST_VERSION_CODE = "latest_version_code"
    private const val KEY_LATEST_VERSION_NAME = "latest_version_name"
    private const val KEY_APK_URL = "apk_url"
    private const val KEY_FORCE_UPDATE = "force_update"

    @Volatile
    private var isChecking = false

    @Volatile
    private var shownForVersionCode = -1L

    @Volatile
    private var observedDownloadId: Long = -1L

    @Volatile
    private var receiverRegistered = false

    fun checkForUpdates(activity: Activity) {
        if (isChecking || activity.isFinishing || activity.isDestroyed) return
        isChecking = true

        val remoteConfig = FirebaseRemoteConfig.getInstance().apply {
            setConfigSettingsAsync(
                remoteConfigSettings {
                    minimumFetchIntervalInSeconds = 3600
                },
            )
            setDefaultsAsync(
                mapOf(
                    KEY_LATEST_VERSION_CODE to BuildConfig.VERSION_CODE.toLong(),
                    KEY_LATEST_VERSION_NAME to BuildConfig.VERSION_NAME,
                    KEY_APK_URL to "",
                    KEY_FORCE_UPDATE to false,
                ),
            )
        }

        remoteConfig.fetchAndActivate()
            .addOnCompleteListener(activity) { task ->
                isChecking = false
                if (!task.isSuccessful) {
                    Log.w(TAG, "Remote Config fetch failed", task.exception)
                    return@addOnCompleteListener
                }

                val latestCode = remoteConfig.getLong(KEY_LATEST_VERSION_CODE)
                val latestName = remoteConfig.getString(KEY_LATEST_VERSION_NAME).ifBlank {
                    "v$latestCode"
                }
                val apkUrl = remoteConfig.getString(KEY_APK_URL).trim()
                val forceUpdate = remoteConfig.getBoolean(KEY_FORCE_UPDATE)

                Log.d(
                    TAG,
                    "Remote Config → latestCode=$latestCode latestName=$latestName " +
                        "forceUpdate=$forceUpdate apkUrl=${apkUrl.take(80)}",
                )

                if (latestCode <= BuildConfig.VERSION_CODE || apkUrl.isBlank()) return@addOnCompleteListener
                // Soft updates: show once per version. Force updates: always re-prompt.
                if (!forceUpdate && shownForVersionCode == latestCode) return@addOnCompleteListener

                shownForVersionCode = latestCode
                showUpdateDialog(activity, latestName, apkUrl, forceUpdate)
            }
    }

    private fun showUpdateDialog(
        activity: Activity,
        latestVersionName: String,
        apkUrl: String,
        forceUpdate: Boolean,
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val builder = AlertDialog.Builder(activity)
            .setTitle(R.string.update_available_title)
            .setMessage(activity.getString(R.string.update_available_message, latestVersionName))
            .setCancelable(!forceUpdate)
            .setPositiveButton(R.string.update_button_now) { _, _ ->
                startDownload(activity, apkUrl, latestVersionName)
            }

        if (!forceUpdate) {
            builder.setNegativeButton(R.string.update_button_later, null)
        }

        val dialog = builder.create()
        if (forceUpdate) {
            dialog.setCanceledOnTouchOutside(false)
            dialog.setOnKeyListener { _, keyCode, _ ->
                // Block Back while force update is required; allow D-pad for Update.
                keyCode == KeyEvent.KEYCODE_BACK
            }
        }
        dialog.show()
    }

    private fun startDownload(activity: Activity, apkUrl: String, latestVersionName: String) {
        try {
            ensureReceiver(activity.applicationContext)

            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle(activity.getString(R.string.app_name))
                .setDescription(activity.getString(R.string.update_downloading))
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalFilesDir(
                    activity,
                    Environment.DIRECTORY_DOWNLOADS,
                    "pcncloud-$latestVersionName.apk",
                )

            val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            observedDownloadId = manager.enqueue(request)
            Toast.makeText(activity, R.string.update_downloading, Toast.LENGTH_SHORT).show()
            Log.i(TAG, "APK download enqueued id=$observedDownloadId url=$apkUrl")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue APK download", e)
            Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun ensureReceiver(appContext: Context) {
        if (receiverRegistered) return
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(
            appContext,
            downloadReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (downloadId == -1L || downloadId != observedDownloadId) return

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            manager.query(query)?.use { cursor ->
                if (!cursor.moveToFirst()) return

                val status = cursor.getInt(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS),
                )
                if (status != DownloadManager.STATUS_SUCCESSFUL) {
                    Log.w(TAG, "Download failed for id=$downloadId status=$status")
                    Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                    return
                }

                val localUri = cursor.getString(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI),
                ) ?: return

                Toast.makeText(context, R.string.update_download_complete, Toast.LENGTH_SHORT).show()
                openInstaller(context, Uri.parse(localUri))
            }
        }
    }

    private fun openInstaller(context: Context, localUri: Uri) {
        try {
            val file = File(requireNotNull(localUri.path) { "Download path missing" })
            val contentUri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                file,
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                Toast.makeText(
                    context,
                    R.string.update_install_permission_needed,
                    Toast.LENGTH_LONG,
                ).show()
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(settingsIntent)
                return
            }

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No activity found to install APK", e)
            Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open APK installer", e)
            Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show()
        }
    }
}
