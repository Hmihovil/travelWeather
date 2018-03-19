package pozzo.apps.travelweather.map.viewmodel

import android.app.Application
import android.arch.lifecycle.MutableLiveData
import com.google.android.gms.maps.model.LatLng
import pozzo.apps.travelweather.core.BaseViewModel
import pozzo.apps.travelweather.location.LocationBusiness
import pozzo.apps.travelweather.location.LocationLiveData

class MapViewModel(application: Application) : BaseViewModel(application) {
    private val locationBusiness: LocationBusiness = LocationBusiness()

    val startPosition = MutableLiveData<LatLng?>()
    val finishPosition = MutableLiveData<LatLng?>()
    val isShowingProgress = MutableLiveData<Boolean>()

    init {
        isShowingProgress.value = false
    }

    fun currentLocationFabClick() {
        finishPosition.postValue(null)
        startPosition.postValue(getCurrentLocation())
    }

    fun getLiveLocation(): LocationLiveData {
        return LocationLiveData.get(getApplication())
    }

    fun getCurrentLocation(): LatLng? {
        try {
            val location = locationBusiness.getCurrentLocation(getApplication())
            if (location != null) {
                return LatLng(location.latitude, location.longitude)
            }
        } catch (e: SecurityException) {

        }

        return null
    }

    //todo remove it when refacted enough
    fun showProgress() {
        isShowingProgress.value = true
    }
    fun hideProgress() {
        isShowingProgress.value = false
    }
}