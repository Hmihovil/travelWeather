package pozzo.apps.travelweather.forecast.weatherunlocked

import pozzo.apps.travelweather.BuildConfig
import pozzo.apps.travelweather.forecast.ForecastClient
import pozzo.apps.travelweather.forecast.ForecastTypeMapper
import retrofit2.Retrofit

class ForecastModuleWeatherUnlocked {

    fun forecastClient(retrofitBuilder: Retrofit.Builder): ForecastClient =
            WeatherUnlockedClient(createApi(retrofitBuilder, baseUrl()), "9e2ec5bf",
                    BuildConfig.WEATHER_UNLOCKED, forecastTypeMapper())

    private fun forecastTypeMapper(): ForecastTypeMapper = ForecastTypeMapperWeatherUnlocked()

    private fun createApi(retrofitBuilder: Retrofit.Builder, baseUrl: String): WeatherUnlockedApi {
        return retrofitBuilder
                .baseUrl(baseUrl)
                .build()
                .create(WeatherUnlockedApi::class.java)
    }

    private fun baseUrl() = "http://api.weatherunlocked.com/api/"
}