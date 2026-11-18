package `in`.yourview.yvgpscam.map

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import `in`.yourview.yvgpscam.BuildConfig
import `in`.yourview.yvgpscam.data.cloud.NetworkConnectivityObserver
import `in`.yourview.yvgpscam.data.local.AppDatabase
import `in`.yourview.yvgpscam.data.model.CapturedPhotoEntity
import `in`.yourview.yvgpscam.data.model.GpsTelemetry
import `in`.yourview.yvgpscam.data.model.MapStyle
import `in`.yourview.yvgpscam.ui.theme.JetBrainsMonoFontFamily
import `in`.yourview.yvgpscam.ui.theme.ObsidianBg
import `in`.yourview.yvgpscam.ui.theme.ObsidianBorder
import `in`.yourview.yvgpscam.ui.theme.ObsidianSurface
import `in`.yourview.yvgpscam.ui.theme.ObsidianSurfaceHigh
import `in`.yourview.yvgpscam.ui.theme.TelemetryEmerald
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.rememberCameraPositionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GoogleMapsConfig {
    fun isApiKeyConfigured(rawKey: String = BuildConfig.MAPS_API_KEY): Boolean {
        val trimmed = rawKey.trim()
        return trimmed.isNotEmpty() &&
            !trimmed.startsWith("MY_", ignoreCase = true) &&
            !trimmed.equals("null", ignoreCase = true)
    }
}

fun MapStyle.toGoogleMapType(): MapType = when (this) {
    MapStyle.STANDARD -> MapType.NORMAL
    MapStyle.SATELLITE -> MapType.SATELLITE
    MapStyle.TERRAIN -> MapType.TERRAIN
    MapStyle.HYBRID -> MapType.HYBRID
}

@Composable
fun rememberDiskCachedMapTileProvider(
    mapStyle: MapStyle,
    offlineOnlyMapMode: Boolean
): DiskCachedMapTileProvider {
    val context = LocalContext.current.applicationContext
    val offlineRepo = remember(context) {
        OfflineMapRepository(
            context = context,
            regionDao = AppDatabase.getInstance(context).offlineRegionDao()
        )
    }
    return remember(offlineRepo, mapStyle, offlineOnlyMapMode) {
        DiskCachedMapTileProvider(
            offlineMapRepository = offlineRepo,
            mapStyle = mapStyle,
            allowNetwork = {
                !offlineOnlyMapMode && NetworkConnectivityObserver.isNetworkConnected(context) &&
                    offlineRepo.isNetworkUsableForTiles()
            }
        )
    }
}

/**
 * Native Google Maps SDK map surface augmented with [DiskCachedMapTileProvider] as a [TileOverlay]
 * so previously visited areas and offline-cached regions render seamlessly even if connectivity drops.
 */
@Composable
fun GoogleSdkCachedTileMap(
    latitude: Double,
    longitude: Double,
    zoom: Float = 15f,
    mapStyle: MapStyle = MapStyle.STANDARD,
    offlineOnlyMapMode: Boolean = false,
    markerTitle: String? = null,
    modifier: Modifier = Modifier
) {
    val tileProvider = rememberDiskCachedMapTileProvider(
        mapStyle = mapStyle,
        offlineOnlyMapMode = offlineOnlyMapMode
    )
    val targetLatLng = remember(latitude, longitude) { LatLng(latitude, longitude) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(targetLatLng, zoom)
    }
    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            mapType = if (offlineOnlyMapMode) MapType.NONE else mapStyle.toGoogleMapType()
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = true,
            compassEnabled = true
        )
    ) {
        TileOverlay(
            tileProvider = tileProvider,
            fadeIn = false,
            zIndex = 1f
        )
        if (!markerTitle.isNullOrBlank()) {
            Marker(
                state = MarkerState(position = targetLatLng),
                title = markerTitle
            )
        }
    }
}

@Composable
fun PhotoDetailGoogleMap(
    latitude: Double,
    longitude: Double,
    title: String,
    snippet: String,
    mapStyle: MapStyle,
    offlineOnlyMapMode: Boolean,
    fallbackBitmap: Bitmap?,
    onOpenFullMap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val pin = remember(latitude, longitude, title, snippet) {
        listOf(
            MapPinMarker(
                id = "detail_photo_pin",
                latitude = latitude,
                longitude = longitude,
                title = title,
                subtitle = snippet,
                isSelected = true
            )
        )
    }
    var syncNonce by remember(latitude, longitude) { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianSurfaceHigh)
            .border(1.dp, TelemetryEmerald.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
    ) {
        InAppInteractiveTileMap(
            centerLat = latitude,
            centerLon = longitude,
            zoom = 15.5f,
            mapStyle = mapStyle,
            offlineOnlyMapMode = offlineOnlyMapMode,
            pins = pin,
            viewportSyncKey = syncNonce,
            showZoomControls = true,
            showTileBadge = true,
            onPinTap = {
                if (onOpenFullMap != null) onOpenFullMap() else syncNonce++
            }
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = ObsidianBg.copy(alpha = 0.86f),
                shape = CircleShape,
                modifier = Modifier
                    .clip(CircleShape)
                    .border(1.dp, ObsidianBorder, CircleShape)
                    .clickable { syncNonce++ }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CenterFocusStrong,
                        contentDescription = "Center on capture pin",
                        tint = TelemetryEmerald,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Center Pin",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (onOpenFullMap != null) {
                Surface(
                    color = TelemetryEmerald,
                    shape = CircleShape,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onOpenFullMap() }
                        .testTag("detail_expand_inapp_map_chip")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInFull,
                            contentDescription = "Expand interactive in-app map",
                            tint = ObsidianBg,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Full Map",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ObsidianBg
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RegionPickerGoogleMap(
    centerLat: Double,
    centerLon: Double,
    radiusKm: Double,
    mapStyle: MapStyle,
    offlineOnlyMapMode: Boolean,
    onLocationPicked: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val zoomForRadius = remember(radiusKm) {
        when {
            radiusKm <= 1.2 -> 14.0f
            radiusKm <= 2.5 -> 13.0f
            else -> 12.0f
        }
    }
    val pin = remember(centerLat, centerLon) {
        listOf(
            MapPinMarker(
                id = "region_center_pin",
                latitude = centerLat,
                longitude = centerLon,
                title = "Download Region Center",
                isSelected = true
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianSurfaceHigh)
            .border(1.dp, TelemetryEmerald.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .testTag("offline_region_map_picker")
    ) {
        InAppInteractiveTileMap(
            centerLat = centerLat,
            centerLon = centerLon,
            zoom = zoomForRadius,
            mapStyle = mapStyle,
            offlineOnlyMapMode = offlineOnlyMapMode,
            pins = pin,
            radiusCircleCenter = centerLat to centerLon,
            radiusCircleMeters = radiusKm * 1000.0,
            viewportSyncKey = Triple(centerLat, centerLon, zoomForRadius),
            showZoomControls = true,
            showTileBadge = false,
            onMapTap = { lat, lon -> onLocationPicked(lat, lon) }
        )

        Surface(
            color = ObsidianBg.copy(alpha = 0.88f),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .border(1.dp, ObsidianBorder, CircleShape)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.TouchApp,
                    contentDescription = null,
                    tint = TelemetryEmerald,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Tap map to set ${radiusKm.toInt()}km area",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Surface(
            color = ObsidianBg.copy(alpha = 0.86f),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .border(1.dp, ObsidianBorder, CircleShape)
        ) {
            Text(
                text = String.format(Locale.US, "%.4f, %.4f", centerLat, centerLon),
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 10.sp,
                color = TelemetryEmerald,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun GalleryMultiPhotoGoogleMap(
    photos: List<CapturedPhotoEntity>,
    mapStyle: MapStyle,
    offlineOnlyMapMode: Boolean,
    onInspectPhoto: (Long) -> Unit,
    onSharePhoto: (CapturedPhotoEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val geotaggedPhotos = remember(photos) {
        photos.filter { it.latitude != null && it.longitude != null }
    }
    var selectedPhotoId by remember(geotaggedPhotos) {
        mutableStateOf(geotaggedPhotos.firstOrNull()?.id)
    }
    val selectedPhoto = remember(selectedPhotoId, geotaggedPhotos) {
        geotaggedPhotos.firstOrNull { it.id == selectedPhotoId }
    }

    if (geotaggedPhotos.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = ObsidianSurface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = TelemetryEmerald,
                        modifier = Modifier.size(38.dp)
                    )
                    Text(
                        text = "No Geotagged Captures Yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Capture photos with GPS lock enabled to explore your survey pins inside the interactive map.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        return
    }

    val first = geotaggedPhotos.first()
    var targetLat by remember { mutableStateOf(first.latitude ?: 17.385044) }
    var targetLon by remember { mutableStateOf(first.longitude ?: 78.486671) }
    var targetZoom by remember { mutableFloatStateOf(14.5f) }
    var syncNonce by remember { mutableIntStateOf(0) }

    val markers = remember(geotaggedPhotos, selectedPhotoId) {
        geotaggedPhotos.mapNotNull { photo ->
            val lat = photo.latitude ?: return@mapNotNull null
            val lon = photo.longitude ?: return@mapNotNull null
            MapPinMarker(
                id = "gallery_pin_${photo.id}",
                latitude = lat,
                longitude = lon,
                title = photo.displayName,
                subtitle = photo.cityState,
                isSelected = photo.id == selectedPhotoId,
                photoId = photo.id
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp))
            .testTag("gallery_google_map_container")
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val baseTilePx = with(density) { 192.dp.toPx() }

        InAppInteractiveTileMap(
            centerLat = targetLat,
            centerLon = targetLon,
            zoom = targetZoom,
            mapStyle = mapStyle,
            offlineOnlyMapMode = offlineOnlyMapMode,
            pins = markers,
            viewportSyncKey = syncNonce,
            showZoomControls = true,
            showTileBadge = false,
            onMapTap = { _, _ -> selectedPhotoId = null },
            onPinTap = { marker ->
                selectedPhotoId = marker.photoId
            }
        )

        // Top Floating Pin Counter & Fit All Button
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = ObsidianBg.copy(alpha = 0.88f),
                shape = CircleShape,
                modifier = Modifier.border(1.dp, ObsidianBorder, CircleShape)
            ) {
                Text(
                    text = "${geotaggedPhotos.size} pins",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TelemetryEmerald,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }

            Surface(
                color = ObsidianBg.copy(alpha = 0.88f),
                shape = CircleShape,
                modifier = Modifier
                    .clip(CircleShape)
                    .border(1.dp, TelemetryEmerald.copy(alpha = 0.55f), CircleShape)
                    .clickable {
                        val (lat, lon, z) = InAppMapMath.computeZoomToFitBounds(
                            lats = geotaggedPhotos.mapNotNull { it.latitude },
                            lons = geotaggedPhotos.mapNotNull { it.longitude },
                            viewportWidthPx = widthPx,
                            viewportHeightPx = heightPx,
                            baseTilePx = baseTilePx
                        )
                        targetLat = lat
                        targetLon = lon
                        targetZoom = z
                        syncNonce++
                    }
                    .testTag("gallery_map_fit_all_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FitScreen,
                        contentDescription = "Fit all photo pins on map",
                        tint = TelemetryEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Fit All",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Selected Photo Bottom Preview Card
        selectedPhoto?.let { photo ->
            val dateText = remember(photo.capturedAtMillis) {
                SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(photo.capturedAtMillis))
            }
            Surface(
                color = ObsidianBg.copy(alpha = 0.94f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(10.dp)
                    .border(1.dp, TelemetryEmerald.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                    .testTag("gallery_map_selected_card")
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AsyncImage(
                            model = photo.mediaUri,
                            contentDescription = photo.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(62.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
                                .clickable { onInspectPhoto(photo.id) }
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = photo.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = photo.cityState.ifBlank { photo.addressLine }.ifBlank { "Captured Location" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (photo.latitude != null && photo.longitude != null) {
                                Text(
                                    text = "${GpsTelemetry.formatSingleCoord(photo.latitude, true)}, ${GpsTelemetry.formatSingleCoord(photo.longitude, false)} • $dateText",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = TelemetryEmerald,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = { selectedPhotoId = null },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss selected pin card",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSharePhoto(photo) },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("map_share_photo_button")
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = "Share photo", modifier = Modifier.size(16.dp))
                        }

                        if (photo.latitude != null && photo.longitude != null) {
                            OutlinedButton(
                                onClick = {
                                    targetLat = photo.latitude
                                    targetLon = photo.longitude
                                    targetZoom = 16.2f
                                    syncNonce++
                                },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("map_center_pin_button")
                            ) {
                                Icon(Icons.Rounded.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Center Pin", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Button(
                            onClick = { onInspectPhoto(photo.id) },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TelemetryEmerald,
                                contentColor = ObsidianBg
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("map_inspect_photo_button")
                        ) {
                            Text("Inspect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
