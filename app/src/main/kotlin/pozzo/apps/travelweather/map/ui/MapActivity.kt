package pozzo.apps.travelweather.map.ui

import android.app.AlertDialog
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.databinding.DataBindingUtil
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import kotlinx.android.synthetic.main.activity_maps.*
import kotlinx.android.synthetic.main.group_flag_shelf.*
import kotlinx.android.synthetic.main.group_top_bar.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import pozzo.apps.tools.AndroidUtil
import pozzo.apps.travelweather.R
import pozzo.apps.travelweather.common.ShadowResByBottomRight
import pozzo.apps.travelweather.common.viewmodel.PreferencesViewModel
import pozzo.apps.travelweather.core.BaseActivity
import pozzo.apps.travelweather.core.Error
import pozzo.apps.travelweather.core.Warning
import pozzo.apps.travelweather.core.action.ActionRequest
import pozzo.apps.travelweather.core.bugtracker.Bug
import pozzo.apps.travelweather.databinding.ActivityMapsBinding
import pozzo.apps.travelweather.forecast.model.Day
import pozzo.apps.travelweather.forecast.model.Route
import pozzo.apps.travelweather.forecast.model.point.MapPoint
import pozzo.apps.travelweather.forecast.model.point.StartPoint
import pozzo.apps.travelweather.map.ReturnAnimation
import pozzo.apps.travelweather.map.manager.PermissionManager
import pozzo.apps.travelweather.map.overlay.MapTutorial
import pozzo.apps.travelweather.map.overlay.Tutorial
import pozzo.apps.travelweather.map.viewmodel.MapViewModel
import java.util.*

//todo colocar data da previsao junto ao texto da previsao
class MapActivity : BaseActivity() {
    private var mapMarkerToWeather = HashMap<Marker, MapPoint>()

    private lateinit var mainThread: Handler
    private lateinit var returnAnimation: ReturnAnimation

    private lateinit var mapFragment: MapFragment
    private lateinit var viewModel: MapViewModel
    private lateinit var preferencesViewModel: PreferencesViewModel
    private lateinit var permissionManager: PermissionManager

    private var lastDisplayedRoute = Route()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupViewModel()
        this.mainThread = Handler()
        this.permissionManager = PermissionManager(this, viewModel)
        this.returnAnimation = ReturnAnimation(resources)
        setupDataBind()
        setupMapFragment()
        setupView()
        observeViewModel()
    }

    private fun setupViewModel() {
        viewModel = ViewModelProviders.of(this).get(MapViewModel::class.java)
        preferencesViewModel = ViewModelProviders.of(this).get(PreferencesViewModel::class.java)
    }

    private fun setupDataBind() {
        val contentView = DataBindingUtil.setContentView<ActivityMapsBinding>(this, R.layout.activity_maps)
        contentView.viewModel = viewModel
    }

    private fun setupMapFragment() {
        mapFragment = supportFragmentManager.findFragmentById(R.id.map) as MapFragment
    }

    private fun setupView() {
        eSearch.setOnEditorActionListener(onSearchGo)

        startFlag.setOnTouchListener(startDraggingFlag)
        finishFlag.setOnTouchListener(startDraggingFlag)
        setupDaySelection()
    }

    private fun setupDaySelection() {
        spinnerDaySelection.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) { }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                preferencesViewModel.setSelectedDay(position)
            }
        }
    }

    private val onSearchGo = TextView.OnEditorActionListener { textView, _, event ->
        if (event == null || event.action != KeyEvent.ACTION_DOWN)
            return@OnEditorActionListener false

        viewModel.searchAddress(textView.text.toString())
        return@OnEditorActionListener true
    }

    private val startDraggingFlag = View.OnTouchListener { view: View, motionEvent: MotionEvent ->
        view.visibility = View.INVISIBLE
        val flagResource = if (view.id == R.id.startFlag) R.drawable.start_flag else R.drawable.finish_flag
        val flag = resources.getDrawable(flagResource, null)
        if (motionEvent.action == MotionEvent.ACTION_DOWN) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                startFlag.startDragAndDrop(null, ShadowResByBottomRight(startFlag, flag), null, 0)
            } else {
                @Suppress("DEPRECATION")
                startFlag.startDrag(null, ShadowResByBottomRight(startFlag, flag), null, 0)
            }
        }

        return@OnTouchListener true
    }

    private fun observeViewModel() {
        preferencesViewModel.selectedDay.observe(this, Observer { it?.let { changeSelectedDay(it) } })

        viewModel.routeData.observe(this, Observer { updateRoute(it as Route) })

        viewModel.isShowingProgress.observe(this, Observer { progressDialogStateChanged(it) })
        viewModel.isShowingTopBar.observe(this, Observer { if (it == true) showTopBar() else hideTopBar() })
        viewModel.shouldFinish.observe(this, Observer { if (it == true) finish() })
        viewModel.error.observe(this, Observer { if (it != null) showError(it) })
        viewModel.warning.observe(this, Observer { if (it != null) showWarning(it) })
        viewModel.actionRequest.observe(this, Observer { if (it != null) showActionRequest(it) })
        viewModel.permissionRequest.observe(this, Observer { if (it != null) permissionManager.requestPermissions(it) })
        viewModel.overlay.observe(this, Observer { it?.let{ showOverlay(it) } })
    }

    private fun showOverlay(overlay: Tutorial) {
        val mapTutorial = MapTutorial(this)
        when(overlay) {
            Tutorial.FULL_TUTORIAL -> mapTutorial.playTutorial(this)
            Tutorial.ROUTE_CREATED_TUTORIAL -> mapTutorial.playRouteCreatedTutorial(this)
            else -> Bug.get().logException("Missing show overlay $overlay")
        }
    }

    private fun updateRoute(route: Route) {
        clearMap()
        if (route.isEmpty()) {
            moveFlagsBackToShelf()
        } else {
            route.polyline?.let { mapFragment.plotRoute(it) }
            setStartPoint(route.startPoint)
            setFinishPoint(route)
            showMapPoints(route)
            pointMapToRoute(route)
        }

        lastDisplayedRoute = route
    }

    private fun setStartPoint(startPoint: StartPoint?) {
        if (startPoint != null) {
            addMark(startPoint)
            startFlag.visibility = View.INVISIBLE
        } else {
            startFlag.visibility = View.VISIBLE
        }
    }

    private fun setFinishPoint(route: Route) {
        val finishPoint = route.finishPoint
        if (finishPoint != null) {
            addMark(finishPoint)
            finishFlag.visibility = View.INVISIBLE
            lDragTheFlag.visibility = View.INVISIBLE
        } else {
            finishFlag.visibility = View.VISIBLE
            finishFlag.alpha = if (route.startPoint == null) .4F else 1F
            finishFlag.isEnabled = route.startPoint != null
        }
    }

    private fun pointMapToRoute(route: Route) {
        if (route.isComplete()) {
            mapFragment.updateCamera(
                CameraUpdateFactory.newLatLngBounds(
                    LatLngBounds.builder()
                        .include(route.startPoint!!.position)
                        .include(route.finishPoint!!.position).build(), 70))
        } else if (route.startPoint != null) {
            mapFragment.updateCamera(CameraUpdateFactory.newLatLngZoom(route.startPoint.position, 8f))
        }
    }

    private fun clearMap() {
        mapMarkerToWeather.clear()
        mapFragment.clearMapOverlay()

    }

    private fun moveFlagsBackToShelf() {
        //todo after animation state
        val projection = mapFragment.getProjection()
        if (projection != null) {
            lastDisplayedRoute.startPoint?.marker?.let { returnAnimation.animate(startFlag, projection.toScreenLocation(it.position)) }
            lastDisplayedRoute.finishPoint?.marker?.let { returnAnimation.animate(finishFlag, projection.toScreenLocation(it.position)) }
        }
    }

    private fun progressDialogStateChanged(isShowingProgress: Boolean?) {
        if (isShowingProgress == true) {
            mainThread.postDelayed(triggerCheckedShowProgress, 200)
        } else
            progressBar.visibility = View.GONE
    }

    private val triggerCheckedShowProgress = Runnable {
        if (viewModel.isShowingProgress.value == true) {
            progressBar.visibility = View.VISIBLE
        }
    }

    private fun showMapPoints(route: Route) {
        launch(UI) {
            for (it in route.mapPoints) {
                if (isFinishing) break

                addMark(it)
            }
        }
    }

    public override fun onSaveInstanceState(outState: Bundle?) {
        outState?.putParcelable("startPosition", viewModel.routeData.value!!.startPoint?.position)
        outState?.putParcelable("finishPosition", viewModel.routeData.value!!.finishPoint?.position)

        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        savedInstanceState?.getParcelable<LatLng?>("startPosition")?.let { viewModel.setStartPosition(it) }
        savedInstanceState?.getParcelable<LatLng?>("finishPosition")?.let { viewModel.setFinishPosition(it) }
    }

    override fun onBackPressed() {
        viewModel.back()
    }

    private fun showError(error: Error) {
        AlertDialog.Builder(this)
            .setTitle(R.string.warning)
            .setMessage(error.messageId)
            .setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
            .setOnDismissListener { viewModel.errorDismissed() }
            .show()
    }

    private fun showWarning(warning: Warning) {
        Toast.makeText(this, getString(warning.messageId), Toast.LENGTH_LONG).show()
    }

    private fun showActionRequest(actionRequest: ActionRequest) {
        AlertDialog.Builder(this)
            .setMessage(actionRequest.messageId)
            .setPositiveButton(R.string.yes) { _, _ -> viewModel.actionRequestAccepted(actionRequest) }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .setOnDismissListener { viewModel.actionRequestDismissed() }
            .show()
    }

    private fun hideTopBar() {
        eSearch.visibility = View.GONE
        lDaySelection.visibility = View.VISIBLE
        spinnerDaySelection.visibility = View.VISIBLE
        AndroidUtil.hideKeyboard(this, eSearch)
    }

    private fun showTopBar() {
        eSearch.visibility = View.VISIBLE
        lDaySelection.visibility = View.GONE
        spinnerDaySelection.visibility = View.GONE
        eSearch.requestFocus()
        AndroidUtil.showKeyboard(this, eSearch)
    }

    private fun changeSelectedDay(newSelection: Day) {
        spinnerDaySelection.setSelection(newSelection.index)
        refreshMarkers(newSelection)
        viewModel.selectedDayChanged(newSelection)
    }

    //todo now that the mapPoint contains the marker, maybe I can find a simpler solution without the cached markers
    private fun refreshMarkers(day: Day) {
        val markerWeathers = this.mapMarkerToWeather
        this.mapMarkerToWeather = HashMap()
        markerWeathers.forEach {
            it.key.remove()
            addMark(it.value, day)
        }
    }

    private fun addMark(mapPoint: MapPoint, day: Day = preferencesViewModel.selectedDay.value!!) {
        mapPoint.day = day

        val marker = mapFragment.addMark(mapPoint)
        mapPoint.marker = marker
        if (marker != null) mapMarkerToWeather[marker] = mapPoint
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (!permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}