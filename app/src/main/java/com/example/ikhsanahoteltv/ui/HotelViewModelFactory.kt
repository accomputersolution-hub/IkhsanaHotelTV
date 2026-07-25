package com.example.ikhsanahoteltv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ikhsanahoteltv.config.HotelConfig
import com.example.ikhsanahoteltv.data.repository.FirestoreRepository
import com.example.ikhsanahoteltv.ui.dining.DiningViewModel
import com.example.ikhsanahoteltv.ui.home.HomeViewModel
import com.example.ikhsanahoteltv.ui.services.ServicesViewModel

class HotelViewModelFactory(
    private val repository: FirestoreRepository,
    private val config: HotelConfig,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) ->
            HomeViewModel(repository) as T
        modelClass.isAssignableFrom(DiningViewModel::class.java) ->
            DiningViewModel(repository, config) as T
        modelClass.isAssignableFrom(ServicesViewModel::class.java) ->
            ServicesViewModel(repository) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
