package pozzo.apps.travelweather.map.viewmodel

import android.app.Application
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.MutableLiveData
import android.graphics.Color
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.analytics.FirebaseAnalytics
import pozzo.apps.tools.NetworkUtil
import pozzo.apps.travelweather.App
import pozzo.apps.travelweather.analytics.MapAnalytics
import pozzo.apps.travelweather.common.business.PreferencesBusiness
import pozzo.apps.travelweather.core.BaseViewModel
import pozzo.apps.travelweather.core.Error
import pozzo.apps.travelweather.core.Warning
import pozzo.apps.travelweather.core.action.ActionRequest
import pozzo.apps.travelweather.core.action.ClearActionRequest
import pozzo.apps.travelweather.core.action.RateMeActionRequest
import pozzo.apps.travelweather.core.userinputrequest.LocationPermissionRequest
import pozzo.apps.travelweather.core.userinputrequest.PermissionRequest
import pozzo.apps.travelweather.direction.DirectionWeatherFilter
import pozzo.apps.travelweather.forecast.ForecastBusiness
import pozzo.apps.travelweather.forecast.model.Route
import pozzo.apps.travelweather.forecast.model.Weather
import pozzo.apps.travelweather.forecast.model.point.FinishPoint
import pozzo.apps.travelweather.forecast.model.point.MapPoint
import pozzo.apps.travelweather.forecast.model.point.StartPoint
import pozzo.apps.travelweather.forecast.model.point.WeatherPoint
import pozzo.apps.travelweather.location.CurrentLocationRequester
import pozzo.apps.travelweather.location.LocationBusiness
import pozzo.apps.travelweather.location.helper.GeoCoderHelper
import pozzo.apps.travelweather.map.overlay.MapTutorial
import pozzo.apps.travelweather.map.overlay.Tutorial
import java.io.IOException
import java.util.concurrent.Executors

//todo I need to break it apart, this is crazy big!
class MapViewModel(application: Application) : BaseViewModel(application) {
    private val locationBusiness = LocationBusiness()
    private val forecastBusiness = ForecastBusiness()
    private val preferencesBusiness = PreferencesBusiness(getApplication())
    private val geoCoderHelper = GeoCoderHelper(application)
    private val mapAnalytics = MapAnalytics(FirebaseAnalytics.getInstance(application))
    private val directionWeatherFilter = DirectionWeatherFilter()
    private var currentLocationRequester = CurrentLocationRequester(getApplication(), CurrentLocationCallback())

    private val routeExecutor = Executors.newSingleThreadExecutor()

    private var dragStart = 0L
    private val mapTutorial = MapTutorial(getApplication())

    private var route = Route()
    val routeData = MutableLiveData<Route>()

    val error = MutableLiveData<Error>()
    val warning = MutableLiveData<Warning>()
    val actionRequest = MutableLiveData<ActionRequest>()
    val permissionRequest = MutableLiveData<PermissionRequest>()
    val overlay = MutableLiveData<Tutorial>()

    val isShowingProgress = MutableLiveData<Boolean>()
    val isShowingTopBar = MutableLiveData<Boolean>()
    val shouldFinish = MutableLiveData<Boolean>()

    init {
        isShowingProgress.value = false
        isShowingTopBar.value = false
        shouldFinish.value = false
        routeData.value = route
        playIfNotPlayed(Tutorial.FULL_TUTORIAL)
    }

    private fun setRoute(route: Route) {
      this.route = route
      routeData.postValue(route)
    }

    fun onMapReady(lifecycleOwner: LifecycleOwner) {
        if (route.startPoint == null) {
            currentLocationRequester.requestCurrentLocationRequestingPermission(lifecycleOwner)
        }
    }

    fun setStartAsCurrentLocationRequestedByUser(lifecycleOwner: LifecycleOwner) {
        currentLocationRequester.requestCurrentLocationRequestingPermission(lifecycleOwner)
        mapAnalytics.sendFirebaseUserRequestedCurrentLocationEvent()
    }

    fun onPermissionGranted(permissionRequest: PermissionRequest, lifecycleOwner: LifecycleOwner) {
        permissionRequest.granted(lifecycleOwner)
        this.permissionRequest.value = null
    }

    fun onPermissionDenied(permissionRequest: PermissionRequest) {
        permissionRequest.denied()
        this.permissionRequest.value = null
    }

    fun warn(warning: Warning) {
        this.warning.postValue(warning)
    }

    private fun postError(error: Error) {
        this.error.postValue(error)
        mapAnalytics.sendErrorMessage(error)
    }

    private fun showProgress() {
        isShowingProgress.postValue(true)
    }

    private fun hideProgress() {
        isShowingProgress.postValue(false)
    }

    private fun requestWeathersFor(weatherPoints: List<LatLng>) : List<Weather> {
        val weathers = try {
            forecastBusiness.from(weatherPoints)
        } catch (e: IOException) {
            handleConnectionError(e)
            emptyList<Weather>()
        }

        if (weathers.size != weatherPoints.size) mapAnalytics.weatherMiss(weatherPoints.size, weathers.size)
        return weathers
    }

    private fun parseWeatherIntoMapPoints(weathers: List<Weather>) : ArrayList<MapPoint> {
        val mapPoints = ArrayList<MapPoint>()

        weathers.forEach {
            parseWeatherIntoMapPoint(it)?.let { mapPoints.add(it) }
        }

        return mapPoints
    }

    private fun parseWeatherIntoMapPoint(weather: Weather) : MapPoint? =
        if (weather.address != null) WeatherPoint(weather) else null

    fun setFinishPosition(finishPosition: LatLng?) {
        if (finishPosition != null) {
            createFinishPoint(finishPosition)
            playIfNotPlayed(Tutorial.ROUTE_CREATED_TUTORIAL)
        } else {
            setRoute(Route(startPoint = route.startPoint))
        }
    }

    private fun createFinishPoint(finishPosition: LatLng) {
        val startPoint = route.startPoint!!
        val mapPoint = FinishPoint(getApplication<App>().resources, finishPosition)
        setRoute(Route(startPoint = startPoint, finishPoint = mapPoint))
        updateRoute(startPoint.position, finishPosition)
    }

    private fun updateRoute(startPosition: LatLng, finishPosition: LatLng) {
        showProgress()

        routeExecutor.execute {
            try {
                val direction = locationBusiness.getDirections(startPosition, finishPosition)
                if (direction?.isEmpty() == false) {
                    val directionLine = setDirectionLine(direction)
                    val mapPoints = toMapPoints(directionWeatherFilter.getWeatherPointsLocations(direction))
                    setRoute(Route(route, polyline = directionLine, mapPoints = mapPoints))
                } else {
                    postError(Error.CANT_FIND_ROUTE)
                }
            } catch (e: IOException) {
                handleConnectionError(e)
            }
            hideProgress()
        }
    }

    private fun setDirectionLine(direction: List<LatLng>) : PolylineOptions =
            PolylineOptions().width(7F).color(Color.BLUE).addAll(direction)

    private fun toMapPoints(weatherPoints: List<LatLng>) : List<MapPoint> {
        val weathers = requestWeathersFor(weatherPoints)
        return parseWeatherIntoMapPoints(weathers)
    }

    fun setStartPosition(startPosition: LatLng?) {
        if (startPosition != null) {
            createStartPoint(startPosition)
        } else {
            setRoute(Route())
        }
    }

    private fun createStartPoint(startPosition: LatLng) {
        val finishPoint = route.finishPoint
        val mapPoint = StartPoint(getApplication<App>().resources, startPosition)
        val route = Route(startPoint = mapPoint, finishPoint = finishPoint)
        setRoute(route)
        finishPoint?.let { updateRoute(startPosition, it.position) }
    }

    fun back() {
        when {
            isShowingTopBar.value == true -> hideTopBar()
            route.finishPoint != null -> setFinishPosition(null)
            route.startPoint != null -> setStartPosition(null)
            else -> shouldFinish.postValue(true)
        }
    }

    fun toggleTopBar(text: String) {
        if (isShowingTopBar.value != true) {
            displayTopBar()
        } else {
            hideTopBar()
            if (text.isNotBlank()) searchAddress(text)
        }
    }

    private fun displayTopBar() {
        isShowingTopBar.postValue(true)
        mapAnalytics.sendDisplayTopBarAction()
    }

    private fun hideTopBar() = isShowingTopBar.postValue(false)

    fun dragStarted() {
        dragStart = System.currentTimeMillis()
    }

    fun flagDragActionFinished(latLng: LatLng) {
        addPoint(latLng)
    }

    fun addPoint(latLng: LatLng) {
        hideTopBar()
        if (route.startPoint == null) {
            setStartPosition(latLng)
            logDragEvent("startFlag")
        } else {
            setFinishPosition(latLng)
            logDragEvent("finishFlag")
        }
    }

    private fun logDragEvent(flagName: String) {
        val dragTime = System.currentTimeMillis() - dragStart
        mapAnalytics.sendDragDurationEvent(flagName, dragTime)
    }

    fun searchAddress(string: String) {
        try {
            mapAnalytics.sendSearchAddress()
            val addressLatLng = geoCoderHelper.getPositionFromFirst(string)
            if (addressLatLng != null)
                addPoint(addressLatLng)
            else
                postError(Error.ADDRESS_NOT_FOUND)
        } catch (e: IOException) {
            handleConnectionError(e)
        }
    }

    fun errorDismissed() {
        error.value = null
    }

    fun requestClearRequestedByUser() {
        mapAnalytics.sendClearRouteEvent()
        requestClear()
    }

    fun requestClear() {
        hideTopBar()
        actionRequest.postValue(ClearActionRequest(this))
    }

    fun actionRequestAccepted(actionRequest: ActionRequest) {
        actionRequest.execute()
        this.actionRequest.value = null
    }

    fun actionRequestDismissed() {
        this.actionRequest.value = null
    }

    private fun handleConnectionError(ioException: IOException) {
      if (error.value != null) return //there is a popup showing already, so no botherr
      if (notConnected()) postError(Error.NO_CONNECTION)
      else postError(Error.CANT_REACH)
    }

    private fun notConnected() = !NetworkUtil.isNetworkAvailable(getApplication())

    private fun playIfNotPlayed(tutorial: Tutorial) {
      if (!mapTutorial.hasPlayed(tutorial)) {
        playTutorial(tutorial)
        mapTutorial.setTutorialPlayed(tutorial)
      }
    }

    private fun playTutorial(tutorial: Tutorial) {
      overlay.postValue(tutorial)
    }

    fun selectedDayChanged() {
        val selectionCount = preferencesBusiness.getDaySelectionCount()
        if (selectionCount == 3 && mapTutorial.hasPlayed(Tutorial.ROUTE_CREATED_TUTORIAL)) {
            actionRequest.postValue(RateMeActionRequest(getApplication()))
        }
    }

    //todo so is coroutines gonna be able to make me get rid of these callbacks!?
    private inner class CurrentLocationCallback : CurrentLocationRequester.Companion.Callback {
        override fun onCurrentLocation(latLng: LatLng) {
            setStartPosition(latLng)
            currentLocationRequester.removeLocationObserver()
        }

        override fun onNotFound() {
            postError(Error.CANT_FIND_CURRENT_LOCATION)
        }

        override fun onPermissionDenied() {
            permissionRequest.postValue(LocationPermissionRequest(LocationPermissionRequestCallback()))
        }
    }

    private inner class LocationPermissionRequestCallback : LocationPermissionRequest.Companion.Callback {
        override fun granted(lifeCycleOwner: LifecycleOwner) {
            currentLocationRequester.requestCurrentLocationRequestingPermission(lifeCycleOwner)
        }

        override fun denied() {
            warn(Warning.PERMISSION_DENIED)
        }
    }
}
