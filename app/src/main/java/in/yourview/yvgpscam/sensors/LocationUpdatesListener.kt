package `in`.yourview.yvgpscam.sensors

import android.location.Location
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Listens to accepted device location updates and triggers a mini-map overlay
 * refresh whenever movement from the last rendered coordinate exceeds
 * [minDisplacementMeters] (default 5.0 meters).
 */
class LocationUpdatesListener(
    val minDisplacementMeters: Float = DEFAULT_MIN_DISPLACEMENT_METERS,
    private val onMiniMapRefreshRequired: (latitude: Double, longitude: Double, distanceMeters: Float) -> Unit
) {
    private var lastTriggeredLatitude: Double? = null
    private var lastTriggeredLongitude: Double? = null

    @Synchronized
    fun onLocationChanged(location: Location): Boolean {
        return onCoordinatesUpdated(location.latitude, location.longitude)
    }

    @Synchronized
    fun onCoordinatesUpdated(latitude: Double, longitude: Double): Boolean {
        val prevLat = lastTriggeredLatitude
        val prevLon = lastTriggeredLongitude
        if (prevLat == null || prevLon == null) {
            lastTriggeredLatitude = latitude
            lastTriggeredLongitude = longitude
            onMiniMapRefreshRequired(latitude, longitude, Float.MAX_VALUE)
            return true
        }
        val movedMeters = computeDistanceMeters(prevLat, prevLon, latitude, longitude)
        if (movedMeters > minDisplacementMeters) {
            lastTriggeredLatitude = latitude
            lastTriggeredLongitude = longitude
            onMiniMapRefreshRequired(latitude, longitude, movedMeters)
            return true
        }
        return false
    }

    @Synchronized
    fun reset() {
        lastTriggeredLatitude = null
        lastTriggeredLongitude = null
    }

    companion object {
        const val DEFAULT_MIN_DISPLACEMENT_METERS = 5.0f

        /**
         * Calculates ground distance in meters between two WGS84 coordinates
         * using the Haversine formula.
         */
        fun computeDistanceMeters(
            startLat: Double,
            startLon: Double,
            endLat: Double,
            endLon: Double
        ): Float {
            val earthRadiusMeters = 6_371_000.0
            val dLat = Math.toRadians(endLat - startLat)
            val dLon = Math.toRadians(endLon - startLon)
            val lat1Rad = Math.toRadians(startLat)
            val lat2Rad = Math.toRadians(endLat)
            val haversine = sin(dLat / 2.0).pow(2.0) +
                cos(lat1Rad) * cos(lat2Rad) * sin(dLon / 2.0).pow(2.0)
            val angularDistance = 2.0 * atan2(sqrt(haversine), sqrt(1.0 - haversine))
            return (earthRadiusMeters * angularDistance).toFloat()
        }
    }
}
