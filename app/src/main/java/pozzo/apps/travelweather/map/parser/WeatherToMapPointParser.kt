package pozzo.apps.travelweather.map.parser

import pozzo.apps.travelweather.forecast.model.Weather
import pozzo.apps.travelweather.forecast.model.point.MapPoint
import pozzo.apps.travelweather.forecast.model.point.WeatherPoint

class WeatherToMapPointParser {

    fun parse(weathers: List<Weather>) : List<MapPoint> = weathers.mapNotNull { parse(it) }

    fun parse(weather: Weather) : MapPoint? = weather.address?.let { WeatherPoint(weather) }
}