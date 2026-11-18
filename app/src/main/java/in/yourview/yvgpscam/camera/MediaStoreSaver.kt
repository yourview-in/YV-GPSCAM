package `in`.yourview.yvgpscam.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import `in`.yourview.yvgpscam.data.model.CapturedPhotoEntity
import `in`.yourview.yvgpscam.data.model.FileNameField
import `in`.yourview.yvgpscam.data.model.GpsTelemetry
import `in`.yourview.yvgpscam.data.model.StampConfig
import `in`.yourview.yvgpscam.data.model.StampPosition
import `in`.yourview.yvgpscam.ui.viewmodel.CameraAspectRatio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt

class MediaStoreSaver(
    private val context: Context,
    private val stampRenderer: PhotoStampRenderer
) {
    suspend fun saveStampedJpegBytes(
        jpegBytes: ByteArray,
        telemetry: GpsTelemetry,
        config: StampConfig,
        miniMapBitmap: Bitmap?,
        mirrorHorizontal: Boolean = false,
        isFrontCamera: Boolean = false,
        deviceOrientationDegrees: Int = 0,
        targetAspectRatio: CameraAspectRatio = CameraAspectRatio.RATIO_4_3
    ): Result<CapturedPhotoEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val decoded = decodeOrientedBitmap(jpegBytes, mirrorHorizontal)
                ?: throw IllegalStateException("Failed to decode captured photo")
            val cropped = cropBitmapToAspectRatio(decoded, targetAspectRatio)
            if (cropped !== decoded) decoded.recycle()

            val now = System.currentTimeMillis()
            val fileName = buildCustomFileName(config, telemetry, now)

            if (config.autoSaveOriginalPhoto) {
                runCatching {
                    saveBitmapUnstampedOriginal(
                        bitmap = cropped,
                        fileName = "ORIG_$fileName",
                        jpegQuality = config.photoQuality.jpegQuality
                    )
                }
            }

            val stamped = stampRenderer.stampBitmap(
                source = cropped,
                telemetry = telemetry,
                config = config,
                miniMapBitmap = miniMapBitmap,
                capturedAtMillis = now,
                isFrontCamera = isFrontCamera,
                deviceOrientationDegrees = deviceOrientationDegrees
            )
            if (stamped !== cropped) cropped.recycle()
            persistStampedBitmap(stamped, telemetry, config, fileName, now)
        }
    }

    suspend fun stampAndSaveFromUri(
        sourceUri: Uri,
        telemetry: GpsTelemetry,
        config: StampConfig,
        miniMapBitmap: Bitmap?
    ): Result<CapturedPhotoEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Unable to read selected image")
            val decoded = decodeOrientedBitmap(bytes, mirrorHorizontal = false)
                ?: throw IllegalStateException("Unable to decode selected image")
            val now = System.currentTimeMillis()
            val fileName = buildCustomFileName(config, telemetry, now)
            val stamped = stampRenderer.stampBitmap(
                source = decoded,
                telemetry = telemetry,
                config = config,
                miniMapBitmap = miniMapBitmap,
                capturedAtMillis = now
            )
            if (stamped !== decoded) decoded.recycle()
            persistStampedBitmap(stamped, telemetry, config, fileName, now)
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun saveStampedVideoFile(
        rawVideoFile: File,
        telemetry: GpsTelemetry,
        config: StampConfig,
        miniMapBitmap: Bitmap?,
        isFrontCamera: Boolean = false
    ): Result<CapturedPhotoEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            val videoName = buildCustomFileName(config, telemetry, now)
                .removeSuffix(".jpg") + ".mp4"
            val (displayW, displayH) = resolveVideoDisplayDimensions(rawVideoFile)
            val stampedTempFile = File(context.cacheDir, "stamped_${rawVideoFile.nameWithoutExtension}.mp4")
            if (stampedTempFile.exists()) stampedTempFile.delete()

            val cardBitmap = stampRenderer.renderStampCardOnly(
                frameWidth = displayW,
                frameHeight = displayH,
                telemetry = telemetry,
                config = config,
                miniMapBitmap = miniMapBitmap,
                capturedAtMillis = now,
                isFrontCamera = isFrontCamera
            )

            val stampedOk = try {
                burnStampCardOntoVideo(
                    rawVideoFile = rawVideoFile,
                    outputVideoFile = stampedTempFile,
                    cardBitmap = cardBitmap,
                    position = config.stampPosition
                )
            } finally {
                if (!cardBitmap.isRecycled) cardBitmap.recycle()
            }

            val sourceToPersist = if (stampedOk && stampedTempFile.exists() && stampedTempFile.length() > 0L) {
                stampedTempFile
            } else {
                rawVideoFile
            }

            try {
                val persistedUri = persistMp4File(sourceToPersist, videoName)
                buildRecordedVideoEntity(
                    videoUri = persistedUri,
                    telemetry = telemetry,
                    config = config,
                    width = displayW,
                    height = displayH,
                    capturedAtMillis = now,
                    displayNameOverride = videoName
                )
            } finally {
                runCatching { rawVideoFile.delete() }
                runCatching { stampedTempFile.delete() }
            }
        }
    }

    private fun resolveVideoDisplayDimensions(videoFile: File): Pair<Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                h to w
            } else {
                w to h
            }
        } catch (_: Throwable) {
            1080 to 1920
        } finally {
            runCatching { retriever.release() }
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun burnStampCardOntoVideo(
        rawVideoFile: File,
        outputVideoFile: File,
        cardBitmap: Bitmap,
        position: StampPosition
    ): Boolean {
        val (anchorX, anchorY) = when (position) {
            StampPosition.TOP_LEFT -> -1f to 1f
            StampPosition.TOP_CENTER -> 0f to 1f
            StampPosition.TOP_RIGHT -> 1f to 1f
            StampPosition.BOTTOM_LEFT -> -1f to -1f
            StampPosition.BOTTOM_CENTER -> 0f to -1f
            StampPosition.BOTTOM_RIGHT -> 1f to -1f
        }
        val (bgX, bgY) = when (position) {
            StampPosition.TOP_LEFT -> -0.94f to 0.94f
            StampPosition.TOP_CENTER -> 0f to 0.94f
            StampPosition.TOP_RIGHT -> 0.94f to 0.94f
            StampPosition.BOTTOM_LEFT -> -0.94f to -0.94f
            StampPosition.BOTTOM_CENTER -> 0f to -0.94f
            StampPosition.BOTTOM_RIGHT -> 0.94f to -0.94f
        }
        val settings = StaticOverlaySettings.Builder()
            .setOverlayFrameAnchor(anchorX, anchorY)
            .setBackgroundFrameAnchor(bgX, bgY)
            .build()
        val overlay = BitmapOverlay.createStaticBitmapOverlay(cardBitmap, settings)
        val overlayEffect = OverlayEffect(listOf(overlay))
        val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(rawVideoFile)))
            .setEffects(Effects(emptyList(), listOf(overlayEffect)))
            .build()

        return withTimeoutOrNull(45_000L) {
            suspendCancellableCoroutine { cont ->
                val mainHandler = Handler(Looper.getMainLooper())
                mainHandler.post {
                    if (!cont.isActive) return@post
                    runCatching {
                        val transformer = Transformer.Builder(context)
                            .addListener(object : Transformer.Listener {
                                override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                                    if (cont.isActive) cont.resume(true)
                                }

                                override fun onError(
                                    composition: androidx.media3.transformer.Composition,
                                    exportResult: ExportResult,
                                    exportException: ExportException
                                ) {
                                    if (cont.isActive) cont.resume(false)
                                }
                            })
                            .build()

                        cont.invokeOnCancellation {
                            mainHandler.post { runCatching { transformer.cancel() } }
                        }
                        transformer.start(editedMediaItem, outputVideoFile.absolutePath)
                    }.onFailure {
                        if (cont.isActive) cont.resume(false)
                    }
                }
            }
        } ?: false
    }

    private fun persistMp4File(sourceFile: File, fileName: String): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/YVGPSCAM")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val insertedUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore failed to allocate video URI")
            resolver.openOutputStream(insertedUri)?.use { out ->
                FileInputStream(sourceFile).use { input -> input.copyTo(out) }
            } ?: throw IllegalStateException("Failed to open output stream for video")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(insertedUri, values, null, null)
            insertedUri
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "YVGPSCAM").apply {
                mkdirs()
            }
            val outFile = File(dir, fileName)
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(outFile).use { fos -> input.copyTo(fos) }
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        }
    }

    fun buildRecordedVideoEntity(
        videoUri: Uri,
        telemetry: GpsTelemetry,
        config: StampConfig,
        width: Int,
        height: Int,
        capturedAtMillis: Long = System.currentTimeMillis(),
        displayNameOverride: String? = null
    ): CapturedPhotoEntity {
        val videoName = displayNameOverride ?: (buildCustomFileName(config, telemetry, capturedAtMillis)
            .removeSuffix(".jpg") + ".mp4")
        return CapturedPhotoEntity(
            mediaUri = videoUri.toString(),
            displayName = videoName,
            capturedAtMillis = capturedAtMillis,
            latitude = telemetry.latitude,
            longitude = telemetry.longitude,
            altitudeMeters = telemetry.altitudeMeters,
            accuracyMeters = telemetry.accuracyMeters,
            compassAzimuth = telemetry.compassAzimuth,
            compassCardinal = telemetry.compassCardinal,
            addressLine = telemetry.addressLine,
            cityState = telemetry.cityState,
            country = telemetry.country,
            customNote = config.customNote,
            templateName = config.layoutTemplate.displayName,
            width = width,
            height = height,
            mediaType = "VIDEO"
        )
    }

    private fun decodeOrientedBitmap(bytes: ByteArray, mirrorHorizontal: Boolean): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inMutable = true
        }
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val exif = runCatching { ExifInterface(ByteArrayInputStream(bytes)) }.getOrNull()
        val orientation = exif?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        ) ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }
        if (mirrorHorizontal) {
            matrix.postScale(-1f, 1f)
        }

        val maxDim = 3200f
        val largest = maxOf(raw.width, raw.height).toFloat()
        if (largest > maxDim) {
            val scale = maxDim / largest
            matrix.postScale(scale, scale)
        }

        return if (!matrix.isIdentity) {
            val transformed = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            if (transformed !== raw) raw.recycle()
            transformed
        } else {
            raw
        }
    }

    private fun saveBitmapUnstampedOriginal(
        bitmap: Bitmap,
        fileName: String,
        jpegQuality: Int
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/YVGPSCAM/Originals")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val insertedUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
            resolver.openOutputStream(insertedUri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(insertedUri, values, null, null)
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "YVGPSCAM/Originals").apply {
                mkdirs()
            }
            val outFile = File(dir, fileName)
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, fos)
            }
        }
    }

    private fun persistStampedBitmap(
        stamped: Bitmap,
        telemetry: GpsTelemetry,
        config: StampConfig,
        fileName: String,
        now: Long
    ): CapturedPhotoEntity {
        val width = stamped.width
        val height = stamped.height
        val quality = config.photoQuality.jpegQuality.coerceIn(70, 98)

        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/YVGPSCAM")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val insertedUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore failed to allocate photo URI")

            resolver.openOutputStream(insertedUri)?.use { out ->
                stamped.compress(Bitmap.CompressFormat.JPEG, quality, out)
            } ?: throw IllegalStateException("Failed to open output stream for photo")

            writeExifToUri(insertedUri, telemetry, config, now)

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(insertedUri, values, null, null)
            insertedUri
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "YVGPSCAM").apply {
                mkdirs()
            }
            val outFile = File(dir, fileName)
            FileOutputStream(outFile).use { fos ->
                stamped.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            }
            writeExifToFile(outFile, telemetry, config, now)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        }

        stamped.recycle()

        return CapturedPhotoEntity(
            mediaUri = uri.toString(),
            displayName = fileName,
            capturedAtMillis = now,
            latitude = telemetry.latitude,
            longitude = telemetry.longitude,
            altitudeMeters = telemetry.altitudeMeters,
            accuracyMeters = telemetry.accuracyMeters,
            compassAzimuth = telemetry.compassAzimuth,
            compassCardinal = telemetry.compassCardinal,
            addressLine = telemetry.addressLine,
            cityState = telemetry.cityState,
            country = telemetry.country,
            customNote = config.customNote,
            templateName = config.layoutTemplate.displayName,
            width = width,
            height = height,
            mediaType = "PHOTO"
        )
    }

    private fun writeExifToUri(uri: Uri, telemetry: GpsTelemetry, config: StampConfig, timestamp: Long) {
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                applyExifTags(exif, telemetry, config, timestamp)
                exif.saveAttributes()
            }
        }
    }

    private fun writeExifToFile(file: File, telemetry: GpsTelemetry, config: StampConfig, timestamp: Long) {
        runCatching {
            val exif = ExifInterface(file)
            applyExifTags(exif, telemetry, config, timestamp)
            exif.saveAttributes()
        }
    }

    private fun applyExifTags(
        exif: ExifInterface,
        telemetry: GpsTelemetry,
        config: StampConfig,
        timestamp: Long
    ) {
        val exifDate = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(timestamp))
        exif.setAttribute(ExifInterface.TAG_DATETIME, exifDate)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exifDate)
        exif.setAttribute(ExifInterface.TAG_MAKE, "YVGPSCAM")
        exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL ?: "Android GPS Camera")
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "YVGPSCAM 1.0")

        val descParts = listOfNotNull(
            telemetry.displayHeadlineAddress.takeIf { it.isNotBlank() },
            config.customNote.takeIf { it.isNotBlank() }
        )
        if (descParts.isNotEmpty()) {
            exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, descParts.joinToString(" | "))
        }

        val lat = telemetry.latitude
        val lon = telemetry.longitude
        if (lat != null && lon != null) {
            exif.setLatLong(lat, lon)
            exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, "GPS")
            telemetry.altitudeMeters?.let { alt ->
                exif.setAltitude(abs(alt))
                exif.setAttribute(
                    ExifInterface.TAG_GPS_ALTITUDE_REF,
                    if (alt >= 0) "0" else "1"
                )
            }
            exif.setAttribute(
                ExifInterface.TAG_GPS_IMG_DIRECTION,
                "${telemetry.compassAzimuth.toInt()}/1"
            )
            exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, "M")
        }
    }

    companion object {
        fun cropBitmapToAspectRatio(
            source: Bitmap,
            targetAspectRatio: CameraAspectRatio
        ): Bitmap {
            val w = source.width
            val h = source.height
            if (w <= 0 || h <= 0) return source
            val isPortrait = h >= w
            val desiredRatio = when (targetAspectRatio) {
                CameraAspectRatio.RATIO_1_1 -> 1f
                CameraAspectRatio.RATIO_4_3 -> if (isPortrait) 3f / 4f else 4f / 3f
                CameraAspectRatio.RATIO_16_9 -> if (isPortrait) 9f / 16f else 16f / 9f
            }
            val currentRatio = w.toFloat() / h.toFloat()
            if (abs(currentRatio - desiredRatio) <= 0.015f) return source

            val cropW: Int
            val cropH: Int
            if (currentRatio > desiredRatio) {
                cropH = h
                cropW = (h * desiredRatio).roundToInt().coerceIn(1, w)
            } else {
                cropW = w
                cropH = (w / desiredRatio).roundToInt().coerceIn(1, h)
            }
            val x = ((w - cropW) / 2).coerceAtLeast(0)
            val y = ((h - cropH) / 2).coerceAtLeast(0)
            return Bitmap.createBitmap(source, x, y, cropW, cropH)
        }

        fun buildCustomFileName(
            config: StampConfig,
            telemetry: GpsTelemetry,
            timestampMillis: Long = System.currentTimeMillis()
        ): String {
            val fallbackStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestampMillis))
            if (!config.customFileNameEnabled) {
                return "YVGPSCAM_$fallbackStamp.jpg"
            }

            val parts = mutableListOf<String>()
            config.parsedFnFieldOrder().forEach { field ->
                when (field) {
                    FileNameField.DATE_TIME -> if (config.fnIncludeDateTime) {
                        parts.add(SimpleDateFormat("yyyyMMdd_hhmmssa", Locale.US).format(Date(timestampMillis)))
                    }
                    FileNameField.SEQUENCE -> if (config.fnIncludeSequence) {
                        parts.add(config.photoSequenceNumber.coerceAtLeast(1).toString())
                    }
                    FileNameField.CUSTOM_1 -> if (config.fnIncludeCustom1 && config.fnCustomText1.isNotBlank()) {
                        parts.add(config.fnCustomText1.trim())
                    }
                    FileNameField.CUSTOM_2 -> if (config.fnIncludeCustom2 && config.fnCustomText2.isNotBlank()) {
                        parts.add(config.fnCustomText2.trim())
                    }
                    FileNameField.CUSTOM_3 -> if (config.fnIncludeCustom3 && config.fnCustomText3.isNotBlank()) {
                        parts.add(config.fnCustomText3.trim())
                    }
                    FileNameField.FULL_ADDRESS -> if (config.fnIncludeFullAddress) {
                        val addr = listOf(telemetry.cityState, telemetry.country, telemetry.addressLine)
                            .filter { it.isNotBlank() }
                            .joinToString("_")
                        if (addr.isNotBlank()) parts.add(addr)
                    }
                    FileNameField.LAT_LON -> if (config.fnIncludeLatLon && telemetry.latitude != null && telemetry.longitude != null) {
                        parts.add(String.format(Locale.US, "%.6f_%.6f", telemetry.latitude, telemetry.longitude))
                    }
                    FileNameField.PLUS_CODE -> if (config.fnIncludePlusCode) {
                        telemetry.plusCode?.let { parts.add(it) }
                    }
                    FileNameField.TIME_ZONE -> if (config.fnIncludeTimeZone) {
                        parts.add(telemetry.formatTimeZoneOffset(timestampMillis).replace(" ", ""))
                    }
                    FileNameField.NOTE -> if (config.fnIncludeNote && config.customNote.isNotBlank()) {
                        parts.add(config.customNote.trim().take(28))
                    }
                }
            }

            val raw = parts.joinToString("_")
                .replace(Regex("[^A-Za-z0-9._-]+"), "_")
                .replace(Regex("_+"), "_")
                .trim('_', '.')
                .take(120)
                .trimEnd('_', '.')

            return if (raw.isBlank()) "YVGPSCAM_$fallbackStamp.jpg" else "$raw.jpg"
        }
    }
}
