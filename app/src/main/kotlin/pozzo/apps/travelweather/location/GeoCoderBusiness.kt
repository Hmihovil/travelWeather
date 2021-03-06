package pozzo.apps.travelweather.location

import android.location.Geocoder
import com.google.android.gms.maps.model.LatLng
import java.io.IOException

/**
 * Helps in location.
 */
class GeoCoderBusiness(private val geocoder: Geocoder) {

    /**
     * Get first related address.
     */
    @Throws(IOException::class)
    fun getPositionFromFirst(address: String?): LatLng? =
            address?.let {
                geocoder.getFromLocationName(address, 1)
            }?.firstOrNull()?.let {
                LatLng(it.latitude, it.longitude)
            }
}
