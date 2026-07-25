package com.example.ikhsanahoteltv

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import com.example.ikhsanahoteltv.config.HotelConfig
import com.example.ikhsanahoteltv.data.FirestorePaths
import com.example.ikhsanahoteltv.data.model.HotelBranding
import com.example.ikhsanahoteltv.data.repository.FirestoreRepository
import com.example.ikhsanahoteltv.ui.HotelViewModelFactory
import com.example.ikhsanahoteltv.ui.components.ServiceSuspendedScreen
import com.example.ikhsanahoteltv.ui.navigation.HotelNavGraph
import com.example.ikhsanahoteltv.ui.theme.IkhsanaHotelTVTheme
import com.example.ikhsanahoteltv.ui.theme.NavyDeep
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.ListenerRegistration

class MainActivity : ComponentActivity() {

    private lateinit var hotelConfig: HotelConfig
    private lateinit var repository: FirestoreRepository
    private val syncListeners = mutableListOf<ListenerRegistration>()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }

        hotelConfig = HotelConfig(applicationContext)
        val hotelId = hotelConfig.getHotelId()
        if (hotelId.isNullOrBlank()) {
            Log.i(TAG, "No paired hotelId — opening PairingActivity")
            startActivity(Intent(this, PairingActivity::class.java))
            finish()
            return
        }

        repository = FirestoreRepository(hotelConfig)
        val viewModelFactory = HotelViewModelFactory(repository, hotelConfig)

        Log.d(TAG, "TV Firestore sync starting → hotelId=$hotelId room=${hotelConfig.roomNumber}")
        Log.d(TAG, "Path Hotels/{hotelId} → ${FirestorePaths.hotelDocument(hotelId)}")
        Log.d(TAG, "Path Hotels/{hotelId}/Rooms → ${FirestorePaths.roomsCollection(hotelId)}")
        Log.d(TAG, "Path guest room → ${FirestorePaths.roomDocument(hotelId, hotelConfig.roomNumber)}")
        Log.d(TAG, "Path menu → ${FirestorePaths.menuCollection(hotelId)}")
        Log.d(TAG, "Path requests → ${FirestorePaths.requestsCollection(hotelId)}")
        Log.d(TAG, "Path alerts → ${FirestorePaths.alertsCollection(hotelId)}")

        // Bind diagnostic SnapshotListeners to Hotels/{saved_hotel_id}/…
        syncListeners += repository.attachSyncDiagnostics(
            onBranding = { branding ->
                Log.d(
                    TAG,
                    "MainActivity branding update → logo_url=${branding.logoUrl} " +
                        "bg_wallpaper=${branding.bgWallpaperUrl} name=${branding.hotelName} " +
                        "status=${branding.status}",
                )
            },
            onRooms = { rooms ->
                Log.d(
                    TAG,
                    "MainActivity Rooms update → count=${rooms.size} " +
                        rooms.joinToString { "${it.roomNumber}:${it.status}:${it.guestName}" },
                )
            },
        )

        setContent {
            val view = LocalView.current
            SideEffect {
                ViewCompat.setBackground(view, null)
            }

            var branding by remember { mutableStateOf(HotelBranding(hotelId = hotelId)) }

            LaunchedEffect(hotelId) {
                repository.observeHotelBranding().collect { next ->
                    branding = next
                    if (next.status.equals("inactive", ignoreCase = true)) {
                        Log.w(TAG, "Hotel $hotelId is inactive — blocking guest UI")
                    }
                }
            }

            IkhsanaHotelTVTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NavyDeep),
                ) {
                    if (branding.status.equals("inactive", ignoreCase = true)) {
                        ServiceSuspendedScreen(hotelName = branding.hotelName)
                    } else {
                        HotelNavGraph(viewModelFactory = viewModelFactory)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        syncListeners.forEach { registration ->
            registration.remove()
        }
        syncListeners.clear()
        Log.d(TAG, "MainActivity sync listeners removed")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
