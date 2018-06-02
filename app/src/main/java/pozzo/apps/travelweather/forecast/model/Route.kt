package pozzo.apps.travelweather.forecast.model

import com.google.android.gms.maps.model.PolylineOptions
import pozzo.apps.travelweather.forecast.model.point.FinishPoint
import pozzo.apps.travelweather.forecast.model.point.MapPoint
import pozzo.apps.travelweather.forecast.model.point.StartPoint

class Route(baseRoute: Route? = null,
        startPoint: StartPoint? = null,
        finishPoint: FinishPoint? = null,
        polyline: PolylineOptions? = null,
        mapPoints: List<MapPoint>? = null) {

    val startPoint: StartPoint?
    val finishPoint: FinishPoint?
    val polyline: PolylineOptions?
    val mapPoints: List<MapPoint>

    init {
        this.startPoint = startPoint ?: baseRoute?.startPoint
        this.finishPoint = finishPoint ?: baseRoute?.finishPoint
        this.polyline = polyline ?: baseRoute?.polyline
        this.mapPoints = mapPoints ?: baseRoute?.mapPoints ?: ArrayList()
    }

  fun hasStartAndFinish() : Boolean = startPoint != null && finishPoint != null
}
