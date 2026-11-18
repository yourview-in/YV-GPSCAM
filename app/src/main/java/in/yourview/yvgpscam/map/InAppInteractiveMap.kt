package `in`.yourview.yvgpscam.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.yourview.yvgpscam.data.local.AppDatabase
import `in`.yourview.yvgpscam.data.model.GpsTelemetry
import `in`.yourview.yvgpscam.data.model.MapStyle
import `in`.yourview.yvgpscam.ui.theme.JetBrainsMonoFontFamily
import `in`.yourview.yvgpscam.ui.theme.ObsidianBg
import `in`.yourview.yvgpscam.ui.theme.ObsidianBorder
import `in`.yourview.yvgpscam.ui.theme.ShutterGold
import `in`.yourview.yvgpscam.ui.theme.TelemetryEmerald
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

data class MapPinMarker(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val subtitle: String = "",
    val isSelected: Boolean = false,
    val isUserLocation: Boolean = false,
    val photoId: Long? = null
)

object InAppMapMath {
    private const val EARTH_CIRCUMFERENCE_METERS = 40_075_016.686

    fun latLonToNormalizedMercator(lat: Double, lon: Double): Pair<Double, Double> {
        val clampedLat = lat.coerceIn(-85.0511, 85.0511)
        val nx = ((lon + 180.0) / 360.0).mod(1.0)
        val latRad = Math.toRadians(clampedLat)
        val ny = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0).coerceIn(0.0, 1.0)
        return nx to ny
    }

    fun normalizedMercatorToLatLon(nx: Double, ny: Double): Pair<Double, Double> {
        val wrappedNx = nx.mod(1.0)
        val clampedNy = ny.coerceIn(0.001, 0.999)
        val lon = wrappedNx * 360.0 - 180.0
        val n = PI - 2.0 * PI * clampedNy
        val lat = Math.toDegrees(atan(sinh(n)))
        return lat to lon
    }

    fun metersToPixelsAtLatitude(meters: Double, lat: Double, worldPx: Double): Float {
        val cosLat = cos(Math.toRadians(lat.coerceIn(-85.0, 85.0))).coerceAtLeast(0.1)
        val metersPerPixel = (EARTH_CIRCUMFERENCE_METERS * cosLat) / worldPx
        return (meters / metersPerPixel).toFloat().coerceAtLeast(1f)
    }

    fun computeZoomToFitBounds(
        lats: List<Double>,
        lons: List<Double>,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        baseTilePx: Float
    ): Triple<Double, Double, Float> {
        if (lats.isEmpty() || lons.isEmpty()) return Triple(17.385044, 78.486671, 14f)
        if (lats.size == 1) return Triple(lats.first(), lons.first(), 15.4f)

        val minLat = lats.min()
        val maxLat = lats.max()
        val minLon = lons.min()
        val maxLon = lons.max()
        val centerLat = (minLat + maxLat) / 2.0
        val centerLon = (minLon + maxLon) / 2.0

        val (nx1, ny1) = latLonToNormalizedMercator(maxLat, minLon)
        val (nx2, ny2) = latLonToNormalizedMercator(minLat, maxLon)
        val spanX = kotlin.math.abs(nx2 - nx1).coerceAtLeast(0.00015)
        val spanY = kotlin.math.abs(ny2 - ny1).coerceAtLeast(0.00015)

        val usableW = (viewportWidthPx * 0.72f).coerceAtLeast(120f)
        val usableH = (viewportHeightPx * 0.68f).coerceAtLeast(120f)
        val zoomX = log2((usableW / (baseTilePx * spanX)).toDouble()).toFloat()
        val zoomY = log2((usableH / (baseTilePx * spanY)).toDouble()).toFloat()
        val targetZoom = minOf(zoomX, zoomY).coerceIn(3.5f, 16.5f)
        return Triple(centerLat, centerLon, targetZoom)
    }

    fun computeDistanceMetersAndBearing(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double
    ): Pair<Double, String> {
        val r = 6_371_000.0
        val lat1 = Math.toRadians(fromLat)
        val lat2 = Math.toRadians(toLat)
        val dLat = Math.toRadians(toLat - fromLat)
        val dLon = Math.toRadians(toLon - fromLon)
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        val c = 2.0 * atan2(sqrt(a), sqrt((1.0 - a).coerceAtLeast(0.0)))
        val distanceMeters = r * c

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val bearingDeg = (((Math.toDegrees(atan2(y, x)) % 360.0) + 360.0) % 360.0).toFloat()
        return distanceMeters to GpsTelemetry.azimuthToCardinal(bearingDeg)
    }

    fun formatDistance(distanceMeters: Double): String = when {
        distanceMeters < 1000.0 -> "${distanceMeters.roundToInt()} m"
        distanceMeters < 10_000.0 -> String.format(Locale.US, "%.2f km", distanceMeters / 1000.0)
        else -> String.format(Locale.US, "%.1f km", distanceMeters / 1000.0)
    }
}

private object SharedSlippyTileCache {
    private val memoryCache = object : LruCache<String, ImageBitmap>(180) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = 1
    }
    private val inFlightKeys = mutableSetOf<String>()
    private val networkSemaphore = Semaphore(4)

    fun get(key: String): ImageBitmap? = synchronized(this) {
        memoryCache.get(key)
    }

    fun put(key: String, bmp: Bitmap): ImageBitmap {
        val img = bmp.asImageBitmap()
        synchronized(this) {
            memoryCache.put(key, img)
        }
        return img
    }

    fun tryClaimFetch(key: String): Boolean = synchronized(this) {
        if (inFlightKeys.contains(key)) {
            false
        } else {
            inFlightKeys.add(key)
            true
        }
    }

    fun finishFetch(key: String) {
        synchronized(this) {
            inFlightKeys.remove(key)
        }
    }

    suspend fun <T> withPermit(block: suspend () -> T): T = networkSemaphore.withPermit { block() }
}

@Composable
fun InAppInteractiveTileMap(
    centerLat: Double,
    centerLon: Double,
    zoom: Float = 15f,
    mapStyle: MapStyle = MapStyle.STANDARD,
    offlineOnlyMapMode: Boolean = false,
    pins: List<MapPinMarker> = emptyList(),
    radiusCircleCenter: Pair<Double, Double>? = null,
    radiusCircleMeters: Double? = null,
    viewportSyncKey: Any? = null,
    showZoomControls: Boolean = true,
    showTileBadge: Boolean = true,
    onCameraMoved: ((Double, Double, Float) -> Unit)? = null,
    onMapTap: ((Double, Double) -> Unit)? = null,
    onPinTap: ((MapPinMarker) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val offlineRepo = remember(context) {
        OfflineMapRepository(
            context = context.applicationContext,
            regionDao = AppDatabase.getInstance(context.applicationContext).offlineRegionDao()
        )
    }

    var viewLat by remember { mutableStateOf(centerLat) }
    var viewLon by remember { mutableStateOf(centerLon) }
    var viewZoom by remember { mutableFloatStateOf(zoom.coerceIn(3f, 18f)) }
    var tileRevision by remember { mutableIntStateOf(0) }

    LaunchedEffect(centerLat, centerLon, zoom, viewportSyncKey) {
        viewLat = centerLat
        viewLon = centerLon
        viewZoom = zoom.coerceIn(3f, 18f)
    }

    val currentLatState by rememberUpdatedState(viewLat)
    val currentLonState by rememberUpdatedState(viewLon)
    val currentZoomState by rememberUpdatedState(viewZoom)
    val currentPinsState by rememberUpdatedState(pins)
    val currentOnPinTap by rememberUpdatedState(onPinTap)
    val currentOnMapTap by rememberUpdatedState(onMapTap)
    val currentOnCameraMoved by rememberUpdatedState(onCameraMoved)

    val baseTilePx = with(density) { 192.dp.toPx() }
    val tileZoom = floor(viewZoom).toInt().coerceIn(2, 17)
    val nTiles = 1 shl tileZoom
    val worldPx = baseTilePx * (2.0.pow(viewZoom.toDouble()))

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                when (mapStyle) {
                    MapStyle.SATELLITE, MapStyle.HYBRID -> Color(0xFF14211D)
                    MapStyle.TERRAIN -> Color(0xFFE5EBDD)
                    MapStyle.STANDARD -> Color(0xFFECE9E0)
                }
            )
    ) {
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

        val (centerNx, centerNy) = InAppMapMath.latLonToNormalizedMercator(viewLat, viewLon)
        val originX = widthPx / 2f - (centerNx * worldPx).toFloat()
        val originY = heightPx / 2f - (centerNy * worldPx).toFloat()
        val tileSizeOnScreen = (worldPx / nTiles).toFloat()

        val minIx = floor((-originX) / tileSizeOnScreen).toInt()
        val maxIx = floor((widthPx - originX) / tileSizeOnScreen).toInt()
        val minIy = floor((-originY) / tileSizeOnScreen).toInt().coerceIn(0, nTiles - 1)
        val maxIy = floor((heightPx - originY) / tileSizeOnScreen).toInt().coerceIn(0, nTiles - 1)

        // Request missing tiles asynchronously for current viewport
        LaunchedEffect(tileZoom, minIx, maxIx, minIy, maxIy, mapStyle, offlineOnlyMapMode) {
            for (ix in minIx..maxIx) {
                val tx = ((ix % nTiles) + nTiles) % nTiles
                for (iy in minIy..maxIy) {
                    val key = "${mapStyle.name}/$tileZoom/$tx/$iy"
                    val overlayKey = "HYBRID_OVERLAY/$tileZoom/$tx/$iy"
                    val needBase = SharedSlippyTileCache.get(key) == null
                    val needHybridOverlay = mapStyle == MapStyle.HYBRID && SharedSlippyTileCache.get(overlayKey) == null

                    if (needBase && SharedSlippyTileCache.tryClaimFetch(key)) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val cachedFile = offlineRepo.getTileFile(tileZoom, tx, iy, mapStyle)
                                val bmp = if (cachedFile.exists() && cachedFile.length() > 0L) {
                                    BitmapFactory.decodeFile(cachedFile.absolutePath)
                                } else if (!offlineOnlyMapMode) {
                                    SharedSlippyTileCache.withPermit {
                                        val bytes = offlineRepo.fetchTileFromNetworkAndCache(tileZoom, tx, iy, mapStyle)
                                        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                                    }
                                } else {
                                    null
                                }
                                if (bmp != null) {
                                    SharedSlippyTileCache.put(key, bmp)
                                    withContext(Dispatchers.Main) { tileRevision++ }
                                }
                            } finally {
                                SharedSlippyTileCache.finishFetch(key)
                            }
                        }
                    }

                    if (needHybridOverlay && !offlineOnlyMapMode && SharedSlippyTileCache.tryClaimFetch(overlayKey)) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                SharedSlippyTileCache.withPermit {
                                    val bytes = offlineRepo.fetchHybridOverlayTile(tileZoom, tx, iy)
                                    val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                                    if (bmp != null) {
                                        SharedSlippyTileCache.put(overlayKey, bmp)
                                        withContext(Dispatchers.Main) { tileRevision++ }
                                    }
                                }
                            } finally {
                                SharedSlippyTileCache.finishFetch(overlayKey)
                            }
                        }
                    }
                }
            }
        }

        val hitThresholdPx = with(density) { 28.dp.toPx() }
        var loadedVisibleTiles = 0

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(baseTilePx, widthPx, heightPx) {
                    detectTransformGestures { centroid, pan, zoomChange, _ ->
                        val oldZoom = currentZoomState
                        val newZoom = (oldZoom + log2(zoomChange.coerceIn(0.5f, 2f))).coerceIn(3f, 18f)
                        val oldWorldPx = baseTilePx * (2.0.pow(oldZoom.toDouble()))
                        val newWorldPx = baseTilePx * (2.0.pow(newZoom.toDouble()))
                        val (oldNx, oldNy) = InAppMapMath.latLonToNormalizedMercator(currentLatState, currentLonState)

                        val dxCenter = centroid.x - widthPx / 2f
                        val dyCenter = centroid.y - heightPx / 2f
                        val anchorNx = oldNx + dxCenter / oldWorldPx
                        val anchorNy = oldNy + dyCenter / oldWorldPx

                        val nextNx = anchorNx - (dxCenter + pan.x) / newWorldPx
                        val nextNy = anchorNy - (dyCenter + pan.y) / newWorldPx
                        val (nextLat, nextLon) = InAppMapMath.normalizedMercatorToLatLon(nextNx, nextNy)

                        viewLat = nextLat
                        viewLon = nextLon
                        viewZoom = newZoom
                        currentOnCameraMoved?.invoke(nextLat, nextLon, newZoom)
                    }
                }
                .pointerInput(baseTilePx, widthPx, heightPx) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            val curWorldPx = baseTilePx * (2.0.pow(currentZoomState.toDouble()))
                            val (curNx, curNy) = InAppMapMath.latLonToNormalizedMercator(currentLatState, currentLonState)

                            // Check interactive marker pins first (photo pins prioritized over GPS dot)
                            val sortedPins = currentPinsState.sortedBy { it.isUserLocation }
                            val hitPin = sortedPins.firstOrNull { pin ->
                                val (pnx, pny) = InAppMapMath.latLonToNormalizedMercator(pin.latitude, pin.longitude)
                                val sx = widthPx / 2f + ((pnx - curNx) * curWorldPx).toFloat()
                                val sy = heightPx / 2f + ((pny - curNy) * curWorldPx).toFloat()
                                val targetY = if (pin.isUserLocation) sy else sy - hitThresholdPx * 0.45f
                                hypot(tapOffset.x - sx, tapOffset.y - targetY) <= hitThresholdPx
                            }
                            if (hitPin != null && currentOnPinTap != null) {
                                currentOnPinTap?.invoke(hitPin)
                            } else {
                                val tapNx = curNx + (tapOffset.x - widthPx / 2f) / curWorldPx
                                val tapNy = curNy + (tapOffset.y - heightPx / 2f) / curWorldPx
                                val (tapLat, tapLon) = InAppMapMath.normalizedMercatorToLatLon(tapNx, tapNy)
                                currentOnMapTap?.invoke(tapLat, tapLon)
                            }
                        },
                        onDoubleTap = { tapOffset ->
                            val oldZoom = currentZoomState
                            val newZoom = (oldZoom + 1.2f).coerceIn(3f, 18f)
                            val oldWorldPx = baseTilePx * (2.0.pow(oldZoom.toDouble()))
                            val newWorldPx = baseTilePx * (2.0.pow(newZoom.toDouble()))
                            val (oldNx, oldNy) = InAppMapMath.latLonToNormalizedMercator(currentLatState, currentLonState)
                            val dx = tapOffset.x - widthPx / 2f
                            val dy = tapOffset.y - heightPx / 2f
                            val anchorNx = oldNx + dx / oldWorldPx
                            val anchorNy = oldNy + dy / oldWorldPx
                            val (nextLat, nextLon) = InAppMapMath.normalizedMercatorToLatLon(
                                anchorNx - dx / newWorldPx,
                                anchorNy - dy / newWorldPx
                            )
                            viewLat = nextLat
                            viewLon = nextLon
                            viewZoom = newZoom
                            currentOnCameraMoved?.invoke(nextLat, nextLon, newZoom)
                        }
                    )
                }
        ) {
            // Read tileRevision so Canvas redraws as background tiles arrive
            val rev = tileRevision
            if (rev < 0) return@Canvas

            // 1. Crisp seamless cartographic grid backdrop so panning/offline always looks clean
            drawCartographicGridBackdrop(
                originX = originX,
                originY = originY,
                tileSizePx = tileSizeOnScreen,
                minIx = minIx,
                maxIx = maxIx,
                minIy = minIy,
                maxIy = maxIy,
                mapStyle = mapStyle
            )

            // 2. Render cached/downloaded Web Mercator map tiles
            for (ix in minIx..maxIx) {
                val tx = ((ix % nTiles) + nTiles) % nTiles
                val destX = (originX + ix * tileSizeOnScreen).roundToInt()
                val destEndX = (originX + (ix + 1) * tileSizeOnScreen).roundToInt()
                val destW = (destEndX - destX).coerceAtLeast(1)

                for (iy in minIy..maxIy) {
                    val key = "${mapStyle.name}/$tileZoom/$tx/$iy"
                    val tileBmp = SharedSlippyTileCache.get(key)
                    if (tileBmp != null) {
                        loadedVisibleTiles++
                        val destY = (originY + iy * tileSizeOnScreen).roundToInt()
                        val destEndY = (originY + (iy + 1) * tileSizeOnScreen).roundToInt()
                        val destH = (destEndY - destY).coerceAtLeast(1)
                        drawImage(
                            image = tileBmp,
                            dstOffset = IntOffset(destX, destY),
                            dstSize = IntSize(destW, destH)
                        )
                        if (mapStyle == MapStyle.HYBRID) {
                            val overlayBmp = SharedSlippyTileCache.get("HYBRID_OVERLAY/$tileZoom/$tx/$iy")
                            if (overlayBmp != null) {
                                drawImage(
                                    image = overlayBmp,
                                    dstOffset = IntOffset(destX, destY),
                                    dstSize = IntSize(destW, destH)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Optional Coverage Radius Circle (Offline Maps Region Picker)
            if (radiusCircleCenter != null && radiusCircleMeters != null && radiusCircleMeters > 0.0) {
                val (rLat, rLon) = radiusCircleCenter
                val (rnx, rny) = InAppMapMath.latLonToNormalizedMercator(rLat, rLon)
                val cx = widthPx / 2f + ((rnx - centerNx) * worldPx).toFloat()
                val cy = heightPx / 2f + ((rny - centerNy) * worldPx).toFloat()
                val radiusPx = InAppMapMath.metersToPixelsAtLatitude(radiusCircleMeters, rLat, worldPx)
                drawCircle(
                    color = TelemetryEmerald.copy(alpha = 0.16f),
                    radius = radiusPx,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = TelemetryEmerald,
                    radius = radiusPx,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }

            // 4. Draw GPS & Photo Marker Pins
            val sortedPins = pins.sortedWith(compareBy<MapPinMarker> { !it.isUserLocation }.thenBy { it.isSelected })
            sortedPins.forEach { pin ->
                val (pnx, pny) = InAppMapMath.latLonToNormalizedMercator(pin.latitude, pin.longitude)
                val sx = widthPx / 2f + ((pnx - centerNx) * worldPx).toFloat()
                val sy = heightPx / 2f + ((pny - centerNy) * worldPx).toFloat()
                if (sx in -80f..(widthPx + 80f) && sy in -80f..(heightPx + 80f)) {
                    if (pin.isUserLocation) {
                        drawUserLocationBeacon(center = Offset(sx, sy))
                    } else {
                        drawPhotoTeardropPin(
                            tip = Offset(sx, sy),
                            isSelected = pin.isSelected
                        )
                    }
                }
            }
        }

        // Top-Left Attribution / Offline Mode Badge
        AnimatedVisibility(
            visible = showTileBadge,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
        ) {
            Surface(
                color = ObsidianBg.copy(alpha = 0.84f),
                shape = CircleShape,
                modifier = Modifier.border(1.dp, ObsidianBorder, CircleShape)
            ) {
                Text(
                    text = when {
                        offlineOnlyMapMode -> "OFFLINE VECTOR / CACHED MAP"
                        loadedVisibleTiles > 0 -> "IN-APP MAP • Z${viewZoom.roundToInt()}"
                        else -> "SURVEY VECTOR MAP • Z${viewZoom.roundToInt()}"
                    },
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (offlineOnlyMapMode) ShutterGold else TelemetryEmerald,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        // Floating Zoom +/- Glass Capsule
        if (showZoomControls) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianBg.copy(alpha = 0.85f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            val nextZoom = (viewZoom + 1f).coerceIn(3f, 18f)
                            viewZoom = nextZoom
                            onCameraMoved?.invoke(viewLat, viewLon, nextZoom)
                        }
                        .testTag("inapp_map_zoom_in_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Zoom in map",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(width = 26.dp, height = 1.dp)
                        .background(Color.White.copy(alpha = 0.16f))
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            val nextZoom = (viewZoom - 1f).coerceIn(3f, 18f)
                            viewZoom = nextZoom
                            onCameraMoved?.invoke(viewLat, viewLon, nextZoom)
                        }
                        .testTag("inapp_map_zoom_out_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "Zoom out map",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawCartographicGridBackdrop(
    originX: Float,
    originY: Float,
    tileSizePx: Float,
    minIx: Int,
    maxIx: Int,
    minIy: Int,
    maxIy: Int,
    mapStyle: MapStyle
) {
    val gridColor = when (mapStyle) {
        MapStyle.SATELLITE, MapStyle.HYBRID -> Color.White.copy(alpha = 0.08f)
        MapStyle.TERRAIN -> Color(0xFF9FB08E).copy(alpha = 0.25f)
        MapStyle.STANDARD -> Color(0xFFC8C2B4).copy(alpha = 0.35f)
    }
    val blockColor = when (mapStyle) {
        MapStyle.SATELLITE, MapStyle.HYBRID -> Color(0xFF192B25)
        MapStyle.TERRAIN -> Color(0xFFDAE4CF)
        MapStyle.STANDARD -> Color(0xFFE3DFD3)
    }

    for (ix in minIx..maxIx) {
        val x = originX + ix * tileSizePx
        for (iy in minIy..maxIy) {
            val y = originY + iy * tileSizePx
            if ((ix + iy) % 2 == 0) {
                drawRect(
                    color = blockColor.copy(alpha = 0.45f),
                    topLeft = Offset(x, y),
                    size = Size(tileSizePx, tileSizePx)
                )
            }
            val subStep = tileSizePx / 4f
            for (s in 1..3) {
                val sx = x + s * subStep
                val sy = y + s * subStep
                drawLine(gridColor.copy(alpha = 0.45f), Offset(sx, y), Offset(sx, y + tileSizePx), strokeWidth = 0.8f)
                drawLine(gridColor.copy(alpha = 0.45f), Offset(x, sy), Offset(x + tileSizePx, sy), strokeWidth = 0.8f)
            }
            drawLine(gridColor, Offset(x, y), Offset(x, y + tileSizePx), strokeWidth = 1.4f)
            drawLine(gridColor, Offset(x, y), Offset(x + tileSizePx, y), strokeWidth = 1.4f)
        }
    }
}

private fun DrawScope.drawUserLocationBeacon(center: Offset) {
    val azure = Color(0xFF2196F3)
    drawCircle(
        color = azure.copy(alpha = 0.22f),
        radius = 18.dp.toPx(),
        center = center
    )
    drawCircle(
        color = Color.White,
        radius = 9.5.dp.toPx(),
        center = center
    )
    drawCircle(
        color = azure,
        radius = 6.5.dp.toPx(),
        center = center
    )
}

private fun DrawScope.drawPhotoTeardropPin(tip: Offset, isSelected: Boolean) {
    val scale = if (isSelected) 1.24f else 1.0f
    val fill = if (isSelected) ShutterGold else TelemetryEmerald
    val headRadius = 11.dp.toPx() * scale
    val headCenterY = tip.y - 22.dp.toPx() * scale
    val headCenter = Offset(tip.x, headCenterY)

    // Ground shadow
    drawCircle(
        color = Color.Black.copy(alpha = 0.34f),
        radius = 6.dp.toPx() * scale,
        center = tip
    )

    if (isSelected) {
        drawCircle(
            color = ShutterGold.copy(alpha = 0.28f),
            radius = headRadius * 1.55f,
            center = headCenter
        )
    }

    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x - headRadius * 0.78f, headCenterY + headRadius * 0.45f)
        lineTo(tip.x + headRadius * 0.78f, headCenterY + headRadius * 0.45f)
        close()
    }
    drawPath(path = path, color = fill)
    drawCircle(color = fill, radius = headRadius, center = headCenter)
    drawCircle(
        color = Color.White,
        radius = headRadius,
        center = headCenter,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    )
    drawCircle(
        color = Color(0xFF0A1410),
        radius = headRadius * 0.40f,
        center = headCenter
    )
}
