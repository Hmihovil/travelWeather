package pozzo.apps.travelweather.map.viewmodel

import android.app.Application
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.MutableLiveData
import android.location.Geocoder
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.launch
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
import pozzo.apps.travelweather.direction.DirectionBusiness
import pozzo.apps.travelweather.direction.DirectionNotFoundException
import pozzo.apps.travelweather.forecast.model.Day
import pozzo.apps.travelweather.forecast.model.Route
import pozzo.apps.travelweather.forecast.model.point.FinishPoint
import pozzo.apps.travelweather.forecast.model.point.StartPoint
import pozzo.apps.travelweather.location.CurrentLocationRequester
import pozzo.apps.travelweather.location.PermissionDeniedException
import pozzo.apps.travelweather.location.helper.GeoCoderBusiness
import pozzo.apps.travelweather.map.DaggerMapComponent
import pozzo.apps.travelweather.map.overlay.MapTutorial
import pozzo.apps.travelweather.map.overlay.Tutorial
import java.io.IOException
import javax.inject.Inject

class MapViewModel(application: Application) : BaseViewModel(application) {
    private val geoCoderBusiness = GeoCoderBusiness(Geocoder(application))
    @Inject protected lateinit var mapAnalytics: MapAnalytics
    @Inject protected lateinit var preferencesBusiness: PreferencesBusiness
    @Inject protected lateinit var directionBusiness: DirectionBusiness
    @Inject protected lateinit var currentLocationRequester: CurrentLocationRequester

    private var dragStart = 0L
    private var updateRouteJob: Job? = null
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
        DaggerMapComponent.builder()
                .appComponent(App.component())
                .build()
                .inject(this)

        isShowingProgress.value = false
        isShowingTopBar.value = false
        shouldFinish.value = false
        routeData.value = route
        //todo it must be a better solution to make this dependency clear with injection
        currentLocationRequester.callback = CurrentLocationCallback()
        playIfNotPlayed(Tutorial.FULL_TUTORIAL)
    }

    private fun setRoute(route: Route) {
      this.route = route
      routeData.postValue(route)
    }

    fun setStartAsCurrentLocationRequestedByUser(lifecycleOwner: LifecycleOwner) {
        mapAnalytics.sendFirebaseUserRequestedCurrentLocationEvent()
        setStartAsCurrentLocation(lifecycleOwner)
    }

    private fun setStartAsCurrentLocation(lifecycleOwner: LifecycleOwner) {
        try {
            currentLocationRequester.requestCurrentLocationRequestingPermission(lifecycleOwner)
        } catch (e: PermissionDeniedException) {
            permissionRequest.postValue(LocationPermissionRequest(LocationPermissionRequestCallback()))
        }
    }

    fun onMapReady(lifecycleOwner: LifecycleOwner) {
        if (route.startPoint == null) {
            setStartAsCurrentLocation(lifecycleOwner)
        }
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

    fun errorDismissed() {
        error.value = null
    }

    private fun showProgress() {
        isShowingProgress.postValue(true)
    }

    private fun hideProgress() {
        isShowingProgress.postValue(false)
    }

    fun clearStartPosition() {
        setRoute(Route(finishPoint = route.finishPoint))
    }

    fun setStartPosition(startPosition: LatLng) {
        val startPoint = StartPoint(startPosition)
        updateRoute(startPoint = startPoint)
    }

    fun clearFinishPosition() {
        setRoute(Route(startPoint = route.startPoint))
    }

    fun setFinishPosition(finishPosition: LatLng) {
        val finishPoint = FinishPoint(finishPosition)
        updateRoute(finishPoint = finishPoint)
        playIfNotPlayed(Tutorial.ROUTE_CREATED_TUTORIAL)
    }

    private fun updateRoute(startPoint: StartPoint? = route.startPoint, finishPoint: FinishPoint? = route.finishPoint) {
        setRoute(Route(startPoint = startPoint, finishPoint = finishPoint))
        if (startPoint == null || finishPoint == null) return

        showProgress()
        updateRouteJob?.cancel()
        updateRouteJob = launch {
            try {
                val route = directionBusiness.createRoute(startPoint, finishPoint)
                if (isActive) setRoute(route)
            } catch (e: DirectionNotFoundException) {
                postError(Error.CANT_FIND_ROUTE)
            } catch (e: IOException) {
                handleConnectionError(e)
            } finally {
                hideProgress()
            }
        }
    }

    private fun handleConnectionError(ioException: IOException) {
        if (error.value != null) return //there is a popup showing already, so no botherr
        if (notConnected()) postError(Error.NO_CONNECTION)
        else postError(Error.CANT_REACH)
    }

    private fun notConnected() = !NetworkUtil.isNetworkAvailable(getApplication())

    fun back() {
        when {
            isShowingTopBar.value == true -> hideTopBar()
            route.finishPoint != null -> clearFinishPosition()
            route.startPoint != null -> clearStartPosition()
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
            geoCoderBusiness.getPositionFromFirst(string)?.let {
                addPoint(it)
            } ?: postError(Error.ADDRESS_NOT_FOUND)
        } catch (e: IOException) {
            handleConnectionError(e)
        }
    }

    fun requestClear() {
        mapAnalytics.sendClearRouteEvent()
        actionRequest.postValue(ClearActionRequest(this))
    }

    fun actionRequestAccepted(actionRequest: ActionRequest) {
        actionRequest.execute()
        this.actionRequest.value = null
    }

    fun actionRequestDismissed() {
        actionRequest.value = null
    }

    private fun playIfNotPlayed(tutorial: Tutorial) {
      if (!mapTutorial.hasPlayed(tutorial)) {
        playTutorial(tutorial)
        mapTutorial.setTutorialPlayed(tutorial)
      }
    }

    private fun playTutorial(tutorial: Tutorial) {
      overlay.postValue(tutorial)
    }

    fun selectedDayChanged(newSelection: Day) {
        val rateMeActionRequest = RateMeActionRequest(getApplication(), mapAnalytics)
        if (rateMeActionRequest.isTimeToDisplay(mapTutorial, preferencesBusiness.getDaySelectionCount())) {
            actionRequest.postValue(rateMeActionRequest)
            mapTutorial.setTutorialPlayed(Tutorial.RATE_DIALOG)
            mapAnalytics.sendRateDialogShown()
        }
    }

    private inner class CurrentLocationCallback : CurrentLocationRequester.Callback {
        override fun onCurrentLocation(latLng: LatLng) {
            setStartPosition(latLng)
            currentLocationRequester.removeLocationObserver()
        }

        override fun onNotFound() {
            postError(Error.CANT_FIND_CURRENT_LOCATION)
        }
    }

    private inner class LocationPermissionRequestCallback : LocationPermissionRequest.Callback {
        override fun granted(lifeCycleOwner: LifecycleOwner) {
            currentLocationRequester.requestCurrentLocationRequestingPermission(lifeCycleOwner)
        }

        override fun denied() {
            warn(Warning.PERMISSION_DENIED)
        }
    }
}