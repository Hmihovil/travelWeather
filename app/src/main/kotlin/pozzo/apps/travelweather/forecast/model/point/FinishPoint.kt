package pozzo.apps.travelweather.forecast.model.point

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import pozzo.apps.travelweather.R
import pozzo.apps.travelweather.common.android.BitmapCreator
import pozzo.apps.travelweather.forecast.ForecastTitleFormatter

class FinishPoint(position: LatLng) : MapPoint(
        BitmapCreator.get().fromResource(R.drawable.finish_flag),
        position, null, true, false) {

    override fun getTitle(context: Context, forecastTitleFormatter: ForecastTitleFormatter) = context.getString(R.string.finishPosition)
}
