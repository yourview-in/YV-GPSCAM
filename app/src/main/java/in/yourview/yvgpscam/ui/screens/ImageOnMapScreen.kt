package `in`.yourview.yvgpscam.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import `in`.yourview.yvgpscam.data.model.CapturedPhotoEntity
import `in`.yourview.yvgpscam.data.model.GpsTelemetry
import `in`.yourview.yvgpscam.data.model.MapStyle
import `in`.yourview.yvgpscam.map.InAppInteractiveTileMap
import `in`.yourview.yvgpscam.map.InAppMapMath
import `in`.yourview.yvgpscam.map.MapPinMarker
import `in`.yourview.yvgpscam.ui.components.AdBannerSlot
import `in`.yourview.yvgpscam.ui.theme.JetBrainsMonoFontFamily
import `in`.yourview.yvgpscam.ui.theme.ObsidianBg
import `in`.yourview.yvgpscam.ui.theme.ObsidianBorder
import `in`.yourview.yvgpscam.ui.theme.ShutterGold
import `in`.yourview.yvgpscam.ui.theme.TelemetryEmerald
import `in`.yourview.yvgpscam.ui.viewmodel.CameraViewModel
import `in`.yourview.yvgpscam.ui.viewmodel.GalleryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ImageOnMapScreen(
    galleryViewModel: GalleryViewModel,
    cameraViewModel: CameraViewModel,
    focusPhotoId: Long? = null,
    focusLat: Double? = null,
    focusLon: Double? = null,
    onBack: () -> Unit,
    onInspectPhoto: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val photos by galleryViewModel.photos.collectAsStateWithLifecycle()
    val config by cameraViewModel.stampConfig.collectAsStateWithLifecycle()
    val liveGps by cameraViewModel.liveGpsTelemetry.collectAsStateWithLifecycle()
    val activeTelemetry by cameraViewModel.telemetry.collectAsStateWithLifecycle()

    val geotaggedPhotos = remember(photos) {
        photos.filter { it.latitude != null && it.longitude != null }
    }
    var selectedPhotoId by remember(focusPhotoId) {
        mutableStateOf(focusPhotoId)
    }
    val selectedPhoto = remember(selectedPhotoId, geotaggedPhotos) {
        geotaggedPhotos.firstOrNull { it.id == selectedPhotoId }
    }

    BackHandler(enabled = selectedPhoto != null) {
        selectedPhotoId = null
    }

    val userCurrentCoords = remember(
        liveGps.latitude,
        liveGps.longitude,
        activeTelemetry.latitude,
        activeTelemetry.longitude,
        geotaggedPhotos
    ) {
        when {
            liveGps.latitude != null && liveGps.longitude != null ->
                liveGps.latitude!! to liveGps.longitude!!
            activeTelemetry.latitude != null && activeTelemetry.longitude != null ->
                activeTelemetry.latitude!! to activeTelemetry.longitude!!
            geotaggedPhotos.isNotEmpty() ->
                geotaggedPhotos.first().latitude!! to geotaggedPhotos.first().longitude!!
            else -> 17.385044 to 78.486671
        }
    }

    val focusedPhoto = remember(focusPhotoId, geotaggedPhotos) {
        focusPhotoId?.let { id -> geotaggedPhotos.firstOrNull { it.id == id } }
    }

    var targetLat by remember {
        mutableStateOf(
            focusedPhoto?.latitude
                ?: focusLat
                ?: userCurrentCoords.first
        )
    }
    var targetLon by remember {
        mutableStateOf(
            focusedPhoto?.longitude
                ?: focusLon
                ?: userCurrentCoords.second
        )
    }
    var targetZoom by remember {
        mutableFloatStateOf(
            if (focusedPhoto != null || (focusLat != null && focusLon != null)) 16.0f else 15.2f
        )
    }
    var syncNonce by remember { mutableIntStateOf(0) }
    var centeredInitial by remember { mutableStateOf(focusPhotoId != null || (focusLat != null && focusLon != null)) }

    // If navigated with focusPhotoId before gallery flow emitted, sync once photo arrives
    LaunchedEffect(focusPhotoId, focusedPhoto) {
        if (focusPhotoId != null && focusedPhoto?.latitude != null && focusedPhoto.longitude != null) {
            selectedPhotoId = focusedPhoto.id
            targetLat = focusedPhoto.latitude
            targetLon = focusedPhoto.longitude
            targetZoom = 16.0f
            centeredInitial = true
            syncNonce++
        }
    }

    // Center once on GPS if screen opened with default fallback and live GPS locks shortly after
    LaunchedEffect(liveGps.latitude, liveGps.longitude) {
        if (!centeredInitial && liveGps.latitude != null && liveGps.longitude != null) {
            centeredInitial = true
            targetLat = liveGps.latitude!!
            targetLon = liveGps.longitude!!
            targetZoom = 15.5f
            syncNonce++
        }
    }

    val pins = remember(userCurrentCoords, activeTelemetry.displayHeadlineAddress, geotaggedPhotos, selectedPhotoId, focusLat, focusLon) {
        buildList {
            add(
                MapPinMarker(
                    id = "user_current_location",
                    latitude = userCurrentCoords.first,
                    longitude = userCurrentCoords.second,
                    title = "Current Location",
                    subtitle = activeTelemetry.displayHeadlineAddress,
                    isUserLocation = true
                )
            )
            if (focusLat != null && focusLon != null && focusedPhoto == null) {
                add(
                    MapPinMarker(
                        id = "focused_coordinate_pin",
                        latitude = focusLat,
                        longitude = focusLon,
                        title = "Focused Location",
                        isSelected = true
                    )
                )
            }
            geotaggedPhotos.forEach { photo ->
                val lat = photo.latitude ?: return@forEach
                val lon = photo.longitude ?: return@forEach
                add(
                    MapPinMarker(
                        id = "photo_${photo.id}",
                        latitude = lat,
                        longitude = lon,
                        title = photo.displayName,
                        subtitle = photo.cityState,
                        isSelected = photo.id == selectedPhotoId,
                        photoId = photo.id
                    )
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("image_on_map_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "Image on Map",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    text = if (geotaggedPhotos.isEmpty()) "Live GPS" else "${geotaggedPhotos.size} pins",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TelemetryEmerald,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("image_on_map_canvas")
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val baseTilePx = with(density) { 192.dp.toPx() }

            InAppInteractiveTileMap(
                centerLat = targetLat,
                centerLon = targetLon,
                zoom = targetZoom,
                mapStyle = config.mapStyle,
                offlineOnlyMapMode = config.offlineOnlyMapMode,
                pins = pins,
                viewportSyncKey = syncNonce,
                showZoomControls = true,
                showTileBadge = true,
                onMapTap = { _, _ -> selectedPhotoId = null },
                onPinTap = { marker ->
                    if (marker.photoId != null) {
                        selectedPhotoId = marker.photoId
                    } else {
                        selectedPhotoId = null
                        targetLat = marker.latitude
                        targetLon = marker.longitude
                        targetZoom = 16.0f
                        syncNonce++
                    }
                }
            )

            // Top-start Fit All pill when geotagged captures exist
            if (geotaggedPhotos.isNotEmpty()) {
                Surface(
                    color = ObsidianBg.copy(alpha = 0.88f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 46.dp)
                        .clip(CircleShape)
                        .border(1.dp, TelemetryEmerald.copy(alpha = 0.55f), CircleShape)
                        .clickable {
                            val allLats = geotaggedPhotos.mapNotNull { it.latitude } + userCurrentCoords.first
                            val allLons = geotaggedPhotos.mapNotNull { it.longitude } + userCurrentCoords.second
                            val (fitLat, fitLon, fitZoom) = InAppMapMath.computeZoomToFitBounds(
                                lats = allLats,
                                lons = allLons,
                                viewportWidthPx = widthPx,
                                viewportHeightPx = heightPx,
                                baseTilePx = baseTilePx
                            )
                            targetLat = fitLat
                            targetLon = fitLon
                            targetZoom = fitZoom
                            syncNonce++
                        }
                        .testTag("image_on_map_fit_all_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FitScreen,
                            contentDescription = "Fit all photo pins and current location",
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

            // Bottom-end Map Style Switcher & Recenter Controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 14.dp,
                        bottom = if (selectedPhoto != null) 184.dp else 18.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    Text(
                        text = config.mapStyle.displayName,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ShutterGold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    color = Color.White,
                    shape = CircleShape,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable {
                            val nextStyle = MapStyle.entries[
                                (MapStyle.entries.indexOf(config.mapStyle) + 1) % MapStyle.entries.size
                            ]
                            cameraViewModel.updateStampConfig { it.copy(mapStyle = nextStyle) }
                            Toast
                                .makeText(context, "Map type: ${nextStyle.displayName}", Toast.LENGTH_SHORT)
                                .show()
                        }
                        .testTag("image_on_map_layers_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Cycle in-app map style",
                            tint = Color(0xFF1F2937),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Surface(
                    color = Color.White,
                    shape = CircleShape,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable {
                            targetLat = userCurrentCoords.first
                            targetLon = userCurrentCoords.second
                            targetZoom = 16.0f
                            syncNonce++
                        }
                        .testTag("image_on_map_recenter_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Center map on your current location",
                            tint = Color(0xFF1F2937),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Bottom Selected Photo Preview Card on pin tap
            selectedPhoto?.let { photo ->
                val distanceBearing = remember(photo.latitude, photo.longitude, userCurrentCoords) {
                    if (photo.latitude != null && photo.longitude != null) {
                        val (meters, cardinal) = InAppMapMath.computeDistanceMetersAndBearing(
                            fromLat = userCurrentCoords.first,
                            fromLon = userCurrentCoords.second,
                            toLat = photo.latitude,
                            toLon = photo.longitude
                        )
                        "${InAppMapMath.formatDistance(meters)} $cardinal"
                    } else {
                        null
                    }
                }
                SelectedMapPhotoCard(
                    photo = photo,
                    distanceLabel = distanceBearing,
                    onDismiss = { selectedPhotoId = null },
                    onInspect = { onInspectPhoto(photo.id) },
                    onCenterPin = {
                        if (photo.latitude != null && photo.longitude != null) {
                            targetLat = photo.latitude
                            targetLon = photo.longitude
                            targetZoom = 16.5f
                            syncNonce++
                        }
                    },
                    onShare = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = if (photo.isVideo) "video/mp4" else "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(photo.mediaUri))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(intent, "Share ${photo.displayName}"))
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                )
            }
        }

        AdBannerSlot(placementId = "image_on_map")
    }
}

@Composable
private fun SelectedMapPhotoCard(
    photo: CapturedPhotoEntity,
    distanceLabel: String?,
    onDismiss: () -> Unit,
    onInspect: () -> Unit,
    onCenterPin: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateText = remember(photo.capturedAtMillis) {
        SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(photo.capturedAtMillis))
    }
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, ShutterGold.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            .testTag("image_on_map_selected_card")
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
                        .size(58.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
                        .clickable(onClick = onInspect)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = photo.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (distanceLabel != null) {
                            Surface(
                                color = TelemetryEmerald.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Explore,
                                        contentDescription = null,
                                        tint = TelemetryEmerald,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = distanceLabel,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TelemetryEmerald
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = photo.cityState.ifBlank { photo.addressLine }.ifBlank { "Captured Site" },
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
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close selected pin card")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onShare,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share capture", modifier = Modifier.size(16.dp))
                }
                OutlinedButton(
                    onClick = onCenterPin,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("image_on_map_center_pin_button")
                ) {
                    Icon(Icons.Rounded.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Center Pin", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onInspect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ShutterGold,
                        contentColor = ObsidianBg
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Inspect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
