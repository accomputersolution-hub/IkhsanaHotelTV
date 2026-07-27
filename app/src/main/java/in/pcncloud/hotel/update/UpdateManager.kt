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
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import `in`.pcncloud.hotel.BuildConfig
import `in`.pcncloud.hotel.R
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import org.json.JSONObject

/**
 * Force-update checker via a direct Google Drive JSON fetch (no Remote Config cache).
 *
 * Expected JSON shape:
 * ```
 * {
 *   "latest_version_code": 2,
 *   "latest_version_name": "1.1",
 *   "force_update": true,
 *   "apk_url": "https://..."
 * }
 * ```
 *
 * Configure [BuildConfig.UPDATE_JSON_URL] (Drive `uc?export=download&id=...`).
 */
object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 12_000

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isChecking = false

    @Volatile
    private var shownForVersionCode = -1

    @Volatile
    private var observedDownloadId: Long = -1L

    @Volatile
    private var receiverRegistered = false

    data class UpdateInfo(
        val latestVersionCode: Int,
        val latestVersionName: String,
        val forceUpdate: Boolean,
        val apkUrl: String,
    )

    fun checkForUpdates(activity: Activity) {
        if (isChecking || activity.isFinishing || activity.isDestroyed) return

        val jsonUrl = BuildConfig.UPDATE_JSON_URL.trim()
        if (jsonUrl.isBlank() || jsonUrl.contains("YOUR_JSON_FILE_ID")) {
            Log.w(TAG, "UPDATE_JSON_URL not configured — skip update check")
            return
        }

        isChecking = true
        ioExecutor.execute {
            try {
                val body = fetchJson(jsonUrl)
                val info = parseUpdateJson(body)
                mainHandler.post {
                    isChecking = false
                    if (activity.isFinishing || activity.isDestroyed) return@post
                    handleUpdateInfo(activity, info)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Update JSON fetch failed — continuing without update gate", e)
                mainHandler.post {
                    isChecking = false
                    // Offline / network error: do not block app launch.
                }
            }
        }
    }

    private fun handleUpdateInfo(activity: Activity, info: UpdateInfo) {
        val installedVc = BuildConfig.VERSION_CODE
        val onlineVc = info.latestVersionCode
        val debugMsg = "Installed VC: $installedVc | Online VC: $onlineVc"

        Log.i(
            TAG,
            "$debugMsg force=${info.forceUpdate} name=${info.latestVersionName} " +
                "apk=${info.apkUrl.take(80)}",
        )
        Toast.makeText(activity, debugMsg, Toast.LENGTH_LONG).show()

        if (onlineVc > installedVc && info.forceUpdate) {
            if (info.apkUrl.isBlank()) {
                Log.w(TAG, "Force update skipped — apk_url is blank")
                return
            }
            if (shownForVersionCode == onlineVc) return
            shownForVersionCode = onlineVc
            showForceUpdateDialog(
                activity = activity,
                latestVersionName = info.latestVersionName.ifBlank { "v$onlineVc" },
                apkUrl = info.apkUrl,
            )
        }
    }

    private fun fetchJson(jsonUrl: String): String {
        val connection = (URL(jsonUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json,text/plain,*/*")
            setRequestProperty("User-Agent", "PCNCloudHotelTV/${BuildConfig.VERSION_NAME}")
        }

        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            } ?: throw IllegalStateException("HTTP $code with empty body")

            val body = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                reader.readText()
            }

            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code: ${body.take(200)}")
            }
            // Drive sometimes returns an HTML interstitial for virus scan / confirm.
            if (body.trimStart().startsWith("<")) {
                throw IllegalStateException(
                    "Google Drive returned HTML instead of JSON — " +
                        "share the file as \"Anyone with the link\" and use a small JSON file.",
                )
            }
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun parseUpdateJson(raw: String): UpdateInfo {
        val json = JSONObject(raw)
        val code = when (val value = json.opt("latest_version_code")) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull() ?: 0
            else -> 0
        }
        val name = json.optString("latest_version_name", "").trim()
        val force = when (val value = json.opt("force_update")) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.trim().equals("true", ignoreCase = true) || value.trim() == "1"
            else -> false
        }
        val apkUrl = json.optString("apk_url", "").trim()
        return UpdateInfo(
            latestVersionCode = code,
            latestVersionName = name,
            forceUpdate = force,
            apkUrl = apkUrl,
        )
    }

    private fun showForceUpdateDialog(
        activity: Activity,
        latestVersionName: String,
        apkUrl: String,
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.update_available_title)
            .setMessage(activity.getString(R.string.update_available_message, latestVersionName))
            .setCancelable(false)
            .setPositiveButton(R.string.update_button_now) { _, _ ->
                startDownload(activity, apkUrl, latestVersionName)
            }
            .create()

        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnKeyListener { _, keyCode, _ ->
            // Block Back while force update is required; allow D-pad for Update.
            keyCode == KeyEvent.KEYCODE_BACK
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
