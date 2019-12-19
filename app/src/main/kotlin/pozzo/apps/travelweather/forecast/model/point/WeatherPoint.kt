package pozzo.apps.travelweather.forecast.model.point

import android.content.Context
import pozzo.apps.travelweather.R
import pozzo.apps.travelweather.forecast.model.Forecast
import pozzo.apps.travelweather.forecast.model.PoweredBy
import pozzo.apps.travelweather.forecast.model.Weather

class WeatherPoint(private val weather: Weather) :
        MapPoint(null, weather.latLng, weather.url, false, true) {

    val forecastSize = weather.forecasts.size
    val forecast: Forecast get() = weather.getForecast(day)
    override val icon get() = forecast.icon
    val poweredBy: PoweredBy get() = weather.poweredBy

    override fun getTitle(context: Context): String {
        //TODO can I avoid the getString?
        val forecastString = context.getString(forecast.forecastType?.stringId!!)
        val min = context.getString(R.string.min)
        val max = context.getString(R.string.max)
        return "$forecastString - $min: ${forecast.low} $max: ${forecast.high}"
    }
}
