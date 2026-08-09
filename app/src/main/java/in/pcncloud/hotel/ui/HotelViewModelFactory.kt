package `in`.pcncloud.hotel.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import `in`.pcncloud.hotel.config.HotelConfig
import `in`.pcncloud.hotel.data.repository.FirestoreRepository
import `in`.pcncloud.hotel.ui.agenda.AgendaViewModel
import `in`.pcncloud.hotel.ui.dining.DiningViewModel
import `in`.pcncloud.hotel.ui.home.HomeViewModel
import `in`.pcncloud.hotel.ui.services.CorporateServicesViewModel
import `in`.pcncloud.hotel.ui.services.ServicesViewModel

class HotelViewModelFactory(
    private val repository: FirestoreRepository,
    private val config: HotelConfig,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) ->
            HomeViewModel(repository, config) as T
        modelClass.isAssignableFrom(DiningViewModel::class.java) ->
            DiningViewModel(repository, config) as T
        modelClass.isAssignableFrom(ServicesViewModel::class.java) ->
            ServicesViewModel(repository) as T
        modelClass.isAssignableFrom(CorporateServicesViewModel::class.java) ->
            CorporateServicesViewModel(repository) as T
        modelClass.isAssignableFrom(AgendaViewModel::class.java) ->
            AgendaViewModel(repository) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
