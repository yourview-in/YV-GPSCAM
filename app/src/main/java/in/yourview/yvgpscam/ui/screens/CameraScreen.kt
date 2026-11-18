package `in`.yourview.yvgpscam.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesomeMosaic
import androidx.compose.material.icons.rounded.BurstMode
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.LocationSearching
import androidx.compose.material.icons.rounded.MotionPhotosAuto
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StayCurrentPortrait
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import `in`.yourview.yvgpscam.R
import `in`.yourview.yvgpscam.data.model.GpsSignalState
import `in`.yourview.yvgpscam.data.model.ScreenTapAction
import `in`.yourview.yvgpscam.data.model.StampOrientationMode
import `in`.yourview.yvgpscam.data.model.StampPosition
import `in`.yourview.yvgpscam.data.model.VideoQualityLevel
import `in`.yourview.yvgpscam.data.model.VolumeButtonAction
import `in`.yourview.yvgpscam.ui.components.AdBannerSlot
import `in`.yourview.yvgpscam.ui.components.CameraQuickToolsDrawer
import `in`.yourview.yvgpscam.ui.components.DetectedQrActionBanner
import `in`.yourview.yvgpscam.ui.components.DigitalHorizonLevelOverlay
import `in`.yourview.yvgpscam.ui.components.ModernDeckPodButton
import `in`.yourview.yvgpscam.ui.components.ModernFloatingCapsule
import `in`.yourview.yvgpscam.ui.components.ModernGlassCircleButton
import `in`.yourview.yvgpscam.ui.components.ModernMiniTogglePill
import `in`.yourview.yvgpscam.ui.components.StampPreviewCard
import `in`.yourview.yvgpscam.ui.components.SystemBarsImmersiveEffect
import `in`.yourview.yvgpscam.ui.components.launchPostCaptureAction
import `in`.yourview.yvgpscam.ui.theme.FocusReticleGreen
import `in`.yourview.yvgpscam.ui.theme.JetBrainsMonoFontFamily
import `in`.yourview.yvgpscam.ui.theme.ObsidianBorder
import `in`.yourview.yvgpscam.ui.theme.ObsidianSurface
import `in`.yourview.yvgpscam.ui.theme.ObsidianSurfaceHigh
import `in`.yourview.yvgpscam.ui.theme.ShutterGold
import `in`.yourview.yvgpscam.ui.theme.TelemetryEmerald
import `in`.yourview.yvgpscam.ui.viewmodel.CameraAspectRatio
import `in`.yourview.yvgpscam.ui.viewmodel.CameraCaptureMode
import `in`.yourview.yvgpscam.ui.viewmodel.CameraViewModel
import `in`.yourview.yvgpscam.util.QrCodeHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

private data class FocusReticleState(
    val point: Offset,
    val isFocused: Boolean?,
    val sequenceId: Int
)

enum class DeviceCaptureOrientation(
    val label: String,
    val angleDegrees: Float,
    val surfaceRotation: Int
) {
    PORTRAIT("PORTRAIT", 0f, Surface.ROTATION_0),
    LANDSCAPE_RIGHT("LANDSCAPE", -90f, Surface.ROTATION_270),
    PORTRAIT_UPSIDE_DOWN("PORTRAIT", 180f, Surface.ROTATION_180),
    LANDSCAPE_LEFT("LANDSCAPE", 90f, Surface.ROTATION_90);

    val isLandscape: Boolean
        get() = this == LANDSCAPE_LEFT || this == LANDSCAPE_RIGHT
}

internal fun resolveDeviceCaptureOrientation(
    sensorDegrees: Int,
    current: DeviceCaptureOrientation
): DeviceCaptureOrientation {
    if (sensorDegrees == OrientationEventListener.ORIENTATION_UNKNOWN || sensorDegrees < 0) {
        return current
    }
    val normalized = ((sensorDegrees % 360) + 360) % 360
    return when {
        normalized >= 330 || normalized <= 30 -> DeviceCaptureOrientation.PORTRAIT
        normalized in 60..120 -> DeviceCaptureOrientation.LANDSCAPE_RIGHT
        normalized in 150..210 -> DeviceCaptureOrientation.PORTRAIT_UPSIDE_DOWN
        normalized in 240..300 -> DeviceCaptureOrientation.LANDSCAPE_LEFT
        else -> current
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    hasPendingUpdate: Boolean = false,
    onOpenGallery: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenFileName: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenImageOnMap: () -> Unit = {},
    onOpenInAppMapCoords: (Double, Double) -> Unit = { _, _ -> },
    onOpenPdfReport: () -> Unit = {},
    onOpenCloudSync: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val isDeviceLocationEnabled by viewModel.isDeviceLocationEnabled.collectAsStateWithLifecycle()
    val stampConfig by viewModel.stampConfig.collectAsStateWithLifecycle()
    val hudState by viewModel.hudState.collectAsStateWithLifecycle()
    val miniMapBitmap by viewModel.miniMapBitmap.collectAsStateWithLifecycle()
    val latestPhoto by viewModel.latestPhoto.collectAsStateWithLifecycle()
    val horizonTilt by viewModel.horizonTiltDegrees.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasCameraPermission = grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasLocationPermission = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        hasAudioPermission = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionsUpdated(hasLocationPermission)
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
    }

    val photoImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.stampImportedPhoto(uri)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            viewModel.onPermissionsUpdated(true)
        }
    }

    DisposableEffect(lifecycleOwner, hasLocationPermission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
                hasAudioPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                viewModel.onPermissionsUpdated(hasLocationPermission)
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.onLifecyclePause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showNoteDialog by remember { mutableStateOf(false) }
    var noteDraft by remember(stampConfig.customNote) { mutableStateOf(stampConfig.customNote) }
    var brandDraft by remember(stampConfig.customBrandLabel) { mutableStateOf(stampConfig.customBrandLabel) }
    var shutterFlash by remember { mutableStateOf(false) }
    var focusReticle by remember { mutableStateOf<FocusReticleState?>(null) }
    var focusSequence by remember { mutableIntStateOf(0) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var activeRawVideoFile by remember { mutableStateOf<File?>(null) }
    var activeVideoFrontCamera by remember { mutableStateOf(false) }
    var isVideoPaused by remember { mutableStateOf(false) }
    var isTorchEnabled by remember { mutableStateOf(false) }
    var isBurstMode by remember { mutableStateOf(false) }
    var exposureIndex by remember { mutableIntStateOf(0) }
    var autoCaptureJob by remember { mutableStateOf<Job?>(null) }
    var autoCaptureProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(shutterFlash) {
        if (shutterFlash) {
            delay(130)
            shutterFlash = false
        }
    }

    LaunchedEffect(focusReticle?.sequenceId) {
        val currentSeq = focusReticle?.sequenceId ?: return@LaunchedEffect
        delay(1600)
        if (focusReticle?.sequenceId == currentSeq) {
            focusReticle = null
        }
    }

    LaunchedEffect(hudState.lensFacing) {
        focusReticle = null
        if (hudState.lensFacing == CameraSelector.LENS_FACING_FRONT) {
            isTorchEnabled = false
        }
        exposureIndex = 0
    }

    LaunchedEffect(hudState.statusMessage) {
        if (hudState.statusMessage != null) {
            delay(2400)
            viewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(hudState.isRecordingVideo, isVideoPaused) {
        if (!hudState.isRecordingVideo) return@LaunchedEffect
        var elapsed = hudState.recordingElapsedSeconds
        while (true) {
            viewModel.setRecordingState(isRecording = true, elapsedSeconds = elapsed)
            delay(1000)
            if (!isVideoPaused) {
                elapsed += 1
            }
        }
    }

    val resolutionSelector = remember(hudState.aspectRatio) {
        val strategy = when (hudState.aspectRatio) {
            CameraAspectRatio.RATIO_16_9 -> AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
            CameraAspectRatio.RATIO_4_3, CameraAspectRatio.RATIO_1_1 -> AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        }
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(strategy)
            .build()
    }

    val imageCapture = remember(hudState.aspectRatio, resolutionSelector) {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(resolutionSelector)
            .build()
    }

    val videoCapture = remember(stampConfig.videoQuality) {
        val qualitySelector = when (stampConfig.videoQuality) {
            VideoQualityLevel.UHD_4K -> QualitySelector.from(
                Quality.UHD,
                FallbackStrategy.higherQualityOrLowerThan(Quality.FHD)
            )
            VideoQualityLevel.FHD_1080P -> QualitySelector.from(
                Quality.FHD,
                FallbackStrategy.higherQualityOrLowerThan(Quality.HD)
            )
            VideoQualityLevel.HD_720P -> QualitySelector.from(Quality.HD)
        }
        val recorder = Recorder.Builder().setQualitySelector(qualitySelector).build()
        VideoCapture.withOutput(recorder)
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val qrAnalysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var lastQrScanMs by remember { mutableLongStateOf(0L) }

    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }

    DisposableEffect(Unit) {
        onDispose {
            autoCaptureJob?.cancel()
            activeRecording?.stop()
            cameraExecutor.shutdown()
            qrAnalysisExecutor.shutdown()
        }
    }

    LaunchedEffect(hudState.captureMode) {
        if (hudState.captureMode != CameraCaptureMode.AUTO_CAPTURE && autoCaptureJob?.isActive == true) {
            autoCaptureJob?.cancel()
            autoCaptureJob = null
            autoCaptureProgress = 0f
            viewModel.setAutoCaptureState(isActive = false, count = 0, countdownSec = 0)
        }
    }

    var activeCamera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(activeCamera, isTorchEnabled, hudState.lensFacing) {
        runCatching {
            activeCamera?.cameraControl?.enableTorch(
                isTorchEnabled && hudState.lensFacing == CameraSelector.LENS_FACING_BACK
            )
        }
    }

    LaunchedEffect(activeCamera, exposureIndex) {
        runCatching {
            activeCamera?.cameraControl?.setExposureCompensationIndex(exposureIndex)
        }
    }

    val configuration = LocalConfiguration.current
    var deviceOrientation by remember {
        mutableStateOf(
            if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                DeviceCaptureOrientation.LANDSCAPE_LEFT
            } else {
                DeviceCaptureOrientation.PORTRAIT
            }
        )
    }

    DisposableEffect(context) {
        val orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                val next = resolveDeviceCaptureOrientation(orientation, deviceOrientation)
                if (next != deviceOrientation) {
                    deviceOrientation = next
                }
            }
        }
        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }
        onDispose { orientationListener.disable() }
    }

    val effectiveStampOrientationAngle = remember(deviceOrientation, stampConfig.stampOrientation) {
        when (stampConfig.stampOrientation) {
            StampOrientationMode.AUTOMATIC -> deviceOrientation.angleDegrees
            StampOrientationMode.PORTRAIT -> 0f
            StampOrientationMode.LANDSCAPE -> 90f
        }
    }
    val effectiveStampOrientationDegrees = remember(effectiveStampOrientationAngle) {
        effectiveStampOrientationAngle.roundToInt()
    }

    LaunchedEffect(hudState.flashMode, imageCapture) {
        imageCapture.flashMode = hudState.flashMode
    }

    LaunchedEffect(deviceOrientation, imageCapture) {
        imageCapture.targetRotation = deviceOrientation.surfaceRotation
        videoCapture.targetRotation = deviceOrientation.surfaceRotation
    }

    LaunchedEffect(hudState.zoomRatio, activeCamera) {
        activeCamera?.cameraControl?.setZoomRatio(hudState.zoomRatio)
    }

    // Collect post-capture Auto-Share / Email / Share Mode actions
    LaunchedEffect(Unit) {
        viewModel.postCaptureEvent.collectLatest { entity ->
            val cfg = viewModel.stampConfig.value
            launchPostCaptureAction(
                context = context,
                entity = entity,
                emailRecipient = cfg.defaultReportEmail,
                preferEmail = cfg.emailAfterCapture && !cfg.autoShareAfterCapture
            )
        }
    }

    fun fireSingleImageCapture(shareNow: Boolean, front: Boolean) {
        shutterFlash = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        imageCapture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()
                    viewModel.saveCapturedPhoto(
                        jpegBytes = bytes,
                        isFrontCamera = front,
                        deviceOrientationDegrees = effectiveStampOrientationDegrees,
                        triggerShareImmediately = shareNow
                    )
                }

                override fun onError(exception: ImageCaptureException) {
                    viewModel.setStatusMessage(exception.message ?: "Capture failed")
                }
            }
        )
    }

    fun stopAutoCaptureLoop(notify: Boolean = true) {
        val shotsTaken = viewModel.hudState.value.autoCaptureCount
        autoCaptureJob?.cancel()
        autoCaptureJob = null
        autoCaptureProgress = 0f
        viewModel.setAutoCaptureState(isActive = false, count = 0, countdownSec = 0)
        if (notify && shotsTaken > 0) {
            viewModel.setStatusMessage("Auto Capture stopped ($shotsTaken saved)")
        }
    }

    fun toggleAutoCaptureLoop() {
        if (autoCaptureJob?.isActive == true) {
            stopAutoCaptureLoop(notify = true)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            return
        }
        autoCaptureJob = scope.launch {
            var count = 0
            while (isActive) {
                count += 1
                val liveHud = viewModel.hudState.value
                val intervalSec = liveHud.autoCaptureIntervalSeconds
                val front = liveHud.lensFacing == CameraSelector.LENS_FACING_FRONT
                viewModel.setAutoCaptureState(isActive = true, count = count, countdownSec = intervalSec)
                fireSingleImageCapture(shareNow = false, front = front)
                val stepMs = 100L
                val totalSteps = (intervalSec * 1000L / stepMs).toInt().coerceAtLeast(1)
                for (step in 1..totalSteps) {
                    autoCaptureProgress = step.toFloat() / totalSteps.toFloat()
                    val remaining = (((totalSteps - step) * stepMs + 999L) / 1000L).toInt()
                    viewModel.setAutoCaptureState(isActive = true, count = count, countdownSec = remaining)
                    delay(stepMs)
                }
                autoCaptureProgress = 0f
            }
        }
    }

    fun executePhotoShutterNow(burstOverride: Boolean = false) {
        val useBurst = (burstOverride || isBurstMode) && hudState.captureMode != CameraCaptureMode.VIDEO
        val shotCount = if (useBurst) 3 else 1
        val shareNow = hudState.captureMode == CameraCaptureMode.SHARE_PHOTO && !useBurst
        val front = hudState.lensFacing == CameraSelector.LENS_FACING_FRONT

        scope.launch {
            for (shotIdx in 1..shotCount) {
                if (useBurst) {
                    viewModel.setStatusMessage("Burst $shotIdx/$shotCount")
                }
                fireSingleImageCapture(shareNow = shareNow, front = front)
                if (shotIdx < shotCount) delay(360L)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun toggleVideoRecording() {
        val currentRec = activeRecording
        if (currentRec != null) {
            currentRec.stop()
            activeRecording = null
            isVideoPaused = false
            viewModel.setRecordingState(isRecording = false, elapsedSeconds = 0)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            return
        }

        val fileTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val tempVideoFile = File(context.cacheDir, "raw_vid_$fileTimestamp.mp4")
        if (tempVideoFile.exists()) tempVideoFile.delete()
        val outputOptions = FileOutputOptions.Builder(tempVideoFile).build()

        val pending = videoCapture.output.prepareRecording(context, outputOptions).apply {
            if (stampConfig.recordVideoWithVoice && hasAudioPermission) {
                withAudioEnabled()
            }
        }

        val isFront = hudState.lensFacing == CameraSelector.LENS_FACING_FRONT
        activeRawVideoFile = tempVideoFile
        activeVideoFrontCamera = isFront
        isVideoPaused = false
        viewModel.setRecordingState(isRecording = true, elapsedSeconds = 0)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                val finishedFile = activeRawVideoFile ?: tempVideoFile
                val usedFront = activeVideoFrontCamera
                activeRecording = null
                activeRawVideoFile = null
                isVideoPaused = false
                if (!event.hasError() && finishedFile.exists()) {
                    viewModel.saveRecordedVideo(finishedFile, isFrontCamera = usedFront)
                } else {
                    runCatching { finishedFile.delete() }
                    viewModel.setRecordingState(isRecording = false, elapsedSeconds = 0)
                    viewModel.setStatusMessage("Video recording stopped (${event.error})")
                }
            }
        }
    }

    fun triggerPrimaryShutter(burstLongPress: Boolean = false) {
        if (!hasCameraPermission || hudState.isCapturing || hudState.countdownRemaining > 0) return
        if (hudState.captureMode == CameraCaptureMode.VIDEO) {
            toggleVideoRecording()
            return
        }
        if (hudState.captureMode == CameraCaptureMode.AUTO_CAPTURE && !burstLongPress) {
            toggleAutoCaptureLoop()
            return
        }
        val delaySec = if (burstLongPress) 0 else stampConfig.timerDelaySeconds
        if (delaySec <= 0) {
            executePhotoShutterNow(burstOverride = burstLongPress)
        } else {
            scope.launch {
                for (rem in delaySec downTo 1) {
                    viewModel.setCountdownRemaining(rem)
                    delay(1000)
                }
                viewModel.setCountdownRemaining(0)
                executePhotoShutterNow(burstOverride = false)
            }
        }
    }

    // Hardware Volume Button Capture Listener
    LaunchedEffect(Unit) {
        viewModel.volumeEvents.collectLatest { action ->
            if (action == VolumeButtonAction.PHOTO_CAPTURE) {
                triggerPrimaryShutter()
            }
        }
    }

    val isFullScreenCamera = hudState.isFullScreenCamera && hasCameraPermission
    SystemBarsImmersiveEffect(immersive = isFullScreenCamera)
    BackHandler(enabled = hudState.isAutoCapturing || hudState.showToolsDrawer || isFullScreenCamera) {
        when {
            hudState.isAutoCapturing -> stopAutoCaptureLoop(notify = true)
            hudState.showToolsDrawer -> viewModel.closeToolsDrawer()
            else -> viewModel.setFullScreenCamera(false)
        }
    }

    val isAutoMode = hudState.captureMode == CameraCaptureMode.AUTO_CAPTURE
    val exposureState = activeCamera?.cameraInfo?.exposureState
    val evRange = exposureState?.exposureCompensationRange
    val minEvIdx = evRange?.lower ?: 0
    val maxEvIdx = evRange?.upper ?: 0
    val evStep = exposureState?.exposureCompensationStep?.toFloat() ?: 0.33f

    // Modernised Floating Glass Top Toolbar
    val topIconToolbar: @Composable () -> Unit = {
        val flashIcon = when (hudState.flashMode) {
            ImageCapture.FLASH_MODE_ON -> Icons.Rounded.FlashOn
            ImageCapture.FLASH_MODE_AUTO -> Icons.Rounded.FlashAuto
            else -> Icons.Rounded.FlashOff
        }
        val activeToolsCount = remember(
            hudState.showGrid,
            stampConfig.timerDelaySeconds,
            isTorchEnabled,
            isBurstMode,
            isAutoMode,
            stampConfig.showDigitalLevel,
            exposureIndex
        ) {
            listOf(
                hudState.showGrid,
                stampConfig.timerDelaySeconds > 0,
                isTorchEnabled,
                isBurstMode,
                isAutoMode,
                stampConfig.showDigitalLevel,
                exposureIndex != 0
            ).count { it }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xEB0A0D13),
                            Color(0xC00D1119),
                            if (isFullScreenCamera) Color.Transparent else Color(0xCC0D1119)
                        )
                    )
                )
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 10.dp)
                    .testTag("camera_quick_settings_bar"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Pro Tools Pill
                ModernGlassCircleButton(
                    icon = Icons.Rounded.Tune,
                    contentDescription = "Toggle camera tools",
                    active = hudState.showToolsDrawer,
                    badgeDot = !hudState.showToolsDrawer && activeToolsCount > 0,
                    badgeDotColor = ShutterGold,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.toggleToolsDrawer()
                    },
                    testTag = "camera_tools_drawer_toggle"
                )

                // 2. Center Grouped Glass Capsule: Flash | Auto Capture | Google Map | Switch Lens
                ModernFloatingCapsule {
                    ModernGlassCircleButton(
                        icon = flashIcon,
                        contentDescription = stringResource(R.string.toggle_flash),
                        active = hudState.flashMode != ImageCapture.FLASH_MODE_OFF,
                        standaloneGlass = false,
                        badgeText = when (hudState.flashMode) {
                            ImageCapture.FLASH_MODE_ON -> "ON"
                            ImageCapture.FLASH_MODE_AUTO -> "A"
                            else -> null
                        },
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.cycleFlashMode()
                        },
                        testTag = "flash_toggle_button"
                    )

                    ModernGlassCircleButton(
                        icon = Icons.Rounded.MotionPhotosAuto,
                        contentDescription = "Toggle Auto Capture mode",
                        active = isAutoMode,
                        activeColor = TelemetryEmerald,
                        standaloneGlass = false,
                        badgeText = if (isAutoMode) "${hudState.autoCaptureIntervalSeconds}s" else null,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.toggleAutoCaptureMode()
                            viewModel.setStatusMessage(
                                if (!isAutoMode) {
                                    "Auto Capture (${hudState.autoCaptureIntervalSeconds}s interval)"
                                } else {
                                    "Single Photo Mode"
                                }
                            )
                        },
                        testTag = "camera_auto_capture_toggle_button"
                    )

                    ModernGlassCircleButton(
                        icon = Icons.Rounded.TravelExplore,
                        contentDescription = "Open interactive in-app map",
                        active = false,
                        standaloneGlass = false,
                        badgeDot = telemetry.hasFix,
                        badgeDotColor = TelemetryEmerald,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenImageOnMap()
                        },
                        testTag = "camera_image_on_map_button"
                    )

                    ModernGlassCircleButton(
                        icon = Icons.Rounded.FlipCameraAndroid,
                        contentDescription = stringResource(R.string.switch_camera),
                        active = hudState.lensFacing == CameraSelector.LENS_FACING_FRONT,
                        standaloneGlass = false,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.toggleLensFacing()
                        },
                        testTag = "switch_camera_button"
                    )
                }

                // 3. Settings Pill (shows emerald badge when update available)
                ModernGlassCircleButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = "Open camera and app settings",
                    active = false,
                    badgeDot = hasPendingUpdate,
                    badgeDotColor = TelemetryEmerald,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenSettings()
                    },
                    testTag = "camera_settings_button"
                )
            }
        }
    }

    val floatingToolsOverlay: @Composable () -> Unit = {
        AnimatedVisibility(
            visible = hudState.showToolsDrawer,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 60.dp)
            ) {
                val maxDrawerHeight = (maxHeight * 0.78f).coerceAtLeast(240.dp)
                val scrimInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f))
                        .clickable(
                            interactionSource = scrimInteraction,
                            indication = null
                        ) { viewModel.closeToolsDrawer() }
                )
                AnimatedVisibility(
                    visible = hudState.showToolsDrawer,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    CameraQuickToolsDrawer(
                        hudState = hudState,
                        config = stampConfig,
                        isTorchEnabled = isTorchEnabled,
                        isBurstMode = isBurstMode,
                        isAutoCaptureMode = isAutoMode,
                        exposureIndex = exposureIndex,
                        minExposureIndex = minEvIdx,
                        maxExposureIndex = maxEvIdx,
                        exposureStepEv = evStep,
                        onCycleRatio = { viewModel.cycleAspectRatio() },
                        onToggleGrid = { viewModel.toggleGrid() },
                        onCycleTimer = { viewModel.cycleTimerDelay() },
                        onToggleFullView = {
                            viewModel.toggleFullScreenCamera()
                            viewModel.closeToolsDrawer()
                        },
                        onToggleTorch = {
                            if (hudState.lensFacing == CameraSelector.LENS_FACING_FRONT) {
                                viewModel.setStatusMessage("Torch is only available on the rear camera")
                            } else {
                                isTorchEnabled = !isTorchEnabled
                            }
                        },
                        onToggleMirror = {
                            viewModel.updateStampConfig { it.copy(mirrorFrontCamera = !it.mirrorFrontCamera) }
                        },
                        onToggleVoice = {
                            val nextVoice = !stampConfig.recordVideoWithVoice
                            viewModel.updateStampConfig { it.copy(recordVideoWithVoice = nextVoice) }
                            if (nextVoice && !hasAudioPermission) {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onToggleLevel = {
                            viewModel.updateStampConfig { it.copy(showDigitalLevel = !it.showDigitalLevel) }
                        },
                        onToggleAutoCapture = {
                            viewModel.toggleAutoCaptureMode()
                            viewModel.closeToolsDrawer()
                        },
                        onToggleBurstMode = {
                            isBurstMode = !isBurstMode
                            viewModel.setStatusMessage(if (isBurstMode) "3-Shot Burst Enabled" else "Single Capture Mode")
                        },
                        onCycleMapStyle = { viewModel.cycleMapStyle() },
                        onToggleOfflineMapMode = {
                            viewModel.updateStampConfig { it.copy(offlineOnlyMapMode = !it.offlineOnlyMapMode) }
                        },
                        onChangeExposureIndex = { idx -> exposureIndex = idx },
                        onCycleStampPosition = { viewModel.cycleStampPosition() },
                        onOpenNoteDialog = {
                            viewModel.closeToolsDrawer()
                            showNoteDialog = true
                        },
                        onImportPhoto = {
                            viewModel.closeToolsDrawer()
                            photoImportLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onOpenDriveSync = {
                            viewModel.closeToolsDrawer()
                            onOpenCloudSync()
                        },
                        modifier = Modifier.heightIn(max = maxDrawerHeight)
                    )
                }
            }
        }
    }

    val livePreviewContent = remember(context) {
        movableContentOf { previewModifier: Modifier ->
            val previewView = remember {
                PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            }

            LaunchedEffect(
                hudState.lensFacing,
                hudState.captureMode,
                stampConfig.qrDetectionEnabled,
                videoCapture,
                imageCapture,
                resolutionSelector
            ) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    runCatching {
                        val provider = cameraProviderFuture.get()
                        val preview = Preview.Builder()
                            .setResolutionSelector(resolutionSelector)
                            .build()
                            .also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                        val selector = CameraSelector.Builder()
                            .requireLensFacing(hudState.lensFacing)
                            .build()
                        provider.unbindAll()

                        val cam = if (hudState.captureMode == CameraCaptureMode.VIDEO) {
                            imageAnalysis.clearAnalyzer()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                selector,
                                preview,
                                videoCapture
                            )
                        } else if (stampConfig.qrDetectionEnabled) {
                            imageAnalysis.setAnalyzer(qrAnalysisExecutor) { proxy ->
                                val now = System.currentTimeMillis()
                                if (now - lastQrScanMs >= 700L) {
                                    lastQrScanMs = now
                                    val plane = proxy.planes.firstOrNull()
                                    if (plane != null) {
                                        val buf = plane.buffer
                                        val bytes = ByteArray(buf.remaining())
                                        buf.get(bytes)
                                        val decoded = QrCodeHelper.decodeQrFromYPlane(
                                            yBytes = bytes,
                                            width = proxy.width,
                                            height = proxy.height
                                        )
                                        if (decoded != null) {
                                            viewModel.updateDetectedQrCode(decoded)
                                        }
                                    }
                                }
                                proxy.close()
                            }
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                selector,
                                preview,
                                imageCapture,
                                imageAnalysis
                            )
                        } else {
                            imageAnalysis.clearAnalyzer()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                selector,
                                preview,
                                imageCapture
                            )
                        }

                        activeCamera = cam
                        cam.cameraInfo.zoomState.value?.let { zState ->
                            viewModel.updateZoomBounds(zState.minZoomRatio, zState.maxZoomRatio)
                        }
                    }
                }, ContextCompat.getMainExecutor(context))
            }

            val scaleDetector = remember(previewView) {
                ScaleGestureDetector(
                    context,
                    object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        override fun onScale(detector: ScaleGestureDetector): Boolean {
                            viewModel.setZoomRatio(hudState.zoomRatio * detector.scaleFactor)
                            return true
                        }
                    }
                )
            }

            AndroidView(
                factory = {
                    previewView.apply {
                        setOnTouchListener { v, event ->
                            scaleDetector.onTouchEvent(event)
                            if (event.pointerCount == 1 && !scaleDetector.isInProgress && event.action == MotionEvent.ACTION_UP) {
                                when (stampConfig.screenTapAction) {
                                    ScreenTapAction.CAPTURE -> {
                                        triggerPrimaryShutter()
                                    }

                                    ScreenTapAction.FOCUS -> {
                                        val tapOffset = Offset(event.x, event.y)
                                        focusSequence += 1
                                        val seq = focusSequence
                                        focusReticle = FocusReticleState(
                                            point = tapOffset,
                                            isFocused = null,
                                            sequenceId = seq
                                        )
                                        val factory = meteringPointFactory
                                        val point = factory.createPoint(event.x, event.y)
                                        val action = FocusMeteringAction.Builder(
                                            point,
                                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                                        )
                                            .setAutoCancelDuration(4, TimeUnit.SECONDS)
                                            .build()

                                        val future = activeCamera?.cameraControl?.startFocusAndMetering(action)
                                        future?.addListener(
                                            {
                                                val focused = runCatching { future.get().isFocusSuccessful }
                                                    .getOrDefault(true)
                                                if (focusReticle?.sequenceId == seq) {
                                                    focusReticle = focusReticle?.copy(isFocused = focused)
                                                }
                                            },
                                            ContextCompat.getMainExecutor(context)
                                        )
                                    }

                                    ScreenTapAction.OFF -> Unit
                                }
                                v.performClick()
                            }
                            true
                        }
                    }
                },
                modifier = previewModifier
            )
        }
    }

    val zoomOptions = remember(hudState.minZoomRatio, hudState.maxZoomRatio) {
        buildList {
            if (hudState.minZoomRatio <= 0.7f) add(0.5f)
            add(1f)
            if (hudState.maxZoomRatio >= 2f) add(2f)
            if (hudState.minZoomRatio > 0.7f && hudState.maxZoomRatio >= 3f) add(3f)
        }
    }

    val viewfinderOverlays: @Composable (Boolean) -> Unit = { fullScreenOverlay ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val topInsetPad = if (fullScreenOverlay) 68.dp else 8.dp
            val bottomControlsReserve = if (fullScreenOverlay) 156.dp else 0.dp
            val isFrontCam = hudState.lensFacing == CameraSelector.LENS_FACING_FRONT

            if (hudState.showGrid) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val lineColor = Color.White.copy(alpha = 0.16f)
                    drawLine(lineColor, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), strokeWidth = 0.9.dp.toPx())
                    drawLine(lineColor, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), strokeWidth = 0.9.dp.toPx())
                    drawLine(lineColor, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), strokeWidth = 0.9.dp.toPx())
                    drawLine(lineColor, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), strokeWidth = 0.9.dp.toPx())
                }
            }

            if (stampConfig.showDigitalLevel) {
                DigitalHorizonLevelOverlay(
                    pitchDeg = horizonTilt.first,
                    rollDeg = horizonTilt.second,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                val activeReticle = focusReticle
                if (activeReticle != null) {
                    TapFocusReticleOverlay(
                        reticle = activeReticle,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CenterFocusBracketsGuide(modifier = Modifier.align(Alignment.Center))
                }
            }

            // Countdown Overlay (3s / 10s Timer)
            if (hudState.countdownRemaining > 0) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(92.dp)
                        .border(2.dp, ShutterGold, CircleShape)
                        .testTag("camera_timer_countdown_badge")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = hudState.countdownRemaining.toString(),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ShutterGold
                        )
                    }
                }
            }

            // Shutter Flash
            AnimatedVisibility(
                visible = shutterFlash,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.55f))
                )
            }

            // Top-Left: Auto-Capture Live Pill OR Video Recording Timer OR Orientation Pill
            if (hudState.isAutoCapturing) {
                Surface(
                    color = Color(0xD90A2E23),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 10.dp, top = topInsetPad)
                        .border(1.dp, TelemetryEmerald, CircleShape)
                        .clip(CircleShape)
                        .clickable { stopAutoCaptureLoop(notify = true) }
                        .testTag("camera_auto_capture_status_pill")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MotionPhotosAuto,
                            contentDescription = "Auto Capture active",
                            tint = TelemetryEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "AUTO #${hudState.autoCaptureCount} • ${hudState.autoCaptureCountdownSec}s • STOP",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            } else if (hudState.isRecordingVideo) {
                val mins = hudState.recordingElapsedSeconds / 60
                val secs = hudState.recordingElapsedSeconds % 60
                Surface(
                    color = Color(0xFFD92D20),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 10.dp, top = topInsetPad)
                        .testTag("video_recording_timer_pill")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                        Text(
                            text = String.format(Locale.US, "REC %02d:%02d", mins, secs),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            } else {
                ViewfinderOrientationStatusPill(
                    orientation = deviceOrientation,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 10.dp, top = topInsetPad)
                )
            }

            // Top-Right: Modern GPS Signal & Offline Satellite Status Pill
            CompactGpsStatusPill(
                signalState = telemetry.signalState,
                accuracyMeters = telemetry.accuracyMeters,
                satellitesUsed = telemetry.satellitesUsed,
                satellitesVisible = telemetry.satellitesVisible,
                offlineOnly = stampConfig.offlineOnlyMapMode,
                isDeviceLocationEnabled = isDeviceLocationEnabled,
                hasLocationPermission = hasLocationPermission,
                onTapAction = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    when {
                        !hasLocationPermission -> {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                        !isDeviceLocationEnabled -> {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            }
                        }
                        else -> {
                            viewModel.refreshDeviceLocation(true)
                            viewModel.setStatusMessage("Refreshing device GPS fix...")
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 10.dp, top = topInsetPad)
            )

            // Detected Live QR Code Banner
            val qrText = hudState.detectedQrText
            if (qrText != null && stampConfig.qrDetectionEnabled) {
                DetectedQrActionBanner(
                    qrText = qrText,
                    context = context,
                    onDismiss = { viewModel.updateDetectedQrCode(null) },
                    onOpenGeoLocation = { lat, lon ->
                        viewModel.updateDetectedQrCode(null)
                        onOpenInAppMapCoords(lat, lon)
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topInsetPad + 32.dp)
                )
            }

            // Stamp Preview Card + Floating Zoom & Quick Control Strip
            val isTopStamp = stampConfig.stampPosition == StampPosition.TOP_LEFT ||
                stampConfig.stampPosition == StampPosition.TOP_CENTER ||
                stampConfig.stampPosition == StampPosition.TOP_RIGHT

            if (isTopStamp) {
                val topAlign = when (stampConfig.stampPosition) {
                    StampPosition.TOP_LEFT -> Alignment.TopStart
                    StampPosition.TOP_RIGHT -> Alignment.TopEnd
                    else -> Alignment.TopCenter
                }
                Box(
                    modifier = Modifier
                        .align(topAlign)
                        .padding(horizontal = 8.dp, vertical = topInsetPad + 34.dp)
                ) {
                    StampPreviewCard(
                        telemetry = telemetry,
                        config = stampConfig,
                        miniMapBitmap = miniMapBitmap,
                        isFrontCamera = isFrontCam,
                        onMiniMapClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenImageOnMap()
                        },
                        onClick = { viewModel.cycleStampTemplate() }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = bottomControlsReserve + 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isTopStamp) {
                    val horizontalAlign = when (stampConfig.stampPosition) {
                        StampPosition.BOTTOM_LEFT -> Alignment.Start
                        StampPosition.BOTTOM_RIGHT -> Alignment.End
                        else -> Alignment.CenterHorizontally
                    }
                    Box(modifier = Modifier.align(horizontalAlign)) {
                        StampPreviewCard(
                            telemetry = telemetry,
                            config = stampConfig,
                            miniMapBitmap = miniMapBitmap,
                            isFrontCamera = isFrontCam,
                            onMiniMapClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onOpenImageOnMap()
                            },
                            onClick = { viewModel.cycleStampTemplate() }
                        )
                    }
                }

                // Auto-Capture Interval Selector Strip when AUTO mode is active
                AnimatedVisibility(visible = hudState.captureMode == CameraCaptureMode.AUTO_CAPTURE) {
                    ModernFloatingCapsule(
                        containerColor = Color(0xBA0E1621),
                        borderColor = TelemetryEmerald.copy(alpha = 0.55f),
                        modifier = Modifier.testTag("auto_capture_interval_bar")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MotionPhotosAuto,
                            contentDescription = "Auto capture interval",
                            tint = TelemetryEmerald,
                            modifier = Modifier
                                .padding(start = 6.dp, end = 4.dp)
                                .size(15.dp)
                        )
                        listOf(3, 5, 10).forEach { sec ->
                            val selected = hudState.autoCaptureIntervalSeconds == sec
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.setAutoCaptureInterval(sec)
                                    }
                                    .testTag("auto_interval_${sec}s")
                            ) {
                                Surface(
                                    color = if (selected) TelemetryEmerald else Color.Transparent,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "${sec}s",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (selected) Color(0xFF0A1410) else Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Floating Zoom + Quick Pro Toggle Strip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ZoomSelectorPill(
                        zoomOptions = zoomOptions,
                        currentZoom = hudState.zoomRatio,
                        onSelectZoom = { viewModel.setZoomRatio(it) }
                    )

                    if (hudState.lensFacing == CameraSelector.LENS_FACING_BACK) {
                        ModernMiniTogglePill(
                            icon = Icons.Rounded.FlashlightOn,
                            label = if (isTorchEnabled) "ON" else "Torch",
                            active = isTorchEnabled,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isTorchEnabled = !isTorchEnabled
                            },
                            testTag = "camera_quick_torch_pill"
                        )
                    }

                    if (hudState.captureMode != CameraCaptureMode.VIDEO) {
                        ModernMiniTogglePill(
                            icon = Icons.Rounded.BurstMode,
                            label = if (isBurstMode) "3x" else "Burst",
                            active = isBurstMode,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isBurstMode = !isBurstMode
                            },
                            testTag = "camera_quick_burst_pill"
                        )
                    }
                }
            }
        }
    }

    // Modernised Bottom Pro Camera Deck
    val bottomCameraDeck: @Composable () -> Unit = {
        val visibleModes = remember(stampConfig.showReportsModeOnCamera) {
            buildList {
                add(CameraCaptureMode.SHARE_PHOTO)
                add(CameraCaptureMode.PHOTO)
                add(CameraCaptureMode.AUTO_CAPTURE)
                add(CameraCaptureMode.VIDEO)
                if (stampConfig.showReportsModeOnCamera) {
                    add(CameraCaptureMode.REPORTS)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            if (isFullScreenCamera) Color(0xCE0C1017) else Color(0xF50E131B),
                            if (isFullScreenCamera) Color(0xDF07090D) else Color(0xFF07090D)
                        )
                    )
                )
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Subtle modern hairline glow divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.22f),
                                TelemetryEmerald.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.22f),
                                Color.Transparent
                            )
                        )
                    )
            )

            AnimatedVisibility(visible = hudState.statusMessage != null) {
                Surface(
                    color = ObsidianSurfaceHigh,
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .border(1.dp, ShutterGold.copy(alpha = 0.45f), CircleShape)
                ) {
                    Text(
                        text = hudState.statusMessage.orEmpty(),
                        color = ShutterGold,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }
            }

            // Sleek Stadium Mode Rail with Icons.Rounded Glyphs
            ModernFloatingCapsule(
                containerColor = Color.White.copy(alpha = 0.07f),
                borderColor = Color.White.copy(alpha = 0.14f),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                visibleModes.forEach { mode ->
                    val isSelected = hudState.captureMode == mode
                    val activeAccent = if (mode == CameraCaptureMode.AUTO_CAPTURE) TelemetryEmerald else ShutterGold
                    val pillBg by animateColorAsState(
                        targetValue = if (isSelected) activeAccent else Color.Transparent,
                        label = "modePillBg"
                    )
                    val modeIcon = when (mode) {
                        CameraCaptureMode.SHARE_PHOTO -> Icons.Rounded.IosShare
                        CameraCaptureMode.PHOTO -> Icons.Rounded.PhotoCamera
                        CameraCaptureMode.AUTO_CAPTURE -> Icons.Rounded.MotionPhotosAuto
                        CameraCaptureMode.VIDEO -> Icons.Rounded.Videocam
                        CameraCaptureMode.REPORTS -> Icons.Rounded.Description
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(CircleShape)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (mode == CameraCaptureMode.REPORTS) {
                                    onOpenPdfReport()
                                } else {
                                    viewModel.setCaptureMode(mode)
                                }
                            }
                            .testTag("camera_mode_${mode.name.lowercase()}")
                    ) {
                        Surface(
                            color = pillBg,
                            shape = CircleShape
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = modeIcon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFF101419) else Color.White.copy(alpha = 0.78f),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = mode.label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (isSelected) Color(0xFF101419) else Color.White.copy(alpha = 0.85f),
                                    letterSpacing = 0.3.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Modernised 5-Item Bottom Action Row: Collection | Locations | Dual-Ring Shutter | File Name | Template
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 1. Collection (circular glass photo pod -> Gallery)
                val thumbInteraction = remember { MutableInteractionSource() }
                val thumbPressed by thumbInteraction.collectIsPressedAsState()
                val thumbScale by animateFloatAsState(
                    targetValue = if (thumbPressed) 0.90f else 1f,
                    animationSpec = spring(dampingRatio = 0.62f, stiffness = 480f),
                    label = "thumbPodScale"
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .width(64.dp)
                        .scale(thumbScale)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = thumbInteraction,
                            indication = null
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenGallery()
                        }
                        .padding(vertical = 3.dp)
                        .testTag("camera_gallery_thumb_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(
                                1.5.dp,
                                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.82f), TelemetryEmerald.copy(alpha = 0.7f))),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (latestPhoto != null) {
                            AsyncImage(
                                model = latestPhoto!!.mediaUri,
                                contentDescription = "Open latest capture collection",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Collections,
                                contentDescription = "Open photo collection",
                                tint = Color.White,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.camera_collection),
                        color = Color.White.copy(alpha = 0.90f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 2. Locations (circular glass pod, highlights emerald when pinned)
                ModernDeckPodButton(
                    icon = Icons.Rounded.LocationOn,
                    label = stringResource(R.string.camera_locations),
                    active = stampConfig.useManualLocation,
                    activeColor = TelemetryEmerald,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenLocations()
                    },
                    modifier = Modifier.testTag("camera_locations_button")
                )

                // 3. Signature Multi-Ring Pro Shutter / Auto Capture / Video Button
                val isVideoMode = hudState.captureMode == CameraCaptureMode.VIDEO
                val isAutoMode = hudState.captureMode == CameraCaptureMode.AUTO_CAPTURE
                val shutterScale by animateFloatAsState(
                    targetValue = if (hudState.isCapturing || hudState.isRecordingVideo || hudState.isAutoCapturing) 0.91f else 1f,
                    animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f),
                    label = "shutterScale"
                )
                val outerBrush = when {
                    !hasCameraPermission -> Brush.linearGradient(listOf(ObsidianBorder, ObsidianBorder))
                    isVideoMode -> Brush.sweepGradient(listOf(Color(0xFFFF453A), Color(0xFFFF9E80), Color(0xFFFF453A)))
                    isAutoMode -> Brush.sweepGradient(listOf(TelemetryEmerald, Color(0xFF69F0AE), ShutterGold, TelemetryEmerald))
                    else -> Brush.sweepGradient(listOf(ShutterGold, Color(0xFFFFE57F), TelemetryEmerald, ShutterGold))
                }
                val innerBrush = when {
                    !hasCameraPermission -> Brush.verticalGradient(listOf(ObsidianSurfaceHigh, ObsidianSurfaceHigh))
                    isVideoMode -> Brush.verticalGradient(listOf(Color(0xFFFF5252), Color(0xFFD50000)))
                    isAutoMode && hudState.isAutoCapturing -> Brush.verticalGradient(listOf(Color(0xFF00E676), TelemetryEmerald))
                    isAutoMode -> Brush.verticalGradient(listOf(TelemetryEmerald, Color(0xFF0A8F62)))
                    else -> Brush.verticalGradient(listOf(Color(0xFFFFD54F), ShutterGold))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .scale(shutterScale),
                        contentAlignment = Alignment.Center
                    ) {
                        // Animated Progress Sweep Ring for Auto Capture
                        if (hudState.isAutoCapturing && autoCaptureProgress > 0f) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawArc(
                                    color = TelemetryEmerald,
                                    startAngle = -90f,
                                    sweepAngle = autoCaptureProgress * 360f,
                                    useCenter = false,
                                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(3.dp, outerBrush, CircleShape)
                                .padding(6.dp)
                                .clip(
                                    if ((isVideoMode && hudState.isRecordingVideo) || hudState.isAutoCapturing) {
                                        RoundedCornerShape(18.dp)
                                    } else {
                                        CircleShape
                                    }
                                )
                                .background(innerBrush)
                                .combinedClickable(
                                    enabled = hasCameraPermission && !hudState.isCapturing,
                                    onClick = { triggerPrimaryShutter(burstLongPress = false) },
                                    onLongClick = {
                                        if (!isVideoMode) {
                                            triggerPrimaryShutter(burstLongPress = true)
                                        }
                                    }
                                )
                                .testTag("capture_shutter_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                hudState.isCapturing -> {
                                    CircularProgressIndicator(
                                        color = Color(0xFF141619),
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                (isVideoMode && hudState.isRecordingVideo) || hudState.isAutoCapturing -> {
                                    Icon(
                                        imageVector = Icons.Rounded.Stop,
                                        contentDescription = if (hudState.isAutoCapturing) "Stop Auto Capture" else "Stop recording",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                isAutoMode -> {
                                    Icon(
                                        imageVector = Icons.Rounded.MotionPhotosAuto,
                                        contentDescription = "Start Auto Capture",
                                        tint = Color(0xFF0A1410),
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                isBurstMode && !isVideoMode -> {
                                    Icon(
                                        imageVector = Icons.Rounded.BurstMode,
                                        contentDescription = "3-Shot Burst Shutter",
                                        tint = Color(0xFF141619),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                isVideoMode -> {
                                    Icon(
                                        imageVector = Icons.Rounded.Videocam,
                                        contentDescription = "Start Video Recording",
                                        tint = Color.White,
                                        modifier = Modifier.size(25.dp)
                                    )
                                }
                                else -> {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .border(1.8.dp, Color(0x45141619), CircleShape)
                                    )
                                }
                            }
                        }
                    }

                    if (isVideoMode && hudState.isRecordingVideo) {
                        ModernGlassCircleButton(
                            icon = if (isVideoPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = if (isVideoPaused) "Resume video recording" else "Pause video recording",
                            active = isVideoPaused,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (isVideoPaused) {
                                    activeRecording?.resume()
                                    isVideoPaused = false
                                } else {
                                    activeRecording?.pause()
                                    isVideoPaused = true
                                }
                            },
                            testTag = "video_pause_resume_button"
                        )
                    }
                }

                // 4. File Name (circular glass pod)
                ModernDeckPodButton(
                    icon = Icons.Rounded.DriveFileRenameOutline,
                    label = stringResource(R.string.camera_file_name),
                    active = stampConfig.customFileNameEnabled,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenFileName()
                    },
                    modifier = Modifier.testTag("camera_edit_note_btn")
                )

                // 5. Template (tap to open picker, long-press to quick-cycle layout)
                ModernDeckPodButton(
                    icon = Icons.Rounded.AutoAwesomeMosaic,
                    label = stringResource(R.string.camera_template),
                    active = false,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenTemplates()
                    },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.cycleStampTemplate()
                        viewModel.setStatusMessage("Switched Stamp Template")
                    },
                    modifier = Modifier.testTag("camera_open_templates_btn")
                )
            }

            AdBannerSlot(placementId = "camera")
        }
    }

    // Root Scaffolded Camera Layout
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isFullScreenCamera) {
            Box(modifier = Modifier.fillMaxSize()) {
                livePreviewContent(Modifier.fillMaxSize())
                viewfinderOverlays(true)
            }
            Box(modifier = Modifier.align(Alignment.TopCenter)) {
                topIconToolbar()
            }
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                bottomCameraDeck()
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                topIconToolbar()

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (!hasCameraPermission) {
                        PermissionOnboardingCard(
                            onRequestPermissions = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.CAMERA,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            onOpenSettings = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            },
                            onImportPhoto = {
                                photoImportLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                    } else {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            val containerRatio = if (maxHeight > 0.dp) maxWidth / maxHeight else 0.75f
                            val targetRatio = hudState.aspectRatio.ratioValue
                            val previewFrameModifier = if (containerRatio > targetRatio) {
                                Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(targetRatio, matchHeightConstraintsFirst = true)
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(targetRatio, matchHeightConstraintsFirst = false)
                            }
                            Box(modifier = previewFrameModifier) {
                                livePreviewContent(Modifier.fillMaxSize())
                            }
                            viewfinderOverlays(false)
                        }
                    }
                }

                bottomCameraDeck()
            }
        }

        floatingToolsOverlay()
    }

    // Quick Note & Brand Label Dialog
    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Stamp Note & Brand Label") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = brandDraft,
                        onValueChange = { brandDraft = it.take(24) },
                        label = { Text("Brand Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = { noteDraft = it.take(80) },
                        label = { Text("Stamp Note") },
                        placeholder = { Text("e.g., Site survey complete") },
                        singleLine = false,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateStampConfig {
                            it.copy(
                                customBrandLabel = brandDraft.trim().ifBlank { "YVGPSCAM" },
                                customNote = noteDraft.trim()
                            )
                        }
                        showNoteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ShutterGold,
                        contentColor = Color(0xFF141619)
                    )
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ZoomSelectorPill(
    zoomOptions: List<Float>,
    currentZoom: Float,
    onSelectZoom: (Float) -> Unit
) {
    ModernFloatingCapsule(
        containerColor = Color(0xA60E141D),
        borderColor = Color.White.copy(alpha = 0.16f),
        modifier = Modifier.testTag("camera_zoom_selector_pill")
    ) {
        zoomOptions.forEach { zoomVal ->
            val selected = abs(currentZoom - zoomVal) < 0.2f
            val label = when {
                zoomVal < 1f -> "0.5"
                zoomVal == 1f -> "1x"
                else -> "${zoomVal.toInt()}x"
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(CircleShape)
                    .clickable { onSelectZoom(zoomVal) }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (selected) ShutterGold else Color.Transparent)
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color(0xFF141619) else Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun CenterFocusBracketsGuide(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(112.dp)
            .testTag("camera_center_focus_brackets")
    ) {
        val len = 24.dp.toPx()
        val stroke = 2.6.dp.toPx()
        val color = FocusReticleGreen.copy(alpha = 0.9f)
        drawLine(color, Offset(0f, 0f), Offset(len, 0f), strokeWidth = stroke, cap = StrokeCap.Square)
        drawLine(color, Offset(0f, 0f), Offset(0f, len), strokeWidth = stroke, cap = StrokeCap.Square)
        drawLine(color, Offset(size.width, 0f), Offset(size.width - len, 0f), strokeWidth = stroke, cap = StrokeCap.Square)
        drawLine(color, Offset(size.width, 0f), Offset(size.width, len), strokeWidth = stroke, cap = StrokeCap.Square)
        drawLine(color, Offset(0f, size.height), Offset(len, size.height), strokeWidth = stroke, cap = StrokeCap.Square)
        drawLine(color, Offset(0f, size.height), Offset(0f, size.height - len), strokeWidth = stroke, cap = StrokeCap.Square)
        drawLine(color, Offset(size.width, size.height), Offset(size.width - len, size.height), strokeWidth = stroke, cap = StrokeCap.Square)
        drawLine(color, Offset(size.width, size.height), Offset(size.width, size.height - len), strokeWidth = stroke, cap = StrokeCap.Square)
    }
}

@Composable
private fun TapFocusReticleOverlay(
    reticle: FocusReticleState,
    modifier: Modifier = Modifier
) {
    val scaleAnim = remember(reticle.sequenceId) { Animatable(1.28f) }
    LaunchedEffect(reticle.sequenceId) {
        scaleAnim.animateTo(
            targetValue = 1.0f,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f)
        )
    }

    val reticleColor = when (reticle.isFocused) {
        true -> FocusReticleGreen
        false -> Color.White
        null -> ShutterGold
    }
    val statusLabel = when (reticle.isFocused) {
        true -> "AF LOCK"
        false -> "METERED"
        null -> "FOCUS"
    }

    BoxWithConstraints(modifier = modifier.testTag("camera_focus_reticle")) {
        val density = LocalDensity.current
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }
        val badgeW = with(density) { 64.dp.toPx() }
        val badgeH = with(density) { 20.dp.toPx() }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val scale = scaleAnim.value
            val halfBox = 44.dp.toPx() * scale
            val bracketLen = 18.dp.toPx() * scale
            val strokePx = 2.6.dp.toPx()
            val cx = reticle.point.x
            val cy = reticle.point.y

            val corners = listOf(
                Pair(Offset(cx - halfBox, cy - halfBox), Pair(Offset(1f, 0f), Offset(0f, 1f))),
                Pair(Offset(cx + halfBox, cy - halfBox), Pair(Offset(-1f, 0f), Offset(0f, 1f))),
                Pair(Offset(cx - halfBox, cy + halfBox), Pair(Offset(1f, 0f), Offset(0f, -1f))),
                Pair(Offset(cx + halfBox, cy + halfBox), Pair(Offset(-1f, 0f), Offset(0f, -1f)))
            )
            corners.forEach { (corner, dirs) ->
                val (dirX, dirY) = dirs
                drawLine(reticleColor, corner, Offset(corner.x + dirX.x * bracketLen, corner.y), strokeWidth = strokePx, cap = StrokeCap.Square)
                drawLine(reticleColor, corner, Offset(corner.x, corner.y + dirY.y * bracketLen), strokeWidth = strokePx, cap = StrokeCap.Square)
            }
        }

        val desiredX = reticle.point.x - badgeW / 2f
        val belowY = with(density) { reticle.point.y + 50.dp.toPx() }
        val aboveY = with(density) { reticle.point.y - 50.dp.toPx() - badgeH }
        val labelOffsetX = desiredX.coerceIn(8f, (maxW - badgeW - 8f).coerceAtLeast(8f)).roundToInt()
        val labelOffsetY = (if (belowY + badgeH + 8f <= maxH) belowY else aboveY)
            .coerceIn(8f, (maxH - badgeH - 8f).coerceAtLeast(8f))
            .roundToInt()

        Surface(
            color = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .offset { IntOffset(labelOffsetX, labelOffsetY) }
                .border(0.75.dp, reticleColor.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
        ) {
            Text(
                text = statusLabel,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = reticleColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun CompactGpsStatusPill(
    signalState: GpsSignalState,
    accuracyMeters: Float?,
    satellitesUsed: Int,
    satellitesVisible: Int,
    offlineOnly: Boolean,
    isDeviceLocationEnabled: Boolean,
    hasLocationPermission: Boolean,
    onTapAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val satSuffix = when {
        satellitesUsed > 0 -> " • ${satellitesUsed}sat"
        satellitesVisible > 0 -> " • $satellitesVisible vis"
        else -> ""
    }
    val (gpsIcon, gpsColor, gpsLabel) = when {
        !hasLocationPermission -> Triple(Icons.Rounded.GpsOff, MaterialTheme.colorScheme.error, "TAP GPS")
        !isDeviceLocationEnabled -> Triple(Icons.Rounded.GpsOff, ShutterGold, "GPS OFF · TAP")
        signalState == GpsSignalState.LOCKED -> Triple(Icons.Rounded.MyLocation, TelemetryEmerald, "±${accuracyMeters?.roundToInt() ?: 0}m$satSuffix")
        signalState == GpsSignalState.COARSE -> Triple(Icons.Rounded.LocationSearching, ShutterGold, "±${accuracyMeters?.roundToInt() ?: 0}m$satSuffix")
        else -> Triple(Icons.Rounded.LocationSearching, ShutterGold, if (satellitesVisible > 0) "SYNC $satellitesUsed/$satellitesVisible" else "GPS SYNC")
    }

    Surface(
        color = Color(0xAA0C1119),
        shape = CircleShape,
        modifier = modifier
            .border(1.dp, gpsColor.copy(alpha = 0.65f), CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onTapAction)
            .testTag("camera_gps_status_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(gpsIcon, contentDescription = "GPS status", tint = gpsColor, modifier = Modifier.size(13.dp))
            Text(
                text = gpsLabel,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = gpsColor
            )
            if (offlineOnly) {
                Icon(Icons.Rounded.OfflinePin, contentDescription = "Offline map mode", tint = ShutterGold, modifier = Modifier.size(11.dp))
            }
        }
    }
}

@Composable
private fun ViewfinderOrientationStatusPill(
    orientation: DeviceCaptureOrientation,
    modifier: Modifier = Modifier
) {
    val isLandscape = orientation.isLandscape
    val accent = if (isLandscape) ShutterGold else Color.White
    val borderColor = if (isLandscape) ShutterGold.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.24f)
    val animatedAngle by animateFloatAsState(
        targetValue = orientation.angleDegrees,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 360f),
        label = "viewfinderOrientationAngle"
    )

    Surface(
        color = Color(0xAA0C1119),
        shape = CircleShape,
        modifier = modifier
            .border(1.dp, borderColor, CircleShape)
            .testTag("viewfinder_orientation_status")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ScreenRotation,
                contentDescription = "Auto-rotate active",
                tint = accent.copy(alpha = 0.85f),
                modifier = Modifier.size(12.dp)
            )
            Icon(
                imageVector = Icons.Rounded.StayCurrentPortrait,
                contentDescription = if (isLandscape) "Phone held in landscape orientation" else "Phone held in portrait orientation",
                tint = accent,
                modifier = Modifier
                    .size(13.dp)
                    .graphicsLayer { rotationZ = animatedAngle }
            )
            Text(
                text = orientation.label,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}

@Composable
private fun PermissionOnboardingCard(
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onImportPhoto: () -> Unit
) {
    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_brand_yv_logo),
                contentDescription = "YVGPSCAM Brand Logo",
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
            Text(
                text = stringResource(R.string.permission_title_camera),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.permission_desc_camera),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRequestPermissions,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = ShutterGold, contentColor = Color(0xFF141619)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("grant_permissions_button")
            ) {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.grant_permissions), fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onImportPhoto,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stamp Photo")
                }
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Settings")
                }
            }
        }
    }
}
