package `in`.yourview.yvgpscam.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import `in`.yourview.yvgpscam.R
import `in`.yourview.yvgpscam.camera.ExifMetadataDetails
import `in`.yourview.yvgpscam.data.model.CapturedPhotoEntity
import `in`.yourview.yvgpscam.data.model.GpsTelemetry
import `in`.yourview.yvgpscam.data.model.MapStyle
import `in`.yourview.yvgpscam.map.PhotoDetailGoogleMap
import `in`.yourview.yvgpscam.ui.components.AdBannerSlot
import `in`.yourview.yvgpscam.ui.components.ModernFloatingCapsule
import `in`.yourview.yvgpscam.ui.components.ModernGlassCircleButton
import `in`.yourview.yvgpscam.ui.components.SystemBarsImmersiveEffect
import `in`.yourview.yvgpscam.ui.theme.JetBrainsMonoFontFamily
import `in`.yourview.yvgpscam.ui.theme.ObsidianBg
import `in`.yourview.yvgpscam.ui.theme.ShutterGold
import `in`.yourview.yvgpscam.ui.theme.TelemetryEmerald
import `in`.yourview.yvgpscam.ui.viewmodel.GalleryViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PhotoDetailScreen(
    photoId: Long,
    galleryViewModel: GalleryViewModel,
    offlineOnlyMapMode: Boolean,
    showBackButton: Boolean = true,
    onBack: () -> Unit,
    onOpenInAppMap: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allPhotos by galleryViewModel.photos.collectAsStateWithLifecycle()
    val fallbackPhoto by galleryViewModel.getPhotoFlow(photoId).collectAsStateWithLifecycle(initialValue = null)
    val mapThumbnails by galleryViewModel.mapThumbnailCache.collectAsStateWithLifecycle()
    val stampConfig by galleryViewModel.stampConfig.collectAsStateWithLifecycle()

    val pagerPhotos: List<CapturedPhotoEntity> = remember(allPhotos, fallbackPhoto, photoId) {
        when {
            allPhotos.any { it.id == photoId } -> allPhotos
            fallbackPhoto != null -> listOf(fallbackPhoto!!) + allPhotos.filter { it.id != photoId }
            else -> allPhotos
        }
    }

    val initialIndex = remember(pagerPhotos, photoId) {
        pagerPhotos.indexOfFirst { it.id == photoId }.coerceAtLeast(0)
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { pagerPhotos.size }
    )

    var hasInitializedPage by remember(photoId) { mutableStateOf(false) }
    LaunchedEffect(pagerPhotos.size, photoId) {
        if (!hasInitializedPage && pagerPhotos.isNotEmpty()) {
            val targetIdx = pagerPhotos.indexOfFirst { it.id == photoId }
            if (targetIdx >= 0) {
                pagerState.scrollToPage(targetIdx)
                hasInitializedPage = true
            }
        }
    }

    val currentPhoto = pagerPhotos.getOrNull(pagerState.currentPage) ?: fallbackPhoto
    val activePhotoId = currentPhoto?.id ?: photoId

    var showDetails by remember { mutableStateOf(false) }
    var exifDetails by remember(activePhotoId) { mutableStateOf<ExifMetadataDetails?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isFullScreenPhoto by remember { mutableStateOf(false) }
    var showFullScreenChrome by remember { mutableStateOf(true) }

    val zoomByPhotoId = remember { mutableStateMapOf<Long, Float>() }
    val panByPhotoId = remember { mutableStateMapOf<Long, Offset>() }
    val viewportSizeByPhotoId = remember { mutableStateMapOf<Long, IntSize>() }

    LaunchedEffect(pagerState.currentPage, isFullScreenPhoto) {
        val currentId = pagerPhotos.getOrNull(pagerState.currentPage)?.id
        zoomByPhotoId.keys.filter { it != currentId }.forEach { staleId ->
            zoomByPhotoId.remove(staleId)
            panByPhotoId.remove(staleId)
        }
    }

    val activeZoom = zoomByPhotoId[activePhotoId] ?: 1f
    val activeViewportSize = viewportSizeByPhotoId[activePhotoId] ?: IntSize(1080, 1440)
    val isCurrentPhotoZoomed = activeZoom > 1.05f

    val adjustPhotoZoom: (Long, Float) -> Unit = { targetId, targetScale ->
        val size = viewportSizeByPhotoId[targetId] ?: IntSize(1080, 1440)
        val currentScale = zoomByPhotoId[targetId] ?: 1f
        val currentPan = panByPhotoId[targetId] ?: Offset.Zero
        val safeTarget = targetScale.coerceIn(1f, 5f)
        val delta = if (currentScale > 0f) safeTarget / currentScale else safeTarget
        val (nextScale, nextPan) = computeZoomedTransform(
            currentScale = currentScale,
            currentPan = currentPan,
            zoomDelta = delta,
            panDelta = Offset.Zero,
            viewportWidth = size.width.toFloat(),
            viewportHeight = size.height.toFloat()
        )
        zoomByPhotoId[targetId] = nextScale
        panByPhotoId[targetId] = nextPan
    }

    SystemBarsImmersiveEffect(immersive = isFullScreenPhoto)
    BackHandler(enabled = isFullScreenPhoto || showDetails) {
        if (isFullScreenPhoto) {
            isFullScreenPhoto = false
        } else if (showDetails) {
            showDetails = false
        }
    }

    // Load EXIF & map preview for whichever photo the user swiped onto when Details is opened
    LaunchedEffect(currentPhoto?.id, currentPhoto?.mediaUri, stampConfig.mapStyle, showDetails) {
        val target = currentPhoto ?: return@LaunchedEffect
        if (showDetails) {
            galleryViewModel.ensureMapThumbnailLoaded(target, offlineOnlyMapMode, stampConfig.mapStyle)
            if (exifDetails == null) {
                exifDetails = galleryViewModel.loadExifDetails(target.mediaUri)
            }
        }
    }

    if (currentPhoto == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text("Loading photo...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val mapBitmap = mapThumbnails[currentPhoto.id]
    val dateFormatted = remember(currentPhoto.capturedAtMillis) {
        SimpleDateFormat("dd MMM yyyy, hh:mm:ss a z", Locale.getDefault())
            .format(Date(currentPhoto.capturedAtMillis))
    }

    val shareCurrentPhoto: () -> Unit = {
        val coords = if (currentPhoto.latitude != null && currentPhoto.longitude != null) {
            String.format(Locale.US, "%.6f, %.6f", currentPhoto.latitude, currentPhoto.longitude)
        } else {
            "GPS unavailable"
        }
        val locationLine = listOf(currentPhoto.addressLine, currentPhoto.cityState, currentPhoto.country)
            .filter { it.isNotBlank() }
            .joinToString(", ")
        val caption = buildString {
            append("Captured with YVGPSCAM\nCoordinates: $coords")
            if (locationLine.isNotBlank()) append("\nLocation: $locationLine")
            if (currentPhoto.customNote.isNotBlank()) append("\nNote: ${currentPhoto.customNote}")
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (currentPhoto.isVideo) "video/mp4" else "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(currentPhoto.mediaUri))
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Geotagged Capture"))
    }

    fun openVideoExternally(photo: CapturedPhotoEntity) {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(photo.mediaUri), "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(viewIntent)
        }.onFailure {
            Toast.makeText(context, "Unable to open video player", Toast.LENGTH_SHORT).show()
        }
    }

    if (isFullScreenPhoto) {
        val fullScreenViewer: @Composable () -> Unit = {
            val fsZoomActive = (zoomByPhotoId[activePhotoId] ?: 1f) > 1.05f

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .testTag("photo_fullscreen_viewer")
            ) {
                HorizontalPager(
                    state = pagerState,
                    key = { index -> pagerPhotos.getOrNull(index)?.id ?: index },
                    userScrollEnabled = !fsZoomActive,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val pagePhoto = pagerPhotos[pageIndex]
                    val pageZoom = zoomByPhotoId[pagePhoto.id] ?: 1f
                    val pagePan = panByPhotoId[pagePhoto.id] ?: Offset.Zero

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { viewportSizeByPhotoId[pagePhoto.id] = it }
                            .pointerInput(pagePhoto.id, pagePhoto.isVideo) {
                                detectTapGestures(
                                    onTap = { showFullScreenChrome = !showFullScreenChrome },
                                    onDoubleTap = {
                                        if (!pagePhoto.isVideo) {
                                            val cur = zoomByPhotoId[pagePhoto.id] ?: 1f
                                            adjustPhotoZoom(pagePhoto.id, if (cur > 1.05f) 1f else 2.5f)
                                        }
                                    }
                                )
                            }
                            .then(
                                if (!pagePhoto.isVideo) {
                                    Modifier.pointerInput(pagePhoto.id) {
                                        awaitEachGesture {
                                            var didZoom = false
                                            awaitFirstDown(requireUnconsumed = false)
                                            do {
                                                val event = awaitPointerEvent()
                                                val pressedCount = event.changes.count { it.pressed }
                                                val currentScale = zoomByPhotoId[pagePhoto.id] ?: 1f
                                                if (pressedCount >= 2 || currentScale > 1.05f) {
                                                    val zoomDelta = event.calculateZoom()
                                                    val panDelta = event.calculatePan()
                                                    if (zoomDelta != 1f || panDelta != Offset.Zero) {
                                                        val vp = viewportSizeByPhotoId[pagePhoto.id] ?: IntSize(size.width, size.height)
                                                        val (nextScale, nextPan) = computeZoomedTransform(
                                                            currentScale = currentScale,
                                                            currentPan = panByPhotoId[pagePhoto.id] ?: Offset.Zero,
                                                            zoomDelta = zoomDelta,
                                                            panDelta = panDelta,
                                                            viewportWidth = vp.width.toFloat(),
                                                            viewportHeight = vp.height.toFloat()
                                                        )
                                                        zoomByPhotoId[pagePhoto.id] = nextScale
                                                        panByPhotoId[pagePhoto.id] = nextPan
                                                        didZoom = true
                                                        event.changes.forEach { change ->
                                                            if (change.positionChanged()) {
                                                                change.consume()
                                                            }
                                                        }
                                                    }
                                                }
                                            } while (event.changes.any { it.pressed })
                                            if (didZoom && (zoomByPhotoId[pagePhoto.id] ?: 1f) <= 1.05f) {
                                                zoomByPhotoId[pagePhoto.id] = 1f
                                                panByPhotoId[pagePhoto.id] = Offset.Zero
                                            }
                                        }
                                    }
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = pagePhoto.mediaUri,
                            contentDescription = pagePhoto.displayName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = pageZoom
                                    scaleY = pageZoom
                                    translationX = pagePan.x
                                    translationY = pagePan.y
                                }
                        )
                        if (pagePhoto.isVideo) {
                            IconButton(
                                onClick = { openVideoExternally(pagePhoto) },
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircleFilled,
                                    contentDescription = "Play video",
                                    tint = ShutterGold,
                                    modifier = Modifier.size(56.dp)
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showFullScreenChrome,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.65f))
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            IconButton(
                                onClick = { isFullScreenPhoto = false },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("exit_photo_fullscreen_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FullscreenExit,
                                    contentDescription = "Exit full screen viewer",
                                    tint = Color.White
                                )
                            }
                            Column {
                                Text(
                                    text = currentPhoto.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${pagerState.currentPage + 1}/${pagerPhotos.size.coerceAtLeast(1)} • $dateFormatted",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = TelemetryEmerald,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = shareCurrentPhoto,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share photo",
                                tint = TelemetryEmerald
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showFullScreenChrome,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!currentPhoto.isVideo) {
                            ZoomControlsPill(
                                currentZoom = activeZoom,
                                onZoomOut = { adjustPhotoZoom(activePhotoId, activeZoom - 0.75f) },
                                onResetZoom = { adjustPhotoZoom(activePhotoId, 1f) },
                                onZoomIn = { adjustPhotoZoom(activePhotoId, activeZoom + 0.75f) },
                                zoomOutTestTag = "fullscreen_zoom_out_button",
                                zoomResetTestTag = "fullscreen_zoom_reset_button",
                                zoomInTestTag = "fullscreen_zoom_in_button"
                            )
                        }
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = when {
                                    currentPhoto.isVideo -> "Tap play to open video • Tap screen to hide"
                                    activeZoom > 1.05f -> "Drag to pan • Pinch or double-tap to reset"
                                    pagerPhotos.size > 1 -> "Pinch or double-tap to zoom • Swipe to switch"
                                    else -> "Pinch or double-tap to zoom • Tap to hide controls"
                                },
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showBackButton) {
            fullScreenViewer()
            return
        } else {
            Dialog(
                onDismissRequest = { isFullScreenPhoto = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                fullScreenViewer()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(if (showBackButton) Modifier.statusBarsPadding() else Modifier)
            .navigationBarsPadding()
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (showBackButton) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to gallery"
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = if (showBackButton) 0.dp else 8.dp)) {
                    Text(
                        text = currentPhoto.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val pageCounter = if (pagerPhotos.size > 1) {
                        "${pagerState.currentPage + 1}/${pagerPhotos.size} • "
                    } else {
                        ""
                    }
                    Text(
                        text = "$pageCounter${currentPhoto.width}×${currentPhoto.height} px • ${currentPhoto.templateName}",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            ModernFloatingCapsule(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                borderColor = MaterialTheme.colorScheme.outline
            ) {
                ModernGlassCircleButton(
                    icon = Icons.Rounded.Fullscreen,
                    contentDescription = "View full screen",
                    inactiveIconTint = MaterialTheme.colorScheme.onSurface,
                    standaloneGlass = false,
                    buttonSize = 38.dp,
                    onClick = { isFullScreenPhoto = true },
                    testTag = "detail_top_fullscreen_button"
                )
                ModernGlassCircleButton(
                    icon = Icons.Rounded.Share,
                    contentDescription = "Share photo",
                    inactiveIconTint = TelemetryEmerald,
                    standaloneGlass = false,
                    buttonSize = 38.dp,
                    onClick = shareCurrentPhoto,
                    testTag = "detail_share_button"
                )
                ModernGlassCircleButton(
                    icon = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete photo",
                    inactiveIconTint = MaterialTheme.colorScheme.error,
                    standaloneGlass = false,
                    buttonSize = 38.dp,
                    onClick = { showDeleteConfirm = true },
                    testTag = "detail_delete_button"
                )
            }
        }

        // Side-Scrollable HorizontalPager Viewport (full height when Details hidden; responsive split when Details open)
        Box(
            modifier = Modifier
                .weight(if (showDetails) 0.42f else 1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                .testTag("photo_detail_pager_container")
        ) {
            HorizontalPager(
                state = pagerState,
                key = { index -> pagerPhotos.getOrNull(index)?.id ?: index },
                userScrollEnabled = !isCurrentPhotoZoomed,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("photo_detail_horizontal_pager")
            ) { pageIndex ->
                val pagePhoto = pagerPhotos[pageIndex]
                val pageZoom = zoomByPhotoId[pagePhoto.id] ?: 1f
                val pagePan = panByPhotoId[pagePhoto.id] ?: Offset.Zero

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewportSizeByPhotoId[pagePhoto.id] = it }
                        .pointerInput(pagePhoto.id, pagePhoto.isVideo) {
                            detectTapGestures(
                                onTap = {
                                    if (pagePhoto.isVideo) {
                                        openVideoExternally(pagePhoto)
                                    } else if ((zoomByPhotoId[pagePhoto.id] ?: 1f) <= 1.05f) {
                                        isFullScreenPhoto = true
                                    }
                                },
                                onDoubleTap = {
                                    if (!pagePhoto.isVideo) {
                                        val cur = zoomByPhotoId[pagePhoto.id] ?: 1f
                                        adjustPhotoZoom(pagePhoto.id, if (cur > 1.05f) 1f else 2.5f)
                                    }
                                }
                            )
                        }
                        .then(
                            if (!pagePhoto.isVideo) {
                                Modifier.pointerInput(pagePhoto.id) {
                                    awaitEachGesture {
                                        var didZoom = false
                                        awaitFirstDown(requireUnconsumed = false)
                                        do {
                                            val event = awaitPointerEvent()
                                            val pressedCount = event.changes.count { it.pressed }
                                            val currentScale = zoomByPhotoId[pagePhoto.id] ?: 1f
                                            if (pressedCount >= 2 || currentScale > 1.05f) {
                                                val zoomDelta = event.calculateZoom()
                                                val panDelta = event.calculatePan()
                                                if (zoomDelta != 1f || panDelta != Offset.Zero) {
                                                    val vp = viewportSizeByPhotoId[pagePhoto.id] ?: IntSize(size.width, size.height)
                                                    val (nextScale, nextPan) = computeZoomedTransform(
                                                        currentScale = currentScale,
                                                        currentPan = panByPhotoId[pagePhoto.id] ?: Offset.Zero,
                                                        zoomDelta = zoomDelta,
                                                        panDelta = panDelta,
                                                        viewportWidth = vp.width.toFloat(),
                                                        viewportHeight = vp.height.toFloat()
                                                    )
                                                    zoomByPhotoId[pagePhoto.id] = nextScale
                                                    panByPhotoId[pagePhoto.id] = nextPan
                                                    didZoom = true
                                                    event.changes.forEach { change ->
                                                        if (change.positionChanged()) {
                                                            change.consume()
                                                        }
                                                    }
                                                }
                                            }
                                        } while (event.changes.any { it.pressed })
                                        if (didZoom && (zoomByPhotoId[pagePhoto.id] ?: 1f) <= 1.05f) {
                                            zoomByPhotoId[pagePhoto.id] = 1f
                                            panByPhotoId[pagePhoto.id] = Offset.Zero
                                        }
                                    }
                                }
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = pagePhoto.mediaUri,
                        contentDescription = pagePhoto.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = pageZoom
                                scaleY = pageZoom
                                translationX = pagePan.x
                                translationY = pagePan.y
                            }
                    )

                    if (pagePhoto.isVideo) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.58f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircleFilled,
                                contentDescription = "Play video",
                                tint = ShutterGold,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }
            }

            if (pagerPhotos.size > 1 && !isCurrentPhotoZoomed) {
                if (pagerState.currentPage > 0) {
                    IconButton(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 4.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.42f))
                            .testTag("detail_prev_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous photo",
                            tint = Color.White
                        )
                    }
                }

                if (pagerState.currentPage < pagerPhotos.lastIndex) {
                    IconButton(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.42f))
                            .testTag("detail_next_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next photo",
                            tint = Color.White
                        )
                    }
                }
            }

            if (!currentPhoto.isVideo) {
                ZoomControlsPill(
                    currentZoom = activeZoom,
                    onZoomOut = { adjustPhotoZoom(activePhotoId, activeZoom - 0.75f) },
                    onResetZoom = { adjustPhotoZoom(activePhotoId, 1f) },
                    onZoomIn = { adjustPhotoZoom(activePhotoId, activeZoom + 0.75f) },
                    zoomOutTestTag = "detail_zoom_out_button",
                    zoomResetTestTag = "detail_zoom_reset_button",
                    zoomInTestTag = "detail_zoom_in_button",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                )
            } else {
                Surface(
                    color = Color.Black.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Tap video to play",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            IconButton(
                onClick = { isFullScreenPhoto = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xAA0C1119))
                    .border(0.9.dp, TelemetryEmerald.copy(alpha = 0.65f), CircleShape)
                    .testTag("detail_fullscreen_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Fullscreen,
                    contentDescription = "Open full screen viewer",
                    tint = TelemetryEmerald
                )
            }
        }

        // Bottom Bar below photo: Information stays hidden unless user clicks "Details"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { showDetails = !showDetails },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showDetails) ShutterGold else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (showDetails) ObsidianBg else MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("detail_toggle_details_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (showDetails) "Hide Details" else stringResource(R.string.action_details),
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (showDetails) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (currentPhoto.latitude != null && currentPhoto.longitude != null) {
                OutlinedButton(
                    onClick = {
                        if (onOpenInAppMap != null) {
                            onOpenInAppMap(currentPhoto.id)
                        } else {
                            showDetails = true
                        }
                    },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("detail_quick_maps_button")
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, tint = TelemetryEmerald, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Map", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Expandable Information Section — ONLY visible when user explicitly clicks Details
        if (showDetails) {
            Column(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 14.dp)
                    .testTag("photo_detail_expanded_info")
            ) {
                // Location Map + Coordinates Action Card
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val lat = currentPhoto.latitude
                        val lon = currentPhoto.longitude

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.detail_gps_telemetry),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (lat != null && lon != null) {
                                Text(
                                    text = "${GpsTelemetry.formatSingleCoord(lat, true)}  •  ${GpsTelemetry.formatSingleCoord(lon, false)}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    color = TelemetryEmerald
                                )
                                Text(
                                    text = "${GpsTelemetry.toDms(lat, true)}  ${GpsTelemetry.toDms(lon, false)}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "Coordinates unavailable at capture",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (lat != null && lon != null) {
                            PhotoDetailGoogleMap(
                                latitude = lat,
                                longitude = lon,
                                title = currentPhoto.displayName,
                                snippet = currentPhoto.cityState.ifBlank { currentPhoto.addressLine },
                                mapStyle = stampConfig.mapStyle,
                                offlineOnlyMapMode = offlineOnlyMapMode,
                                fallbackBitmap = mapBitmap,
                                onOpenFullMap = onOpenInAppMap?.let { openMap ->
                                    { openMap(currentPhoto.id) }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MapStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = stampConfig.mapStyle == style,
                                    onClick = { galleryViewModel.setMapStyle(style) },
                                    label = { Text(style.displayName, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = TelemetryEmerald.copy(alpha = 0.2f),
                                        selectedLabelColor = TelemetryEmerald
                                    )
                                )
                            }
                        }

                        if (lat != null && lon != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val coordText = String.format(Locale.US, "%.6f, %.6f", lat, lon)
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("GPS Coordinates", coordText))
                                        Toast.makeText(context, "Copied $coordText", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy Coords")
                                }

                                Button(
                                    onClick = {
                                        if (onOpenInAppMap != null) {
                                            onOpenInAppMap(currentPhoto.id)
                                        } else {
                                            Toast.makeText(context, "Interactive map shown above", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TelemetryEmerald,
                                        contentColor = ObsidianBg
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("detail_open_google_maps_button")
                                ) {
                                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Full Map", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Detailed EXIF & Metadata Card
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Embedded EXIF & Sensor Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        ExifRow("Captured Timestamp", dateFormatted)
                        if (currentPhoto.addressLine.isNotBlank()) {
                            ExifRow("Address", currentPhoto.addressLine)
                        }
                        if (currentPhoto.cityState.isNotBlank() || currentPhoto.country.isNotBlank()) {
                            ExifRow(
                                "Locality",
                                listOf(currentPhoto.cityState, currentPhoto.country).filter { it.isNotBlank() }.joinToString(", ")
                            )
                        }
                        currentPhoto.altitudeMeters?.let {
                            ExifRow("Elevation (Altitude)", String.format(Locale.US, "%.1f meters", it))
                        }
                        currentPhoto.accuracyMeters?.let {
                            ExifRow("GPS Accuracy", String.format(Locale.US, "±%.1f meters", it))
                        }
                        ExifRow("Compass Heading", "${currentPhoto.compassAzimuth.roundToInt()}° (${currentPhoto.compassCardinal})")
                        if (currentPhoto.customNote.isNotBlank()) {
                            ExifRow("Field Note", currentPhoto.customNote)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        val exif = exifDetails
                        ExifRow("EXIF Make / Software", "${exif?.make ?: "YVGPSCAM"} • ${exif?.software ?: "YVGPSCAM 1.0"}")
                        ExifRow("Device Model", exif?.model ?: android.os.Build.MODEL)
                        ExifRow("EXIF DateTimeOriginal", exif?.dateTimeOriginal ?: dateFormatted)
                        if (exif?.gpsLatitude != null && exif.gpsLongitude != null) {
                            ExifRow(
                                "EXIF GPS Tag",
                                String.format(Locale.US, "%.6f, %.6f", exif.gpsLatitude, exif.gpsLongitude)
                            )
                        }
                        exif?.gpsAltitude?.let {
                            ExifRow("EXIF GPS Altitude", String.format(Locale.US, "%.1f m", it))
                        }
                        exif?.gpsDirection?.let {
                            ExifRow("EXIF Img Direction", "$it (Magnetic)")
                        }
                        ExifRow("Dimensions", "${currentPhoto.width} × ${currentPhoto.height} px")
                    }
                }
            }
        }

        AdBannerSlot(placementId = "photo_detail")
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Geotagged Capture?") },
            text = { Text("This removes ${currentPhoto.displayName} from your YVGPSCAM gallery and device storage.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        val isLast = pagerPhotos.size <= 1
                        galleryViewModel.deletePhoto(
                            photo = currentPhoto,
                            onDeleted = {
                                if (isLast) {
                                    onBack()
                                } else {
                                    Toast.makeText(context, "Capture deleted", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ExifRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
private fun ZoomControlsPill(
    currentZoom: Float,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    onZoomIn: () -> Unit,
    zoomOutTestTag: String,
    zoomResetTestTag: String,
    zoomInTestTag: String,
    modifier: Modifier = Modifier
) {
    val canZoomOut = currentZoom > 1.02f
    val canZoomIn = currentZoom < 4.98f
    Surface(
        color = Color(0xCC0C1119),
        shape = CircleShape,
        modifier = modifier
            .border(0.9.dp, TelemetryEmerald.copy(alpha = 0.55f), CircleShape)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            IconButton(
                onClick = onZoomOut,
                enabled = canZoomOut,
                modifier = Modifier
                    .size(44.dp)
                    .testTag(zoomOutTestTag)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom out photo",
                    tint = if (canZoomOut) Color.White else Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Surface(
                onClick = onResetZoom,
                enabled = canZoomOut,
                color = if (canZoomOut) ShutterGold.copy(alpha = 0.22f) else Color.Transparent,
                shape = CircleShape,
                modifier = Modifier
                    .height(36.dp)
                    .testTag(zoomResetTestTag)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 10.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1fx", currentZoom),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canZoomOut) ShutterGold else Color.White
                    )
                }
            }

            IconButton(
                onClick = onZoomIn,
                enabled = canZoomIn,
                modifier = Modifier
                    .size(44.dp)
                    .testTag(zoomInTestTag)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom in photo",
                    tint = if (canZoomIn) TelemetryEmerald else TelemetryEmerald.copy(alpha = 0.35f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

internal fun computeZoomedTransform(
    currentScale: Float,
    currentPan: Offset,
    zoomDelta: Float,
    panDelta: Offset,
    viewportWidth: Float,
    viewportHeight: Float,
    minScale: Float = 1f,
    maxScale: Float = 5f
): Pair<Float, Offset> {
    val safeMin = minScale.coerceAtLeast(1f)
    val safeMax = maxScale.coerceAtLeast(safeMin)
    val nextScale = (currentScale * zoomDelta).coerceIn(safeMin, safeMax)
    if (nextScale <= 1.02f) {
        return 1f to Offset.Zero
    }
    val maxOffsetX = (viewportWidth.coerceAtLeast(1f) * (nextScale - 1f)) / 2f
    val maxOffsetY = (viewportHeight.coerceAtLeast(1f) * (nextScale - 1f)) / 2f
    val nextX = (currentPan.x + panDelta.x).coerceIn(-maxOffsetX, maxOffsetX)
    val nextY = (currentPan.y + panDelta.y).coerceIn(-maxOffsetY, maxOffsetY)
    return nextScale to Offset(nextX, nextY)
}

