package pozzo.apps.travelweather.map.viewmodel

import android.app.Application
import android.arch.lifecycle.MutableLiveData
import android.graphics.Color
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import pozzo.apps.tools.NetworkUtil
import pozzo.apps.travelweather.R
import pozzo.apps.travelweather.core.BaseViewModel
import pozzo.apps.travelweather.forecast.ForecastBusiness
import pozzo.apps.travelweather.forecast.ForecastHelper
import pozzo.apps.travelweather.forecast.model.Weather
import pozzo.apps.travelweather.location.LocationBusiness
import pozzo.apps.travelweather.location.LocationLiveData
import pozzo.apps.travelweather.map.helper.GeoCoderHelper
import java.io.IOException
import java.util.concurrent.Executors

class MapViewModel(application: Application) : BaseViewModel(application) {
    private val locationBusiness = LocationBusiness()
    private val forecastBusiness = ForecastBusiness()
    private val geoCoderHelper = GeoCoderHelper(application)


//    todo should I create a bigger pool of threads or leave it small?
//      executor = ThreadPoolExecutor(
//    7, 20, 1, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>(7), ThreadPoolExecutor.DiscardPolicy()
//    )
    private val routeExecutor = Executors.newSingleThreadExecutor()
    private val addWeatherExecutor = Executors.newSingleThreadExecutor()

    val startPosition = MutableLiveData<LatLng?>()
    val finishPosition = MutableLiveData<LatLng?>()
    val directionLine = MutableLiveData<PolylineOptions>()
    val weathers = MutableLiveData<List<Weather>>()
    val errorMessage = MutableLiveData<String>()

    val isShowingProgress = MutableLiveData<Boolean>()
    val isShowingTopBar = MutableLiveData<Boolean>()
    val shouldFinish = MutableLiveData<Boolean>()

    init {
        isShowingProgress.value = false
        isShowingTopBar.value = false
    }

    fun currentLocationFabClick() {
        finishPosition.postValue(null)
        setStartPosition(getCurrentLocation())
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

    /**
     * Update route.
     */
     fun updateRoute() {
        showProgress()

        routeExecutor.execute({
            val direction = locationBusiness.getDirections(startPosition.value, finishPosition.value)
            if (direction?.isEmpty() == false) {
                setDirectionLine(direction)
                addWeathers(filterDirectionToWeatherPoints(direction))
            }
            hideProgress()
        })
    }

    private fun setDirectionLine(direction: List<LatLng>) {
        val rectLine = PolylineOptions().width(7F).color(Color.BLUE)
        direction.forEach {
            rectLine.add(it)
        }
        this.directionLine.postValue(rectLine)
    }

    private fun filterDirectionToWeatherPoints(direction: List<LatLng>) : List<LatLng> {
        val filteredPoints = ArrayList<LatLng>()
        var lastForecast = direction.get(0)
        for (i in 1 until direction.size - 1) {
            val latLng = direction.get(i)
            if (i % 250 == 1 //Um mod para nao checar em todos os pontos, sao muitos
                    && ForecastHelper.isMinDistanceToForecast(latLng, lastForecast)) {
                lastForecast = latLng
                filteredPoints.add(latLng)
            }
        }
        return filteredPoints
    }

    private fun addWeathers(weatherPoints: List<LatLng>) {
        addWeatherExecutor.execute({
            val newWeathers = requestWeathersFor(weatherPoints)
            val currentWeathers = this.weathers.value
            if (currentWeathers?.isEmpty() == false)
                newWeathers.addAll(currentWeathers)
            this.weathers.postValue(newWeathers)
        })
    }

    private fun requestWeathersFor(weatherPoints: List<LatLng>) : ArrayList<Weather> {
        val weathers = ArrayList<Weather>()
        weatherPoints.forEach {
            weathers.add(forecastBusiness.from(it))
        }
        return weathers
    }

    fun setFinishPosition(finishPosition: LatLng?) {
        this.finishPosition.postValue(finishPosition)
        if (finishPosition != null)
            addWeathers(listOf(finishPosition))
    }

    fun setStartPosition(startPosition: LatLng?) {
        this.startPosition.postValue(startPosition)
        if (startPosition != null)
            addWeathers(listOf(startPosition))
    }

    fun back() {
        when {
            isShowingTopBar.value == true -> hideTopBar()
            finishPosition.value != null -> setFinishPosition(null)
            startPosition.value != null -> setStartPosition(null)
            else -> shouldFinish.postValue(true)
        }
    }

    fun toggleTopBar() = if (isShowingTopBar.value != true) displayTopBar() else hideTopBar()
    fun displayTopBar() = isShowingTopBar.postValue(true)
    fun hideTopBar() = isShowingTopBar.postValue(false)

    fun getRouteBounds() : LatLngBounds? {
        return if (isFullRouteSelected()) {
            LatLngBounds.builder()
                    .include(startPosition.value)
                    .include(finishPosition.value).build()
        } else {
            null
        }
    }

    fun isFullRouteSelected() : Boolean = startPosition.value != null && finishPosition.value != null

    fun addPoint(latLng: LatLng) {
        //todo what about create a polymorphsm on something like "currentSelection", so at least 1 if is avoided
        hideTopBar()
        if (!checkConnection()) {
            errorMessage.postValue(getString(R.string.warning_needsConnection))
        } else if (startPosition.value == null) {
            setStartPosition(latLng)
        } else {
            setFinishPosition(latLng)
        }
    }

    private fun checkConnection() : Boolean = NetworkUtil.isNetworkAvailable(getApplication())

    fun searchAddress(string: String) {
        try {
            val location = geoCoderHelper.getPositionFromFirst(string)
            addPoint(location)
        } catch (e: IOException) {
            errorMessage.postValue(getString(R.string.error_addressNotFound))
        }
    }

    fun dismissError() {
        errorMessage.value = null
    }

    /*
     * todo maybe there is a better place for it
     */
    private fun getString(id: Int) : String = getApplication<Application>().getString(id)

    //todo remove it when refacted enough
    fun showProgress() {
        isShowingProgress.postValue(true)
    }
    fun hideProgress() {
        isShowingProgress.postValue(false)
    }
}
