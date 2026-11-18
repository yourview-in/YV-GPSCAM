package `in`.yourview.yvgpscam.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import `in`.yourview.yvgpscam.data.model.GpsSignalState
import `in`.yourview.yvgpscam.data.model.GpsTelemetry
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.abs

class LocationTelemetryManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val systemLocationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val addressCachePrefs by lazy {
        context.getSharedPreferences(ADDRESS_CACHE_PREFS, Context.MODE_PRIVATE)
    }

    private val _telemetry = MutableStateFlow(GpsTelemetry())
    val telemetry: StateFlow<GpsTelemetry> = _telemetry.asStateFlow()

    private val _horizonTiltDegrees = MutableStateFlow(0f to 0f)
    val horizonTiltDegrees: StateFlow<Pair<Float, Float>> = _horizonTiltDegrees.asStateFlow()

    private val _isDeviceLocationEnabled = MutableStateFlow(checkDeviceLocationEnabled())
    val isDeviceLocationEnabled: StateFlow<Boolean> = _isDeviceLocationEnabled.asStateFlow()

    private val httpClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private var isLocationStarted = false
    private var bestCurrentLocation: Location? = null
    private var lastGeocodedLocation: Location? = null
    private var lastWeatherLocation: Location? = null
    private var lastWeatherFetchedAtMillis: Long = 0L
    private var currentFusedCancellation: CancellationTokenSource? = null
    private var currentNativeCancellation: CancellationSignal? = null
    private val locationUpdatesListeners = CopyOnWriteArraySet<LocationUpdatesListener>()

    private val gravityReading = FloatArray(3)
    private val magnetReading = FloatArray(3)
    private var hasGravity = false
    private var hasMagnet = false
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            handleNewLocation(location)
        }
    }

    private val nativeLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleNewLocation(location)
        }

        override fun onLocationChanged(locations: MutableList<Location>) {
            locations.lastOrNull()?.let { handleNewLocation(it) }
        }

        override fun onProviderEnabled(provider: String) {
            _isDeviceLocationEnabled.value = checkDeviceLocationEnabled()
            if (isLocationStarted) {
                requestSingleNativeFix(provider)
            }
        }

        override fun onProviderDisabled(provider: String) {
            _isDeviceLocationEnabled.value = checkDeviceLocationEnabled()
        }

        @Deprecated("Deprecated in Android API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    private val gnssStatusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val visible = status.satelliteCount
            var used = 0
            for (i in 0 until visible) {
                if (status.usedInFix(i)) used++
            }
            val current = _telemetry.value
            if (current.satellitesUsed != used || current.satellitesVisible != visible) {
                _telemetry.update {
                    it.copy(
                        satellitesUsed = used,
                        satellitesVisible = visible
                    )
                }
            }
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    updateCompass(Math.toDegrees(orientationAngles[0].toDouble()).toFloat())
                    updateTilt(
                        pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat(),
                        rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
                    )
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    System.arraycopy(event.values, 0, gravityReading, 0, 3)
                    hasGravity = true
                    val rollDeg = Math.toDegrees(
                        kotlin.math.atan2(
                            -event.values[0].toDouble(),
                            kotlin.math.sqrt((event.values[1] * event.values[1] + event.values[2] * event.values[2]).toDouble())
                        )
                    ).toFloat()
                    val pitchDeg = Math.toDegrees(
                        kotlin.math.atan2(event.values[1].toDouble(), event.values[2].toDouble())
                    ).toFloat()
                    updateTilt(pitchDeg, rollDeg)
                    computeFallbackCompass()
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    System.arraycopy(event.values, 0, magnetReading, 0, 3)
                    hasMagnet = true
                    val magnitudeUt = kotlin.math.sqrt(
                        (event.values[0] * event.values[0] +
                            event.values[1] * event.values[1] +
                            event.values[2] * event.values[2]).toDouble()
                    ).toFloat()
                    if (abs(magnitudeUt - (_telemetry.value.magneticFieldMicroTesla ?: 0f)) > 3f) {
                        _telemetry.update { it.copy(magneticFieldMicroTesla = magnitudeUt) }
                    }
                    computeFallbackCompass()
                }
                Sensor.TYPE_PRESSURE -> {
                    val hpa = event.values[0]
                    if (abs(hpa - (_telemetry.value.pressureHpa ?: 0f)) > 0.5f) {
                        _telemetry.update { it.copy(pressureHpa = hpa) }
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun checkDeviceLocationEnabled(): Boolean {
        val mgr = systemLocationManager ?: return true
        return runCatching { LocationManagerCompat.isLocationEnabled(mgr) }.getOrDefault(true)
    }

    private fun computeFallbackCompass() {
        if (hasGravity && hasMagnet) {
            if (SensorManager.getRotationMatrix(rotationMatrix, null, gravityReading, magnetReading)) {
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                updateCompass(Math.toDegrees(orientationAngles[0].toDouble()).toFloat())
            }
        }
    }

    private fun updateTilt(pitchDeg: Float, rollDeg: Float) {
        val (curPitch, curRoll) = _horizonTiltDegrees.value
        if (abs(pitchDeg - curPitch) > 0.4f || abs(rollDeg - curRoll) > 0.4f) {
            _horizonTiltDegrees.value = pitchDeg to rollDeg
        }
    }

    private fun updateCompass(rawDegrees: Float) {
        val normalized = ((rawDegrees % 360f) + 360f) % 360f
        val current = _telemetry.value.compassAzimuth
        val diff = abs(normalized - current)
        if (diff > 1.5f) {
            _telemetry.update {
                it.copy(
                    compassAzimuth = normalized,
                    compassCardinal = GpsTelemetry.azimuthToCardinal(normalized)
                )
            }
        }
    }

    fun startCompass() {
        val mgr = sensorManager ?: return
        val rotSensor = mgr.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotSensor != null) {
            mgr.registerListener(sensorListener, rotSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            mgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                mgr.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
        mgr.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
            mgr.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        mgr.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let {
            mgr.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stopCompass() {
        sensorManager?.unregisterListener(sensorListener)
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(hasLocationPermission: Boolean) {
        _isDeviceLocationEnabled.value = checkDeviceLocationEnabled()
        if (!hasLocationPermission) {
            _telemetry.update { it.copy(signalState = GpsSignalState.PERMISSION_DENIED) }
            return
        }
        if (isLocationStarted) {
            // Re-seed last known & request a fresh fix on resume (critical when offline GPS toggled on)
            refreshLocationNow(hasLocationPermission = true)
            return
        }
        isLocationStarted = true

        _telemetry.update {
            if (!it.hasFix) it.copy(signalState = GpsSignalState.ACQUIRING) else it
        }

        // 1. Seed immediately from native LocationManager last-known cache (works 100% offline)
        seedFromLastKnownLocation()

        // 2. Register hardware GNSS + native LocationManager providers (GPS_PROVIDER works without internet)
        registerNativeLocationProviders()

        // 3. Register Play Services FusedLocationProviderClient with waitForAccurateLocation = false
        runCatching {
            fusedClient.lastLocation.addOnSuccessListener { lastLoc ->
                if (lastLoc != null) handleNewLocation(lastLoc)
            }
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                .setMinUpdateIntervalMillis(1000L)
                .setWaitForAccurateLocation(false)
                .build()
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        }

        // 4. Trigger immediate high-accuracy single fix across Fused + native GPS
        requestActiveSingleFix()
    }

    @SuppressLint("MissingPermission")
    fun refreshLocationNow(hasLocationPermission: Boolean) {
        _isDeviceLocationEnabled.value = checkDeviceLocationEnabled()
        if (!hasLocationPermission) {
            _telemetry.update { it.copy(signalState = GpsSignalState.PERMISSION_DENIED) }
            return
        }
        if (!isLocationStarted) {
            startLocationUpdates(true)
            return
        }
        seedFromLastKnownLocation()
        registerNativeLocationProviders()
        requestActiveSingleFix()
    }

    @SuppressLint("MissingPermission")
    private fun seedFromLastKnownLocation() {
        val mgr = systemLocationManager
        val candidates = mutableListOf<Location>()
        if (mgr != null) {
            val providers = buildList {
                add(LocationManager.GPS_PROVIDER)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(LocationManager.FUSED_PROVIDER)
                }
                add(LocationManager.NETWORK_PROVIDER)
                add(LocationManager.PASSIVE_PROVIDER)
            }
            for (provider in providers) {
                runCatching {
                    if (mgr.allProviders.contains(provider)) {
                        mgr.getLastKnownLocation(provider)?.let { candidates.add(it) }
                    }
                }
            }
        }
        if (candidates.isNotEmpty()) {
            candidates.forEach { loc -> handleNewLocation(loc) }
        } else if (bestCurrentLocation == null) {
            loadRecentPersistedLocationFallback()?.let { handleNewLocation(it) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerNativeLocationProviders() {
        val mgr = systemLocationManager ?: return
        val providers = buildList {
            add(LocationManager.GPS_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        for (provider in providers) {
            runCatching {
                if (mgr.allProviders.contains(provider)) {
                    val minTimeMs = if (provider == LocationManager.GPS_PROVIDER) 1000L else 1500L
                    mgr.requestLocationUpdates(
                        provider,
                        minTimeMs,
                        0f,
                        nativeLocationListener,
                        Looper.getMainLooper()
                    )
                }
            }
        }
        runCatching {
            mgr.registerGnssStatusCallback(gnssStatusCallback, mainHandler)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestActiveSingleFix() {
        runCatching {
            currentFusedCancellation?.cancel()
            val cts = CancellationTokenSource()
            currentFusedCancellation = cts
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) handleNewLocation(loc)
                }
        }
        requestSingleNativeFix(LocationManager.GPS_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    private fun requestSingleNativeFix(provider: String) {
        val mgr = systemLocationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                if (mgr.isProviderEnabled(provider)) {
                    currentNativeCancellation?.cancel()
                    val signal = CancellationSignal()
                    currentNativeCancellation = signal
                    mgr.getCurrentLocation(
                        provider,
                        signal,
                        ContextCompat.getMainExecutor(context)
                    ) { loc ->
                        if (loc != null) handleNewLocation(loc)
                    }
                }
            }
        }
    }

    fun stopLocationUpdates() {
        if (!isLocationStarted) return
        isLocationStarted = false
        currentFusedCancellation?.cancel()
        currentNativeCancellation?.cancel()
        runCatching { fusedClient.removeLocationUpdates(locationCallback) }
        runCatching { systemLocationManager?.removeUpdates(nativeLocationListener) }
        runCatching { systemLocationManager?.unregisterGnssStatusCallback(gnssStatusCallback) }
    }

    @VisibleForTesting
    internal fun acceptLocationForTest(location: Location) {
        handleNewLocation(location)
    }

    fun addLocationUpdatesListener(listener: LocationUpdatesListener) {
        locationUpdatesListeners.add(listener)
        bestCurrentLocation?.let { listener.onLocationChanged(it) }
    }

    fun removeLocationUpdatesListener(listener: LocationUpdatesListener) {
        locationUpdatesListeners.remove(listener)
    }

    fun onNetworkRestored(hasLocationPermission: Boolean) {
        val currentLoc = bestCurrentLocation
        if (currentLoc != null) {
            val prevGeocoded = lastGeocodedLocation
            if (prevGeocoded == null || prevGeocoded.distanceTo(currentLoc) > 35f) {
                reverseGeocode(currentLoc)
            }
            val now = System.currentTimeMillis()
            val prevWeatherLoc = lastWeatherLocation
            val movedFar = prevWeatherLoc == null || prevWeatherLoc.distanceTo(currentLoc) > 2000f
            val staleWeather = (now - lastWeatherFetchedAtMillis) > 15 * 60 * 1000L
            if (movedFar || staleWeather) {
                lastWeatherLocation = currentLoc
                lastWeatherFetchedAtMillis = now
                fetchWeatherInBackground(currentLoc.latitude, currentLoc.longitude)
            }
        } else if (hasLocationPermission) {
            refreshLocationNow(hasLocationPermission = true)
        }
    }

    private fun handleNewLocation(location: Location) {
        val currentBest = bestCurrentLocation
        if (!isBetterLocation(location, currentBest)) return
        bestCurrentLocation = location
        if (location.provider != PROVIDER_PERSISTED_CACHE) {
            persistLastValidCoordinates(location)
        }
        locationUpdatesListeners.forEach { listener ->
            listener.onLocationChanged(location)
        }

        val signalState = when {
            location.provider == PROVIDER_PERSISTED_CACHE -> GpsSignalState.COARSE
            location.hasAccuracy() && location.accuracy <= 35f -> GpsSignalState.LOCKED
            location.provider == LocationManager.GPS_PROVIDER -> GpsSignalState.LOCKED
            else -> GpsSignalState.COARSE
        }
        val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else null
        val altitude = if (location.hasAltitude()) location.altitude else null
        val accuracy = if (location.hasAccuracy()) location.accuracy else null
        val bearing = if (location.hasBearing()) location.bearing else null
        val providerName = location.provider.orEmpty().uppercase(Locale.US)

        // Reuse cached nearby address when offline; preserve regional city/country up to 5km while clearing distant street
        val cachedAddress = loadCachedAddressNear(location)

        _telemetry.update { prevState ->
            val distFromLastGeocoded = lastGeocodedLocation?.distanceTo(location)
            val keepStreetAddress = distFromLastGeocoded != null && distFromLastGeocoded <= OFFLINE_ADDRESS_REUSE_METERS
            val keepRegionAddress = distFromLastGeocoded != null && distFromLastGeocoded <= OFFLINE_REGION_REUSE_METERS

            prevState.copy(
                latitude = location.latitude,
                longitude = location.longitude,
                altitudeMeters = altitude,
                accuracyMeters = accuracy,
                speedKmh = speedKmh,
                bearingDegrees = bearing,
                signalState = signalState,
                lastUpdatedMillis = System.currentTimeMillis(),
                provider = providerName,
                addressLine = when {
                    keepStreetAddress && prevState.addressLine.isNotBlank() -> prevState.addressLine
                    cachedAddress != null && cachedAddress.addressLine.isNotBlank() -> cachedAddress.addressLine
                    else -> ""
                },
                cityState = when {
                    keepRegionAddress && prevState.cityState.isNotBlank() -> prevState.cityState
                    cachedAddress != null -> cachedAddress.cityState
                    else -> ""
                },
                country = when {
                    keepRegionAddress && prevState.country.isNotBlank() -> prevState.country
                    cachedAddress != null -> cachedAddress.country
                    else -> ""
                },
                countryCode = when {
                    keepRegionAddress && prevState.countryCode.isNotBlank() -> prevState.countryCode
                    cachedAddress != null -> cachedAddress.countryCode
                    else -> ""
                },
                postalCode = when {
                    keepStreetAddress && prevState.postalCode.isNotBlank() -> prevState.postalCode
                    cachedAddress != null && cachedAddress.postalCode.isNotBlank() -> cachedAddress.postalCode
                    else -> ""
                }
            )
        }

        val prevGeocoded = lastGeocodedLocation
        if (prevGeocoded == null || prevGeocoded.distanceTo(location) > 35f) {
            reverseGeocode(location)
        }

        val now = System.currentTimeMillis()
        val prevWeatherLoc = lastWeatherLocation
        val movedFar = prevWeatherLoc == null || prevWeatherLoc.distanceTo(location) > 2000f
        val staleWeather = (now - lastWeatherFetchedAtMillis) > 15 * 60 * 1000L
        if (movedFar || staleWeather) {
            lastWeatherLocation = location
            lastWeatherFetchedAtMillis = now
            fetchWeatherInBackground(location.latitude, location.longitude)
        }
    }

    private fun fetchWeatherInBackground(lat: Double, lon: Double) {
        scope.launch {
            runCatching {
                val url = String.format(
                    Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,relative_humidity_2m,wind_speed_10m",
                    lat,
                    lon
                )
                val req = okhttp3.Request.Builder().url(url).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    val current = org.json.JSONObject(body).optJSONObject("current") ?: return@use
                    val temp = if (current.has("temperature_2m")) current.optDouble("temperature_2m").toFloat() else null
                    val humidity = if (current.has("relative_humidity_2m")) current.optInt("relative_humidity_2m") else null
                    val wind = if (current.has("wind_speed_10m")) current.optDouble("wind_speed_10m").toFloat() else null
                    _telemetry.update {
                        it.copy(
                            weatherTempCelsius = temp,
                            weatherHumidityPercent = humidity,
                            weatherWindKmh = wind
                        )
                    }
                }
            }
        }
    }

    private fun reverseGeocode(location: Location) {
        if (!Geocoder.isPresent()) return
        val geocoder = Geocoder(context, Locale.getDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                geocoder.getFromLocation(
                    location.latitude,
                    location.longitude,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) {
                            addresses.firstOrNull()?.let { addr ->
                                applyAddress(addr, location)
                            }
                        }

                        override fun onError(errorMessage: String?) = Unit
                    }
                )
            }
        } else {
            scope.launch {
                runCatching {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    addresses?.firstOrNull()?.let { addr ->
                        applyAddress(addr, location)
                    }
                }
            }
        }
    }

    private fun applyAddress(addr: android.location.Address, sourceLocation: Location) {
        lastGeocodedLocation = sourceLocation
        val street = addr.getAddressLine(0) ?: listOfNotNull(
            addr.subThoroughfare,
            addr.thoroughfare,
            addr.subLocality
        ).joinToString(" ")
        val cityState = listOfNotNull(addr.locality, addr.adminArea)
            .distinct()
            .joinToString(", ")
        val country = addr.countryName.orEmpty()
        val countryCode = addr.countryCode.orEmpty()
        val postal = addr.postalCode.orEmpty()

        saveCachedAddress(sourceLocation, street, cityState, country, countryCode, postal)

        _telemetry.update {
            it.copy(
                addressLine = street,
                cityState = cityState,
                country = country,
                countryCode = countryCode,
                postalCode = postal
            )
        }
    }

    private data class CachedGeoAddress(
        val addressLine: String,
        val cityState: String,
        val country: String,
        val countryCode: String,
        val postalCode: String
    )

    private fun persistLastValidCoordinates(loc: Location) {
        val editor = addressCachePrefs.edit()
            .putString(KEY_LAST_FIX_LAT, loc.latitude.toString())
            .putString(KEY_LAST_FIX_LON, loc.longitude.toString())
            .putLong(KEY_LAST_FIX_TIME, loc.time.takeIf { it > 0L } ?: System.currentTimeMillis())
        if (loc.hasAltitude()) {
            editor.putString(KEY_LAST_FIX_ALT, loc.altitude.toString())
        }
        editor.apply()
    }

    private fun loadRecentPersistedLocationFallback(): Location? {
        val lat = addressCachePrefs.getString(KEY_LAST_FIX_LAT, null)?.toDoubleOrNull() ?: return null
        val lon = addressCachePrefs.getString(KEY_LAST_FIX_LON, null)?.toDoubleOrNull() ?: return null
        val fixTime = addressCachePrefs.getLong(KEY_LAST_FIX_TIME, 0L)
        if (fixTime <= 0L || (System.currentTimeMillis() - fixTime) > PERSISTED_FIX_MAX_AGE_MS) {
            return null
        }
        return Location(PROVIDER_PERSISTED_CACHE).apply {
            latitude = lat
            longitude = lon
            accuracy = 60f
            time = fixTime
            addressCachePrefs.getString(KEY_LAST_FIX_ALT, null)?.toDoubleOrNull()?.let {
                altitude = it
            }
        }
    }

    private fun saveCachedAddress(
        loc: Location,
        street: String,
        cityState: String,
        country: String,
        countryCode: String,
        postal: String
    ) {
        addressCachePrefs.edit()
            .putString(KEY_LAT, loc.latitude.toString())
            .putString(KEY_LON, loc.longitude.toString())
            .putString(KEY_STREET, street)
            .putString(KEY_CITY, cityState)
            .putString(KEY_COUNTRY, country)
            .putString(KEY_CODE, countryCode)
            .putString(KEY_POSTAL, postal)
            .apply()
    }

    private fun loadCachedAddressNear(loc: Location): CachedGeoAddress? {
        val lat = addressCachePrefs.getString(KEY_LAT, null)?.toDoubleOrNull() ?: return null
        val lon = addressCachePrefs.getString(KEY_LON, null)?.toDoubleOrNull() ?: return null
        val cachedLoc = Location("cache").apply {
            latitude = lat
            longitude = lon
        }
        val distanceMeters = cachedLoc.distanceTo(loc)
        if (distanceMeters > OFFLINE_REGION_REUSE_METERS) return null
        val withinStreetRadius = distanceMeters <= OFFLINE_ADDRESS_REUSE_METERS
        val street = if (withinStreetRadius) addressCachePrefs.getString(KEY_STREET, "").orEmpty() else ""
        val city = addressCachePrefs.getString(KEY_CITY, "").orEmpty()
        val country = addressCachePrefs.getString(KEY_COUNTRY, "").orEmpty()
        val postal = if (withinStreetRadius) addressCachePrefs.getString(KEY_POSTAL, "").orEmpty() else ""
        if (street.isBlank() && city.isBlank() && country.isBlank()) return null
        return CachedGeoAddress(
            addressLine = street,
            cityState = city,
            country = country,
            countryCode = addressCachePrefs.getString(KEY_CODE, "").orEmpty(),
            postalCode = postal
        )
    }

    @VisibleForTesting
    internal fun saveCachedAddressForTest(
        loc: Location,
        street: String,
        cityState: String,
        country: String,
        countryCode: String = "",
        postal: String = ""
    ) {
        saveCachedAddress(loc, street, cityState, country, countryCode, postal)
    }

    companion object {
        private const val ADDRESS_CACHE_PREFS = "yvgpscam_last_address"
        private const val PROVIDER_PERSISTED_CACHE = "last_known"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val KEY_STREET = "street"
        private const val KEY_CITY = "city"
        private const val KEY_COUNTRY = "country"
        private const val KEY_CODE = "country_code"
        private const val KEY_POSTAL = "postal"
        private const val KEY_LAST_FIX_LAT = "last_fix_lat"
        private const val KEY_LAST_FIX_LON = "last_fix_lon"
        private const val KEY_LAST_FIX_ALT = "last_fix_alt"
        private const val KEY_LAST_FIX_TIME = "last_fix_time"
        private const val PERSISTED_FIX_MAX_AGE_MS = 60 * 60 * 1000L
        private const val OFFLINE_ADDRESS_REUSE_METERS = 300f
        private const val OFFLINE_REGION_REUSE_METERS = 5000f
        private const val TWO_MINUTES_MS = 120_000L
        private const val STALE_FIX_OVERRIDE_MS = 20_000L

        fun isBetterLocation(candidate: Location, currentBest: Location?): Boolean {
            if (currentBest == null) return true

            val timeDelta = candidate.time - currentBest.time
            val isSignificantlyNewer = timeDelta > TWO_MINUTES_MS
            val isSignificantlyOlder = timeDelta < -TWO_MINUTES_MS
            val isNewer = timeDelta > 0

            if (isSignificantlyNewer) return true
            if (isSignificantlyOlder) return false

            val accuracyDelta = (candidate.accuracy - currentBest.accuracy).toInt()
            val isLessAccurate = accuracyDelta > 0
            val isMoreAccurate = accuracyDelta < 0
            val isSignificantlyLessAccurate = accuracyDelta > 150
            val isFromSameProvider = candidate.provider == currentBest.provider
            val isGpsCandidate = candidate.provider == LocationManager.GPS_PROVIDER
            val currentIsNetwork = currentBest.provider == LocationManager.NETWORK_PROVIDER

            return when {
                isMoreAccurate -> true
                isNewer && !isLessAccurate -> true
                isNewer && isGpsCandidate && !isSignificantlyLessAccurate -> true
                timeDelta > STALE_FIX_OVERRIDE_MS && (!isSignificantlyLessAccurate || candidate.accuracy <= 65f) -> true
                isNewer && currentIsNetwork && !isGpsCandidate && candidate.accuracy <= 50f -> true
                isNewer && !isSignificantlyLessAccurate && isFromSameProvider -> true
                else -> false
            }
        }
    }
}
