package `in`.pcncloud.hotel.alert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import `in`.pcncloud.hotel.MainActivity
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.config.HotelConfig
import `in`.pcncloud.hotel.config.IntroVideoCache
import `in`.pcncloud.hotel.data.repository.FirestoreRepository
import `in`.pcncloud.hotel.ui.home.BrandAssets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that draws Firestore/RTDB guest alerts via
 * [WindowManager] + [Settings.canDrawOverlays] so messages appear over
 * YouTube / Live TV on Android TV (API 28+).
 *
 * Window is focusable so the Close button can take D-pad / remote focus.
 * [FLAG_NOT_TOUCH_MODAL] still lets touches outside the card pass through.
 * Do **not** use [FLAG_NOT_FOCUSABLE] here — that blocks all key events, so
 * the remote can never highlight or press Close.
 */
class AlertOverlayService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)

    private var windowManager: WindowManager? = null
    private var popupView: View? = null
    private var showingAlertId: String? = null
    private val autoDismissRunnable = Runnable { dismissOverlay(markRead = true) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = applicationContext.getSystemService(WINDOW_SERVICE) as WindowManager
        startAsForeground()
        Log.i(TAG, "onCreate — overlay service ready")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                val alertId = intent.getStringExtra(EXTRA_ALERT_ID).orEmpty()
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Message" }
                val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
                val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 0L)
                mainHandler.post {
                    showOverlay(
                        alertId = alertId,
                        title = title,
                        message = message,
                        durationMs = durationMs,
                    )
                }
            }
            ACTION_DISMISS -> {
                mainHandler.post { dismissOverlay(markRead = true) }
            }
            ACTION_STOP -> {
                mainHandler.post {
                    dismissOverlay(markRead = false)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            else -> Log.d(TAG, "onStartCommand ignored action=${intent?.action}")
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(autoDismissRunnable)
        mainHandler.post {
            removePopupViewSafe()
        }
        serviceJob.cancel()
        Log.i(TAG, "onDestroy")
        super.onDestroy()
    }

    private fun showOverlay(
        alertId: String,
        title: String,
        message: String,
        durationMs: Long,
    ) {
        if (!Settings.canDrawOverlays(applicationContext)) {
            Log.w(TAG, "showOverlay aborted — SYSTEM_ALERT_WINDOW not granted")
            stopSelf()
            return
        }

        // Replace any existing popup (newest alert wins).
        removePopupViewSafe()
        mainHandler.removeCallbacks(autoDismissRunnable)

        val view = LayoutInflater.from(applicationContext)
            .inflate(R.layout.overlay_alert_popup, null)
        view.findViewById<TextView>(R.id.alert_overlay_title).text = title
        view.findViewById<TextView>(R.id.alert_overlay_message).text = message

        val closeButton = view.findViewById<Button>(R.id.alert_overlay_close)
        closeButton.isFocusable = true
        closeButton.isFocusableInTouchMode = true
        closeButton.setOnClickListener {
            dismissOverlay(markRead = true)
        }
        // OK / Center / Enter / Back on the focused Close button.
        closeButton.setOnKeyListener { _, keyCode, event ->
            if (event.action != android.view.KeyEvent.ACTION_UP) return@setOnKeyListener false
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER,
                android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
                android.view.KeyEvent.KEYCODE_BACK,
                -> {
                    dismissOverlay(markRead = true)
                    true
                }
                else -> false
            }
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // Focusable window (no FLAG_NOT_FOCUSABLE) so D-pad can land on Close.
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            format = PixelFormat.TRANSLUCENT
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
        }

        try {
            windowManager?.addView(view, params)
            popupView = view
            showingAlertId = alertId
            // Defer focus until the view is attached / laid out.
            closeButton.post {
                runCatching { closeButton.requestFocus() }
                    .onFailure { Log.w(TAG, "Close requestFocus failed", it) }
            }
            Log.i(TAG, "Overlay attached alertId=$alertId title=$title (D-pad focus on Close)")

            // Optional admin-configured timeout only — Close is the primary dismiss path.
            if (durationMs > 0L) {
                mainHandler.postDelayed(autoDismissRunnable, durationMs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "windowManager.addView failed", e)
            popupView = null
            showingAlertId = null
            stopSelf()
        }
    }

    private fun dismissOverlay(markRead: Boolean) {
        mainHandler.removeCallbacks(autoDismissRunnable)
        val alertId = showingAlertId
        removePopupViewSafe()
        showingAlertId = null

        if (markRead && !alertId.isNullOrBlank()) {
            serviceScope.launch {
                runCatching {
                    val config = HotelConfig(applicationContext)
                    val repo = FirestoreRepository(config, IntroVideoCache(applicationContext))
                    repo.markAlertRead(alertId)
                }.onFailure { Log.w(TAG, "markAlertRead failed for $alertId", it) }
            }
        }

        sendBroadcast(
            Intent(ACTION_ALERT_OVERLAY_DISMISSED).apply {
                setPackage(packageName)
                putExtra(EXTRA_ALERT_ID, alertId)
            },
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun removePopupViewSafe() {
        val view = popupView ?: return
        popupView = null
        try {
            if (view.isAttachedToWindow) {
                windowManager?.removeView(view)
            }
        } catch (e: Exception) {
            Log.w(TAG, "removeView failed", e)
            runCatching { windowManager?.removeViewImmediate(view) }
        }
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.alert_overlay_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.alert_overlay_channel_desc)
                    setShowBadge(false)
                },
            )
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.alert_overlay_notification_title))
            .setContentText(getString(R.string.alert_overlay_notification_text))
            .setSmallIcon(BrandAssets.logoRes)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "AlertOverlayService"
        private const val CHANNEL_ID = "hotel_tv_alert_overlay"
        private const val NOTIFICATION_ID = 1002

        const val ACTION_SHOW = "in.pcncloud.hotel.alert.SHOW"
        const val ACTION_DISMISS = "in.pcncloud.hotel.alert.DISMISS"
        const val ACTION_STOP = "in.pcncloud.hotel.alert.STOP"
        const val ACTION_ALERT_OVERLAY_DISMISSED = "in.pcncloud.hotel.alert.OVERLAY_DISMISSED"

        const val EXTRA_ALERT_ID = "alert_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_DURATION_MS = "duration_ms"

        /**
         * Ask the overlay service to display [title]/[message] over other apps.
         * No-ops (with log) when overlay permission is missing.
         */
        fun show(
            context: Context,
            alertId: String,
            title: String,
            message: String,
            durationMs: Long = 0L,
        ) {
            val app = context.applicationContext
            if (!Settings.canDrawOverlays(app)) {
                Log.w(TAG, "show() skipped — SYSTEM_ALERT_WINDOW not granted")
                return
            }
            val intent = Intent(app, AlertOverlayService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_ALERT_ID, alertId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_DURATION_MS, durationMs)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(intent)
                } else {
                    app.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "startForegroundService failed", e)
            }
        }

        fun dismiss(context: Context) {
            val app = context.applicationContext
            runCatching {
                app.startService(
                    Intent(app, AlertOverlayService::class.java).setAction(ACTION_DISMISS),
                )
            }
        }

        /**
         * Tear down overlay WindowManager views and stop the service.
         * Used by clean kiosk exit so SYSTEM_ALERT_WINDOW popups cannot glitch on.
         */
        fun stopFully(context: Context) {
            val app = context.applicationContext
            runCatching {
                app.startService(
                    Intent(app, AlertOverlayService::class.java).setAction(ACTION_STOP),
                )
            }.onFailure { Log.w(TAG, "ACTION_STOP startService failed", it) }
            runCatching {
                app.stopService(Intent(app, AlertOverlayService::class.java))
            }.onFailure { Log.w(TAG, "stopService AlertOverlayService failed", it) }
            Log.i(TAG, "stopFully — dismiss + stop requested")
        }
    }
}
