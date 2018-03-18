package pozzo.apps.travelweather.forecast;

import com.google.android.gms.maps.model.LatLng;

import pozzo.apps.travelweather.forecast.yahoo.ForecastClientYahoo;
import pozzo.apps.travelweather.map.model.Address;
import pozzo.apps.travelweather.forecast.model.Weather;

/**
 * Forecast business logic.
 */
public class ForecastBusiness {
	public static final int MAX_RETRIES = 3;

	private ForecastClient forecastClient = new ForecastClientYahoo();

    /**
     * Forecast from given location.
     */
    public Weather from(LatLng location) {
		return forecastClient.fromCoordinates(location);
    }

	public Weather from(Address address) {
		int i = 0;
		String addressStr = address.getAddress();
		if (addressStr == null)
			return null;
		do {
			try {
				if (!addressStr.contains(","))
					return null;

				Weather weather = forecastClient.fromAddress(addressStr);
				weather.setAddress(address);
				return weather;
			} catch (Exception e) {
				//ignored to retry
			}
			int firstCommaIdx = addressStr.indexOf(",");
			addressStr = firstCommaIdx == -1 ? "" : addressStr.substring(firstCommaIdx + 1).trim();
		} while (++i < MAX_RETRIES);
		return null;
	}
}
