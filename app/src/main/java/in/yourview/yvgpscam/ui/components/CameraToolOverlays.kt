package `in`.yourview.yvgpscam.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BurstMode
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Exposure
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Grid4x4
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MotionPhotosAuto
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.yourview.yvgpscam.data.model.CapturedPhotoEntity
import `in`.yourview.yvgpscam.data.model.StampConfig
import `in`.yourview.yvgpscam.ui.theme.FocusReticleGreen
import `in`.yourview.yvgpscam.ui.theme.JetBrainsMonoFontFamily
import `in`.yourview.yvgpscam.ui.theme.ShutterGold
import `in`.yourview.yvgpscam.ui.theme.TelemetryEmerald
import `in`.yourview.yvgpscam.ui.viewmodel.CameraHudState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Modernised expandable Quick Tools deck:
 * Ratio | Grid | Timer | Full View
 * Torch | Mirror | Voice | Level
 * Burst | Map Style | Offline | Add Text
 * Exposure EV Slider (when supported)
 * Stamp Pos | Stamp Photo | Drive Sync
 */
@Composable
fun CameraQuickToolsDrawer(
    hudState: CameraHudState,
    config: StampConfig,
    isTorchEnabled: Boolean = false,
    isBurstMode: Boolean = false,
    isAutoCaptureMode: Boolean = false,
    exposureIndex: Int = 0,
    minExposureIndex: Int = 0,
    maxExposureIndex: Int = 0,
    exposureStepEv: Float = 0.33f,
    onCycleRatio: () -> Unit,
    onToggleGrid: () -> Unit,
    onCycleTimer: () -> Unit,
    onToggleFullView: () -> Unit,
    onToggleTorch: () -> Unit = {},
    onToggleMirror: () -> Unit,
    onToggleVoice: () -> Unit,
    onToggleLevel: () -> Unit,
    onToggleAutoCapture: () -> Unit = {},
    onToggleBurstMode: () -> Unit = {},
    onCycleMapStyle: () -> Unit = {},
    onToggleOfflineMapMode: () -> Unit = {},
    onChangeExposureIndex: (Int) -> Unit = {},
    onCycleStampPosition: () -> Unit,
    onOpenNoteDialog: () -> Unit,
    onImportPhoto: () -> Unit,
    onOpenDriveSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xF00E131B),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.08f))
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .testTag("camera_quick_tools_drawer")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Row 1: Ratio | Grid | Timer | Full View
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickToolTile(
                    icon = Icons.Rounded.AspectRatio,
                    label = "Ratio ${hudState.aspectRatio.label}",
                    active = hudState.aspectRatio.label != "3:4",
                    onClick = onCycleRatio,
                    testTag = "quick_tool_ratio",
                    modifier = Modifier.weight(1f)
                )
                QuickToolTile(
                    icon = Icons.Rounded.Grid4x4,
                    label = "Grid",
                    active = hudState.showGrid,
                    onClick = onToggleGrid,
                    testTag = "quick_tool_grid",
                    modifier = Modifier.weight(1f)
                )
                QuickToolTile(
                    icon = Icons.Rounded.Timer,
                    label = if (config.timerDelaySeconds > 0) "Timer ${config.timerDelaySeconds}s" else "Timer",
                    active = config.timerDelaySeconds > 0,
                    onClick = onCycleTimer,
                    testTag = "quick_tool_timer",
                    modifier = Modifier.weight(1f)
                )
                QuickToolTile(
                    icon = if (hudState.isFullScreenCamera) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                    label = "Full View",
                    active = hudState.isFullScreenCamera,
                    onClick = onToggleFullView,
                    testTag = "quick_tool_full_view",
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 2: Auto Capture | Burst | Torch | Level
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickToolTile(
                    icon = Icons.Rounded.MotionPhotosAuto,
                    label = if (isAutoCaptureMode) "Auto ${hudState.autoCaptureIntervalSeconds}s" else "Auto Cap",
                    active = isAutoCaptureMode,
                    activeColor = TelemetryEmerald,
                    onClick = onToggleAutoCapture,
                    testTag = "quick_tool_auto_capture",
                    modifier = Modifier.weight(1f)
                )
                QuickToolTile(
                    icon = Icons.Rounded.BurstMode,
                    label = if (isBurstMode) "3x Burst" else "Burst",
                    active = isBurstMode,
                    onClick = onToggleBurstMode,
                    testTag = "quick_tool_burst",
                    modifier = Modifier.weight(1f)
                )
                QuickToolTile(
                    icon = if (isTorchEnabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                    label = if (isTorchEnabled) "Torch ON" else "Torch",
                    active = isTorchEnabled,
                    onClick = onToggleTorch,
                    testTag = "quick_tool_torch",
                    modifier = Modifier.weight(1f)
                )
                QuickToolTile(
                    icon = Icons.Rounded.ScreenRotation,
                    label = "Level",
                    active = config.showDigitalLevel,
                    onClick = onToggleLevel,
                    testTag = "quick_tool_level",
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 3: Mirror | Voice | Map Style | Offline
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickToolTile(
                    icon = Icons.Rounded.Flip,
                    label = "Mirror",
                    active = config.mirrorFrontCamera,
                    onClick = onToggleMirror,
                    testTag = "quick_tool_mirror",
                    modifier = Modifier.weight(1f)
                )
                QuickToolTile(
                    icon = if (config.recordVideoWithVoice) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                    label = "Voice",
                    active = config.recordVideoWithVoice,
                    onClick = onToggleVoice,
                    testTag = "quick_tool_voice",
                    modifier = Modifier.weight(1f)
                )
                QuickToolTile(
                    icon = Icons.Rounded.Layers,
                    label = config.mapStyle.displayName,
                    active = config.mapStyle.name != "STANDARD",
                    onClick = onCycleMapStyle,
                    testTag = "quick_tool_map_style",
                    modifier = Modifier.weight(1f)
                )
                QuickToolTile(
                    icon = Icons.Rounded.OfflinePin,
                    label = if (config.offlineOnlyMapMode) "Offline ON" else "Offline",
                    active = config.offlineOnlyMapMode,
                    activeColor = TelemetryEmerald,
                    onClick = onToggleOfflineMapMode,
                    testTag = "quick_tool_offline_map",
                    modifier = Modifier.weight(1f)
                )
            }

            // Exposure Compensation Bar (shown when camera hardware exposes EV range)
            if (maxExposureIndex > minExposureIndex) {
                val evValue = exposureIndex * exposureStepEv
                val evLabel = String.format(Locale.US, "%+.1f EV", evValue)
                Surface(
                    color = Color.White.copy(alpha = 0.06f),
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.8.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                        .testTag("quick_tool_exposure_slider_row")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Exposure,
                            contentDescription = "Exposure compensation",
                            tint = ShutterGold,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = evLabel,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (exposureIndex == 0) Color.White else ShutterGold,
                            modifier = Modifier.width(56.dp)
                        )
                        Slider(
                            value = exposureIndex.toFloat(),
                            onValueChange = { onChangeExposureIndex(it.roundToInt()) },
                            valueRange = minExposureIndex.toFloat()..maxExposureIndex.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = ShutterGold,
                                activeTrackColor = ShutterGold,
                                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                        )
                        if (exposureIndex != 0) {
                            Text(
                                text = "Reset",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TelemetryEmerald,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onChangeExposureIndex(0) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Stadium Pill Action Bar: Stamp Pos | Note | Stamp Photo | Drive Sync
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DrawerPillButton(
                    icon = Icons.Rounded.AddLocationAlt,
                    label = config.stampPosition.shortLabel,
                    accent = Color.White,
                    onClick = onCycleStampPosition,
                    testTag = "camera_stamp_position_button",
                    modifier = Modifier.weight(1f)
                )
                DrawerPillButton(
                    icon = Icons.Rounded.EditNote,
                    label = if (config.customNote.isNotBlank()) "Note ON" else "Add Text",
                    accent = if (config.customNote.isNotBlank()) ShutterGold else Color.White,
                    onClick = onOpenNoteDialog,
                    testTag = "quick_tool_note",
                    modifier = Modifier.weight(1f)
                )
                DrawerPillButton(
                    icon = Icons.Rounded.Collections,
                    label = "Stamp",
                    accent = ShutterGold,
                    onClick = onImportPhoto,
                    testTag = "quick_tool_import",
                    modifier = Modifier.weight(1f)
                )
                DrawerPillButton(
                    icon = Icons.Rounded.CloudUpload,
                    label = "Drive",
                    accent = TelemetryEmerald,
                    onClick = onOpenDriveSync,
                    testTag = "quick_tool_drive",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DrawerPillButton(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White.copy(alpha = 0.08f),
        shape = CircleShape,
        modifier = modifier
            .clip(CircleShape)
            .border(0.9.dp, accent.copy(alpha = 0.34f), CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickToolTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    activeColor: Color = ShutterGold
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 480f),
        label = "quickTileScale"
    )
    val podColor by animateColorAsState(
        targetValue = if (active) activeColor else Color.White.copy(alpha = 0.09f),
        label = "quickTileBg"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(podColor)
                .border(
                    width = 1.dp,
                    color = if (active) activeColor else Color.White.copy(alpha = 0.16f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Color(0xFF141619) else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (active) activeColor else Color.White.copy(alpha = 0.9f),
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Digital Horizon Level indicator overlay drawn at the center of the camera preview.
 */
@Composable
fun DigitalHorizonLevelOverlay(
    pitchDeg: Float,
    rollDeg: Float,
    modifier: Modifier = Modifier
) {
    val isLevel = abs(rollDeg) <= 1.5f
    val lineColor = if (isLevel) FocusReticleGreen else ShutterGold

    Column(
        modifier = modifier.testTag("camera_digital_horizon_level"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(modifier = Modifier.size(width = 160.dp, height = 38.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val rad = Math.toRadians(rollDeg.coerceIn(-45f, 45f).toDouble())
            val halfSpan = 46.dp.toPx()
            val dx = (cos(rad) * halfSpan).toFloat()
            val dy = (sin(rad) * halfSpan).toFloat()

            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = Offset(cx - halfSpan - 14.dp.toPx(), cy),
                end = Offset(cx - halfSpan - 4.dp.toPx(), cy),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = Offset(cx + halfSpan + 4.dp.toPx(), cy),
                end = Offset(cx + halfSpan + 14.dp.toPx(), cy),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            drawLine(
                color = lineColor,
                start = Offset(cx - dx, cy - dy),
                end = Offset(cx + dx, cy + dy),
                strokeWidth = 2.6.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = lineColor,
                radius = 3.5.dp.toPx(),
                center = Offset(cx, cy)
            )
        }
        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "LVL ${rollDeg.roundToInt()}° · P ${pitchDeg.roundToInt()}°",
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = lineColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

fun parseMapQrCoordinates(qrText: String): Pair<Double, Double>? {
    val trimmed = qrText.trim()
    if (trimmed.startsWith("geo:", ignoreCase = true)) {
        val rawCoords = trimmed.substringAfter(":").substringBefore("?")
        val parts = rawCoords.split(",")
        if (parts.size >= 2) {
            val lat = parts[0].trim().toDoubleOrNull()
            val lon = parts[1].trim().toDoubleOrNull()
            if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                return lat to lon
            }
        }
    }
    if (trimmed.contains("maps.google.", ignoreCase = true) || trimmed.contains("google.com/maps", ignoreCase = true)) {
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        val qParam = uri.getQueryParameter("q") ?: uri.getQueryParameter("query")
        if (!qParam.isNullOrBlank()) {
            val parts = qParam.split(",")
            if (parts.size >= 2) {
                val lat = parts[0].trim().toDoubleOrNull()
                val lon = parts[1].trim().toDoubleOrNull()
                if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                    return lat to lon
                }
            }
        }
    }
    return null
}

/**
 * Clickable QR detection pill shown at the top of the viewfinder when ZXing decodes a live QR code.
 */
@Composable
fun DetectedQrActionBanner(
    qrText: String,
    context: Context,
    onDismiss: () -> Unit,
    onOpenGeoLocation: ((Double, Double) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val mapCoords = remember(qrText) { parseMapQrCoordinates(qrText) }
    val isWebUrl = qrText.startsWith("http://", ignoreCase = true) ||
        qrText.startsWith("https://", ignoreCase = true)

    Surface(
        color = Color(0xEB111822),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .padding(horizontal = 16.dp)
            .border(1.dp, ShutterGold.copy(alpha = 0.75f), RoundedCornerShape(14.dp))
            .clickable {
                if (mapCoords != null && onOpenGeoLocation != null) {
                    onOpenGeoLocation(mapCoords.first, mapCoords.second)
                } else if (isWebUrl) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(qrText)))
                    }.onFailure {
                        copyQrToClipboard(context, qrText)
                    }
                } else {
                    copyQrToClipboard(context, qrText)
                }
            }
            .testTag("camera_detected_qr_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.QrCodeScanner,
                contentDescription = "Detected QR code",
                tint = ShutterGold,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = when {
                        mapCoords != null && onOpenGeoLocation != null -> "Location QR • Tap for In-App Map"
                        isWebUrl -> "QR Link Detected • Tap to Open"
                        else -> "QR Text Detected • Tap to Copy"
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = ShutterGold
                )
                Text(
                    text = qrText,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = if (isWebUrl || mapCoords != null) Icons.AutoMirrored.Rounded.OpenInNew else Icons.Rounded.ContentCopy,
                contentDescription = null,
                tint = TelemetryEmerald,
                modifier = Modifier.size(16.dp)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss QR banner", tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
    }
}

private fun copyQrToClipboard(context: Context, text: String) {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cb?.setPrimaryClip(ClipData.newPlainText("Scanned QR", text))
    Toast.makeText(context, "QR copied: $text", Toast.LENGTH_SHORT).show()
}

fun launchPostCaptureAction(
    context: Context,
    entity: CapturedPhotoEntity,
    emailRecipient: String,
    preferEmail: Boolean
) {
    val uri = Uri.parse(entity.mediaUri)
    val coords = if (entity.latitude != null && entity.longitude != null) {
        String.format(Locale.US, "%.6f, %.6f", entity.latitude, entity.longitude)
    } else {
        "GPS unavailable"
    }
    val locationLine = listOf(entity.addressLine, entity.cityState, entity.country)
        .filter { it.isNotBlank() }
        .joinToString(", ")
    val bodyText = buildString {
        append("Captured with YVGPSCAM\n")
        append("Coordinates: $coords")
        if (locationLine.isNotBlank()) append("\nLocation: $locationLine")
        if (entity.customNote.isNotBlank()) append("\nNote: ${entity.customNote}")
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = if (entity.isVideo) "video/mp4" else "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, bodyText)
        if (preferEmail) {
            putExtra(Intent.EXTRA_SUBJECT, "YVGPSCAM Field Capture: ${entity.displayName}")
            if (emailRecipient.isNotBlank()) {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(emailRecipient))
            }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooserTitle = if (preferEmail) "Email Captured Photo" else "Share Geotagged Photo"
    runCatching {
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }
}
