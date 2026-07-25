package com.example.ikhsanahoteltv

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.ikhsanahoteltv.config.HotelConfig
import com.example.ikhsanahoteltv.data.FirestorePaths
import com.example.ikhsanahoteltv.data.repository.FirestoreRepository
import com.example.ikhsanahoteltv.ui.HotelViewModelFactory
import com.example.ikhsanahoteltv.ui.navigation.HotelNavGraph
import com.example.ikhsanahoteltv.ui.theme.IkhsanaHotelTVTheme
import com.example.ikhsanahoteltv.ui.theme.NavyDeep
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }

        val hotelConfig = HotelConfig(applicationContext)
        val repository = FirestoreRepository(hotelConfig)
        val viewModelFactory = HotelViewModelFactory(repository, hotelConfig)

        Log.i(
            TAG,
            "TV device config → hotelId=${hotelConfig.hotelId}, room=${hotelConfig.roomNumber}",
        )
        Log.i(TAG, "Listening guest at: ${FirestorePaths.roomDocument(hotelConfig.hotelId, hotelConfig.roomNumber)}")
        Log.i(TAG, "Listening alerts at: ${FirestorePaths.alertsCollection(hotelConfig.hotelId)} (roomNumber=${hotelConfig.roomNumber})")

        setContent {
            IkhsanaHotelTVTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NavyDeep),
                ) {
                    HotelNavGraph(viewModelFactory = viewModelFactory)
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
