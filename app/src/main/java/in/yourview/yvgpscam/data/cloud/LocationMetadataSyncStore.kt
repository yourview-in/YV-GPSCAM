package `in`.yourview.yvgpscam.data.cloud

import android.content.Context
import android.location.Location
import `in`.yourview.yvgpscam.data.model.CapturedPhotoEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

data class CachedLocationMetadataEntry(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float?,
    val compassAzimuth: Float,
    val compassCardinal: String,
    val provider: String,
    val addressLine: String,
    val cityState: String,
    val country: String,
    val recordedAtMillis: Long,
    val isCaptureEvent: Boolean = false,
    val needsGeocode: Boolean = false,
    val syncedToCloud: Boolean = false
)

/**
 * Thread-safe persistent local queue for location updates and capture geotag metadata cached
 * while internet connectivity is unavailable. Drives the camera overlay's sync-pending indicator
 * and supplies batches to [in.yourview.yvgpscam.work.LocationMetadataSyncWorker].
 */
class LocationMetadataSyncStore internal constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val lock = Any()

    private val _entriesFlow = MutableStateFlow<List<CachedLocationMetadataEntry>>(emptyList())
    val entriesFlow: StateFlow<List<CachedLocationMetadataEntry>> = _entriesFlow.asStateFlow()

    private val _pendingCountFlow = MutableStateFlow(0)
    val pendingCountFlow: StateFlow<Int> = _pendingCountFlow.asStateFlow()

    private val _isSyncingFlow = MutableStateFlow(false)
    val isSyncingFlow: StateFlow<Boolean> = _isSyncingFlow.asStateFlow()

    private val _lastSyncedAtMillisFlow = MutableStateFlow(0L)
    val lastSyncedAtMillisFlow: StateFlow<Long> = _lastSyncedAtMillisFlow.asStateFlow()

    init {
        loadFromPrefsLocked()
    }

    fun recordOfflineLocation(
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double? = null,
        accuracyMeters: Float? = null,
        compassAzimuth: Float = 0f,
        compassCardinal: String = "N",
        provider: String = "GPS",
        addressLine: String = "",
        cityState: String = "",
        country: String = "",
        recordedAtMillis: Long = System.currentTimeMillis(),
        forceRecord: Boolean = false
    ): CachedLocationMetadataEntry? {
        synchronized(lock) {
            val current = readListLocked().toMutableList()
            val lastUnsynced = current.lastOrNull { !it.syncedToCloud }
            if (!forceRecord && lastUnsynced != null) {
                val dist = FloatArray(1)
                Location.distanceBetween(
                    lastUnsynced.latitude,
                    lastUnsynced.longitude,
                    latitude,
                    longitude,
                    dist
                )
                val ageMs = recordedAtMillis - lastUnsynced.recordedAtMillis
                if (dist[0] < MIN_CACHE_DISPLACEMENT_METERS && ageMs < MIN_CACHE_INTERVAL_MS) {
                    return null
                }
            }

            val entry = CachedLocationMetadataEntry(
                id = UUID.randomUUID().toString(),
                latitude = latitude,
                longitude = longitude,
                altitudeMeters = altitudeMeters,
                accuracyMeters = accuracyMeters,
                compassAzimuth = compassAzimuth,
                compassCardinal = compassCardinal,
                provider = provider,
                addressLine = addressLine,
                cityState = cityState,
                country = country,
                recordedAtMillis = recordedAtMillis,
                isCaptureEvent = false,
                needsGeocode = addressLine.isBlank() || cityState.isBlank(),
                syncedToCloud = false
            )
            current.add(entry)
            trimAndPersistLocked(current)
            return entry
        }
    }

    fun recordPendingCaptureMetadata(photo: CapturedPhotoEntity): CachedLocationMetadataEntry? {
        val lat = photo.latitude ?: return null
        val lon = photo.longitude ?: return null
        synchronized(lock) {
            val current = readListLocked().toMutableList()
            val entry = CachedLocationMetadataEntry(
                id = "cap_${photo.capturedAtMillis}_${photo.displayName.hashCode()}",
                latitude = lat,
                longitude = lon,
                altitudeMeters = photo.altitudeMeters,
                accuracyMeters = photo.accuracyMeters,
                compassAzimuth = photo.compassAzimuth,
                compassCardinal = photo.compassCardinal,
                provider = "CAPTURE",
                addressLine = photo.addressLine,
                cityState = photo.cityState,
                country = photo.country,
                recordedAtMillis = photo.capturedAtMillis,
                isCaptureEvent = true,
                needsGeocode = photo.addressLine.isBlank() || photo.cityState.isBlank(),
                syncedToCloud = false
            )
            current.removeAll { it.id == entry.id }
            current.add(entry)
            trimAndPersistLocked(current)
            return entry
        }
    }

    fun getPendingEntries(): List<CachedLocationMetadataEntry> {
        synchronized(lock) {
            return readListLocked().filter { !it.syncedToCloud }
        }
    }

    fun updateGeocodedEntry(
        id: String,
        addressLine: String,
        cityState: String,
        country: String
    ) {
        synchronized(lock) {
            val current = readListLocked().map { entry ->
                if (entry.id == id) {
                    entry.copy(
                        addressLine = addressLine.ifBlank { entry.addressLine },
                        cityState = cityState.ifBlank { entry.cityState },
                        country = country.ifBlank { entry.country },
                        needsGeocode = false
                    )
                } else {
                    entry
                }
            }
            trimAndPersistLocked(current.toMutableList())
        }
    }

    fun markEntriesSynced(
        ids: Collection<String>,
        syncedAtMillis: Long = System.currentTimeMillis()
    ) {
        if (ids.isEmpty()) return
        val idSet = ids.toSet()
        synchronized(lock) {
            val updated = readListLocked().map { entry ->
                if (entry.id in idSet) {
                    entry.copy(syncedToCloud = true, needsGeocode = false)
                } else {
                    entry
                }
            }.toMutableList()
            prefs.edit().putLong(KEY_LAST_SYNCED_AT, syncedAtMillis).apply()
            _lastSyncedAtMillisFlow.value = syncedAtMillis
            trimAndPersistLocked(updated)
        }
    }

    fun setSyncing(syncing: Boolean) {
        _isSyncingFlow.value = syncing
    }

    fun clearAll() {
        synchronized(lock) {
            prefs.edit()
                .remove(KEY_ENTRIES_JSON)
                .remove(KEY_LAST_SYNCED_AT)
                .commit()
            _entriesFlow.value = emptyList()
            _pendingCountFlow.value = 0
            _isSyncingFlow.value = false
            _lastSyncedAtMillisFlow.value = 0L
        }
    }

    fun buildSyncPayloadJson(entries: List<CachedLocationMetadataEntry>): String {
        val arr = JSONArray()
        for (entry in entries) {
            arr.put(entryToJson(entry))
        }
        return JSONObject().apply {
            put("brand", "YVGPSCAM")
            put("exportedAtMillis", System.currentTimeMillis())
            put("count", entries.size)
            put("locations", arr)
        }.toString(2)
    }

    private fun loadFromPrefsLocked() {
        synchronized(lock) {
            val entries = readListLocked()
            _entriesFlow.value = entries
            _pendingCountFlow.value = entries.count { !it.syncedToCloud }
            _lastSyncedAtMillisFlow.value = prefs.getLong(KEY_LAST_SYNCED_AT, 0L)
        }
    }

    private fun trimAndPersistLocked(list: MutableList<CachedLocationMetadataEntry>) {
        while (list.size > MAX_CACHED_ENTRIES) {
            val firstSyncedIdx = list.indexOfFirst { it.syncedToCloud }
            if (firstSyncedIdx >= 0) {
                list.removeAt(firstSyncedIdx)
            } else {
                list.removeAt(0)
            }
        }
        val arr = JSONArray()
        list.forEach { arr.put(entryToJson(it)) }
        prefs.edit().putString(KEY_ENTRIES_JSON, arr.toString()).apply()
        _entriesFlow.value = list.toList()
        _pendingCountFlow.value = list.count { !it.syncedToCloud }
    }

    private fun readListLocked(): List<CachedLocationMetadataEntry> {
        val raw = prefs.getString(KEY_ENTRIES_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(jsonToEntry(obj))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun entryToJson(entry: CachedLocationMetadataEntry): JSONObject {
        return JSONObject().apply {
            put("id", entry.id)
            put("lat", String.format(Locale.US, "%.6f", entry.latitude).toDouble())
            put("lon", String.format(Locale.US, "%.6f", entry.longitude).toDouble())
            entry.altitudeMeters?.let { put("alt", it) }
            entry.accuracyMeters?.let { put("acc", it.toDouble()) }
            put("azi", entry.compassAzimuth.toDouble())
            put("crd", entry.compassCardinal)
            put("provider", entry.provider)
            put("addr", entry.addressLine)
            put("city", entry.cityState)
            put("ctry", entry.country)
            put("ts", entry.recordedAtMillis)
            put("cap", entry.isCaptureEvent)
            put("geo", entry.needsGeocode)
            put("synced", entry.syncedToCloud)
        }
    }

    private fun jsonToEntry(obj: JSONObject): CachedLocationMetadataEntry {
        return CachedLocationMetadataEntry(
            id = obj.optString("id", UUID.randomUUID().toString()),
            latitude = obj.optDouble("lat", 0.0),
            longitude = obj.optDouble("lon", 0.0),
            altitudeMeters = if (obj.has("alt") && !obj.isNull("alt")) obj.optDouble("alt") else null,
            accuracyMeters = if (obj.has("acc") && !obj.isNull("acc")) obj.optDouble("acc").toFloat() else null,
            compassAzimuth = obj.optDouble("azi", 0.0).toFloat(),
            compassCardinal = obj.optString("crd", "N"),
            provider = obj.optString("provider", "GPS"),
            addressLine = obj.optString("addr", ""),
            cityState = obj.optString("city", ""),
            country = obj.optString("ctry", ""),
            recordedAtMillis = obj.optLong("ts", 0L),
            isCaptureEvent = obj.optBoolean("cap", false),
            needsGeocode = obj.optBoolean("geo", false),
            syncedToCloud = obj.optBoolean("synced", false)
        )
    }

    companion object {
        private const val PREFS_NAME = "yvgpscam_offline_location_sync"
        private const val KEY_ENTRIES_JSON = "entries_json"
        private const val KEY_LAST_SYNCED_AT = "last_synced_at"
        private const val MAX_CACHED_ENTRIES = 150
        private const val MIN_CACHE_DISPLACEMENT_METERS = 5f
        private const val MIN_CACHE_INTERVAL_MS = 15_000L

        @Volatile
        private var INSTANCE: LocationMetadataSyncStore? = null

        fun getInstance(context: Context): LocationMetadataSyncStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocationMetadataSyncStore(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
