package `in`.yourview.yvgpscam.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import `in`.yourview.yvgpscam.BuildConfig
import `in`.yourview.yvgpscam.data.cloud.NetworkConnectivityObserver
import `in`.yourview.yvgpscam.data.local.OfflineRegionDao
import `in`.yourview.yvgpscam.data.model.MapStyle
import `in`.yourview.yvgpscam.data.model.OfflineRegionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

data class TileCoord(val z: Int, val x: Int, val y: Int)

data class DownloadProgress(
    val isDownloading: Boolean = false,
    val regionName: String = "",
    val completedTiles: Int = 0,
    val totalTiles: Int = 0,
    val failedTiles: Int = 0,
    val errorMessage: String? = null
)

class OfflineMapRepository(
    private val context: Context,
    private val regionDao: OfflineRegionDao,
    private val httpClient: OkHttpClient = defaultFastTileClient(),
    private val isNetworkOnline: () -> Boolean = { NetworkConnectivityObserver.isNetworkConnected(context) }
) {
    private val tilesRootDir: File by lazy {
        File(context.filesDir, "offline_tiles").apply { mkdirs() }
    }

    @Volatile
    private var networkBackoffUntilMillis: Long = 0L

    @Volatile
    private var consecutiveNetworkFailures: Int = 0

    private val androidCertSha1: String by lazy {
        runCatching {
            val sig = if (android.os.Build.VERSION.SDK_INT >= 28) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
                ).signingInfo?.apkContentsSigners?.firstOrNull()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.GET_SIGNATURES
                ).signatures?.firstOrNull()
            }
            if (sig != null) {
                java.security.MessageDigest.getInstance("SHA-1")
                    .digest(sig.toByteArray())
                    .joinToString("") { "%02X".format(it) }
            } else {
                ""
            }
        }.getOrDefault("")
    }

    val savedRegions: Flow<List<OfflineRegionEntity>> = regionDao.getAllRegions()

    fun resetNetworkBackoff() {
        networkBackoffUntilMillis = 0L
        consecutiveNetworkFailures = 0
    }

    fun isNetworkUsableForTiles(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!isNetworkOnline()) return false
        return nowMillis >= networkBackoffUntilMillis
    }

    fun getTileFile(z: Int, x: Int, y: Int, mapStyle: MapStyle = MapStyle.STANDARD): File {
        return when (mapStyle) {
            MapStyle.STANDARD -> File(tilesRootDir, "$z/$x/$y.png")
            MapStyle.SATELLITE -> File(tilesRootDir, "satellite/$z/$x/$y.jpg")
            MapStyle.TERRAIN -> File(tilesRootDir, "terrain/$z/$x/$y.png")
            MapStyle.HYBRID -> File(tilesRootDir, "hybrid/$z/$x/$y.jpg")
        }
    }

    fun hasCachedTile(z: Int, x: Int, y: Int, mapStyle: MapStyle = MapStyle.STANDARD): Boolean {
        val file = getTileFile(z, x, y, mapStyle)
        return file.exists() && file.length() > 0L
    }

    fun saveTileBytes(z: Int, x: Int, y: Int, bytes: ByteArray, mapStyle: MapStyle = MapStyle.STANDARD) {
        val file = getTileFile(z, x, y, mapStyle)
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { it.write(bytes) }
    }

    /**
     * Loads the exact cached tile bitmap if present, or crops/upscales from a cached
     * ancestor parent zoom tile (up to 3 zoom levels out) so downloaded offline areas
     * keep rendering even when the user zooms in or pans between zoom steps without network.
     */
    fun getBestAvailableCachedTileBitmap(
        z: Int,
        x: Int,
        y: Int,
        mapStyle: MapStyle = MapStyle.STANDARD
    ): Bitmap? {
        val exactFile = getTileFile(z, x, y, mapStyle)
        if (exactFile.exists() && exactFile.length() > 0L) {
            BitmapFactory.decodeFile(exactFile.absolutePath)?.let { return it }
        }

        for (delta in 1..4) {
            val parentZ = z - delta
            if (parentZ < 1) break
            val parentX = x shr delta
            val parentY = y shr delta
            val parentFile = getTileFile(parentZ, parentX, parentY, mapStyle)
            if (parentFile.exists() && parentFile.length() > 0L) {
                val parentBmp = BitmapFactory.decodeFile(parentFile.absolutePath) ?: continue
                val scale = 1 shl delta
                val subX = x and (scale - 1)
                val subY = y and (scale - 1)
                val srcW = (parentBmp.width / scale).coerceAtLeast(1)
                val srcH = (parentBmp.height / scale).coerceAtLeast(1)
                val srcX = (subX * srcW).coerceIn(0, (parentBmp.width - srcW).coerceAtLeast(0))
                val srcY = (subY * srcH).coerceIn(0, (parentBmp.height - srcH).coerceAtLeast(0))

                val cropped = Bitmap.createBitmap(parentBmp, srcX, srcY, srcW, srcH)
                val upscaled = Bitmap.createScaledBitmap(cropped, 256, 256, true)
                if (cropped !== upscaled && cropped !== parentBmp) {
                    cropped.recycle()
                }
                if (parentBmp !== upscaled) {
                    parentBmp.recycle()
                }
                return upscaled
            }
        }
        return null
    }

    /**
     * Finds the closest cached Google Static Map image within [maxDistanceMeters] of a visited
     * coordinate so revisiting or drifting within a previously visited area still renders offline.
     */
    fun findNearestCachedStaticMapBytes(
        lat: Double,
        lon: Double,
        zoom: Int,
        mapStyle: MapStyle,
        maxDistanceMeters: Double = 350.0
    ): ByteArray? {
        val staticDir = File(tilesRootDir, "google_static")
        if (!staticDir.exists() || !staticDir.isDirectory) return null
        val prefix = "${mapStyle.name}_z${zoom}_"
        val candidates = staticDir.listFiles { file ->
            file.isFile && file.name.startsWith(prefix) && file.name.endsWith(".png") && file.length() > 0L
        } ?: return null

        var bestFile: File? = null
        var bestDistance = maxDistanceMeters
        val distResult = FloatArray(1)

        for (file in candidates) {
            val coordsPart = file.name.removePrefix(prefix).removeSuffix(".png")
            val split = coordsPart.split('_')
            if (split.size != 2) continue
            val cLat = split[0].toDoubleOrNull() ?: continue
            val cLon = split[1].toDoubleOrNull() ?: continue
            android.location.Location.distanceBetween(lat, lon, cLat, cLon, distResult)
            if (distResult[0].toDouble() <= bestDistance) {
                bestDistance = distResult[0].toDouble()
                bestFile = file
            }
        }
        return bestFile?.let { f -> runCatching { f.readBytes() }.getOrNull() }
    }

    /**
     * Silently populates the slippy disk tile cache around a visited coordinate while online
     * so Google Maps SDK TileProvider and MiniMapTileRenderer can render visited areas offline.
     */
    fun cacheVisitedAreaTiles(
        lat: Double,
        lon: Double,
        zoom: Int = 15,
        mapStyle: MapStyle = MapStyle.STANDARD
    ): Int {
        if (!isNetworkUsableForTiles()) return 0
        val clampedZoom = zoom.coerceIn(10, 17)
        val maxIdx = (1 shl clampedZoom) - 1
        val centerX = lonToTileX(lon, clampedZoom).coerceIn(0, maxIdx)
        val centerY = latToTileY(lat, clampedZoom).coerceIn(0, maxIdx)

        var cachedCount = 0
        val targets = mutableListOf(TileCoord(clampedZoom, centerX, centerY))
        // Pre-warm parent zoom tile plus immediate neighbors (3x3) for smooth panning/drift offline
        if (clampedZoom > 10) {
            targets.add(TileCoord(clampedZoom - 1, centerX shr 1, centerY shr 1))
        }
        for (dx in -1..1) {
            for (dy in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = (centerX + dx).coerceIn(0, maxIdx)
                val ny = (centerY + dy).coerceIn(0, maxIdx)
                targets.add(TileCoord(clampedZoom, nx, ny))
            }
        }

        for (tile in targets) {
            if (!isNetworkUsableForTiles()) break
            if (!hasCachedTile(tile.z, tile.x, tile.y, mapStyle)) {
                val bytes = fetchTileFromNetworkAndCache(tile.z, tile.x, tile.y, mapStyle)
                if (bytes != null) cachedCount++
            }
        }
        return cachedCount
    }

    fun fetchGoogleStaticMapAndCache(
        lat: Double,
        lon: Double,
        zoom: Int,
        outputSizePx: Int,
        mapStyle: MapStyle,
        allowNetwork: Boolean = true
    ): ByteArray? {
        val qLat = String.format(Locale.US, "%.5f", lat)
        val qLon = String.format(Locale.US, "%.5f", lon)
        val cacheFile = File(tilesRootDir, "google_static/${mapStyle.name}_z${zoom}_${qLat}_${qLon}.png")
        if (cacheFile.exists() && cacheFile.length() > 0L) {
            return runCatching { cacheFile.readBytes() }.getOrNull()
        }

        if (!allowNetwork || !GoogleMapsConfig.isApiKeyConfigured() || !isNetworkUsableForTiles()) {
            return findNearestCachedStaticMapBytes(lat, lon, zoom, mapStyle)
        }

        val googleMapType = when (mapStyle) {
            MapStyle.STANDARD -> "roadmap"
            MapStyle.SATELLITE -> "satellite"
            MapStyle.TERRAIN -> "terrain"
            MapStyle.HYBRID -> "hybrid"
        }
        val reqSize = outputSizePx.coerceIn(180, 400)
        val url = String.format(
            Locale.US,
            "https://maps.googleapis.com/maps/api/staticmap?center=%.6f,%.6f&zoom=%d&size=%dx%d&scale=2&maptype=%s&key=%s",
            lat,
            lon,
            zoom,
            reqSize,
            reqSize,
            googleMapType,
            BuildConfig.MAPS_API_KEY.trim()
        )
        val bytes = executeTileRequest(url)
        if (bytes != null && isValidImageBytes(bytes)) {
            cacheFile.parentFile?.mkdirs()
            runCatching { FileOutputStream(cacheFile).use { it.write(bytes) } }
            return bytes
        }
        return findNearestCachedStaticMapBytes(lat, lon, zoom, mapStyle)
    }

    fun fetchHybridOverlayTile(
        z: Int,
        x: Int,
        y: Int,
        allowNetwork: Boolean = true
    ): ByteArray? {
        val cacheFile = File(tilesRootDir, "hybrid_overlay/$z/$x/$y.png")
        if (cacheFile.exists() && cacheFile.length() > 0L) {
            return runCatching { cacheFile.readBytes() }.getOrNull()
        }
        if (!allowNetwork || !isNetworkUsableForTiles()) return null
        val urls = listOf(
            "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/$z/$y/$x",
            "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Transportation/MapServer/tile/$z/$y/$x"
        )
        for (url in urls) {
            if (!isNetworkUsableForTiles()) return null
            val bytes = executeTileRequest(url)
            if (bytes != null && isValidImageBytes(bytes)) {
                cacheFile.parentFile?.mkdirs()
                runCatching { FileOutputStream(cacheFile).use { it.write(bytes) } }
                return bytes
            }
        }
        return null
    }

    fun fetchTileFromNetworkAndCache(z: Int, x: Int, y: Int, mapStyle: MapStyle = MapStyle.STANDARD): ByteArray? {
        if (!isNetworkUsableForTiles()) return null
        if (GoogleMapsConfig.isApiKeyConfigured()) {
            val n = 2.0.pow(z.toDouble())
            val tileLon = ((x + 0.5) / n) * 360.0 - 180.0
            val latRad = atan(sinh(PI * (1.0 - 2.0 * (y + 0.5) / n)))
            val tileLat = Math.toDegrees(latRad)
            val googleMapType = when (mapStyle) {
                MapStyle.STANDARD -> "roadmap"
                MapStyle.SATELLITE -> "satellite"
                MapStyle.TERRAIN -> "terrain"
                MapStyle.HYBRID -> "hybrid"
            }
            val googleUrl = String.format(
                Locale.US,
                "https://maps.googleapis.com/maps/api/staticmap?center=%.6f,%.6f&zoom=%d&size=256x256&scale=2&maptype=%s&key=%s",
                tileLat,
                tileLon,
                z.coerceIn(1, 20),
                googleMapType,
                BuildConfig.MAPS_API_KEY.trim()
            )
            val googleBytes = executeTileRequest(googleUrl)
            if (googleBytes != null && isValidImageBytes(googleBytes)) {
                saveTileBytes(z, x, y, googleBytes, mapStyle)
                return googleBytes
            }
            if (!isNetworkUsableForTiles()) return null
        }
        val candidateUrls = when (mapStyle) {
            MapStyle.STANDARD -> listOf(
                "https://basemaps.cartocdn.com/rastertiles/voyager/$z/$x/$y.png",
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/$z/$y/$x",
                "https://tile.openstreetmap.org/$z/$x/$y.png"
            )
            MapStyle.SATELLITE -> listOf(
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
            )
            MapStyle.TERRAIN -> listOf(
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/$z/$y/$x",
                "https://tile.opentopomap.org/$z/$x/$y.png"
            )
            MapStyle.HYBRID -> listOf(
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
            )
        }
        for (url in candidateUrls) {
            if (!isNetworkUsableForTiles()) return null
            val bytes = executeTileRequest(url)
            if (bytes != null && isValidImageBytes(bytes)) {
                saveTileBytes(z, x, y, bytes, mapStyle)
                return bytes
            }
        }
        return null
    }

    private fun executeTileRequest(url: String): ByteArray? {
        if (!isNetworkUsableForTiles()) return null
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "YVGPSCAM-Android/1.0 (Geotag Camera; Android)")
        if (url.contains("googleapis.com")) {
            reqBuilder.header("X-Android-Package", context.packageName)
            if (androidCertSha1.isNotEmpty()) {
                reqBuilder.header("X-Android-Cert", androidCertSha1)
            }
        }
        val request = reqBuilder.build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        consecutiveNetworkFailures = 0
                        networkBackoffUntilMillis = 0L
                        bytes
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: IOException) {
            val failures = ++consecutiveNetworkFailures
            val isConnectivityDrop = e is UnknownHostException ||
                e is ConnectException ||
                e is NoRouteToHostException ||
                e is SocketTimeoutException ||
                failures >= 2
            if (isConnectivityDrop) {
                networkBackoffUntilMillis = System.currentTimeMillis() + CIRCUIT_BREAKER_BACKOFF_MS
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun isValidImageBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 16) return false
        val isPng = bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte()
        val isJpg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        val isWebp = bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[8] == 'W'.code.toByte()
        return isPng || isJpg || isWebp
    }

    fun estimateTilesForRegion(
        centerLat: Double,
        centerLon: Double,
        radiusKm: Double,
        zoomMin: Int,
        zoomMax: Int
    ): List<TileCoord> {
        val latDelta = radiusKm / 111.0
        val cosLat = cos(Math.toRadians(centerLat)).coerceAtLeast(0.1)
        val lonDelta = radiusKm / (111.32 * cosLat)

        val minLat = (centerLat - latDelta).coerceIn(-85.0, 85.0)
        val maxLat = (centerLat + latDelta).coerceIn(-85.0, 85.0)
        val minLon = (centerLon - lonDelta).coerceIn(-180.0, 180.0)
        val maxLon = (centerLon + lonDelta).coerceIn(-180.0, 180.0)

        val result = mutableListOf<TileCoord>()
        for (z in zoomMin..zoomMax) {
            val n = 1 shl z
            val x1 = lonToTileX(minLon, z).coerceIn(0, n - 1)
            val x2 = lonToTileX(maxLon, z).coerceIn(0, n - 1)
            val y1 = latToTileY(maxLat, z).coerceIn(0, n - 1)
            val y2 = latToTileY(minLat, z).coerceIn(0, n - 1)
            for (x in minOf(x1, x2)..maxOf(x1, x2)) {
                for (y in minOf(y1, y2)..maxOf(y1, y2)) {
                    result.add(TileCoord(z, x, y))
                    if (result.size >= 360) return result
                }
            }
        }
        return result
    }

    suspend fun downloadRegion(
        regionName: String,
        centerLat: Double,
        centerLon: Double,
        radiusKm: Double,
        zoomMin: Int = 14,
        zoomMax: Int = 16,
        onProgress: (DownloadProgress) -> Unit
    ): Result<OfflineRegionEntity> = withContext(Dispatchers.IO) {
        val tiles = estimateTilesForRegion(centerLat, centerLon, radiusKm, zoomMin, zoomMax)
        if (tiles.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No tiles found for region"))
        }

        val hasUncachedTiles = tiles.any { !hasCachedTile(it.z, it.x, it.y) }
        if (hasUncachedTiles && !isNetworkOnline()) {
            return@withContext Result.failure(
                IllegalStateException("Unable to download map tiles: no internet connection. Offline vector grid remains active.")
            )
        }

        var completed = 0
        var failed = 0
        var consecutiveNetworkFails = 0
        var totalBytes = 0L

        onProgress(
            DownloadProgress(
                isDownloading = true,
                regionName = regionName,
                completedTiles = 0,
                totalTiles = tiles.size
            )
        )

        for (tile in tiles) {
            val existing = getTileFile(tile.z, tile.x, tile.y)
            if (existing.exists() && existing.length() > 0L) {
                totalBytes += existing.length()
                completed++
                consecutiveNetworkFails = 0
            } else {
                val bytes = fetchTileFromNetworkAndCache(tile.z, tile.x, tile.y)
                if (bytes != null) {
                    totalBytes += bytes.size
                    completed++
                    consecutiveNetworkFails = 0
                } else {
                    failed++
                    consecutiveNetworkFails++
                    if (consecutiveNetworkFails >= 4 && !isNetworkUsableForTiles()) {
                        break
                    }
                }
            }
            onProgress(
                DownloadProgress(
                    isDownloading = true,
                    regionName = regionName,
                    completedTiles = completed + failed,
                    totalTiles = tiles.size,
                    failedTiles = failed
                )
            )
        }

        if (completed == 0) {
            return@withContext Result.failure(
                IllegalStateException("Unable to download map tiles. Check internet connectivity.")
            )
        }

        val entity = OfflineRegionEntity(
            name = regionName.ifBlank { "Offline Area" },
            centerLat = centerLat,
            centerLon = centerLon,
            radiusKm = radiusKm,
            zoomMin = zoomMin,
            zoomMax = zoomMax,
            tileCount = completed,
            sizeBytes = totalBytes,
            downloadedAtMillis = System.currentTimeMillis()
        )
        val id = regionDao.insertRegion(entity)
        Result.success(entity.copy(id = id))
    }

    suspend fun deleteRegion(region: OfflineRegionEntity) = withContext(Dispatchers.IO) {
        val tiles = estimateTilesForRegion(
            region.centerLat,
            region.centerLon,
            region.radiusKm,
            region.zoomMin,
            region.zoomMax
        )
        tiles.forEach { tile ->
            val f = getTileFile(tile.z, tile.x, tile.y)
            if (f.exists()) f.delete()
        }
        regionDao.deleteRegionById(region.id)
    }

    suspend fun getTotalDiskCacheBytes(): Long = withContext(Dispatchers.IO) {
        tilesRootDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun getTotalCachedTileCount(): Int = withContext(Dispatchers.IO) {
        tilesRootDir.walkTopDown().count { it.isFile && (it.extension == "png" || it.extension == "jpg") }
    }

    companion object {
        private const val CIRCUIT_BREAKER_BACKOFF_MS = 12_000L

        fun defaultFastTileClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(3000, TimeUnit.MILLISECONDS)
            .writeTimeout(3000, TimeUnit.MILLISECONDS)
            .callTimeout(4000, TimeUnit.MILLISECONDS)
            .build()

        fun lonToTileX(lon: Double, zoom: Int): Int {
            return floor((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()
        }

        fun latToTileY(lat: Double, zoom: Int): Int {
            val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
            return floor(
                (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * (1 shl zoom)
            ).toInt()
        }

        fun lonToFractionalTileX(lon: Double, zoom: Int): Double {
            return (lon + 180.0) / 360.0 * (1 shl zoom)
        }

        fun latToFractionalTileY(lat: Double, zoom: Int): Double {
            val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
            return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * (1 shl zoom)
        }

        fun tileYToLat(y: Double, zoom: Int): Double {
            val n = PI - 2.0 * PI * y / 2.0.pow(zoom)
            return Math.toDegrees(atan(sinh(n)))
        }
    }
}
