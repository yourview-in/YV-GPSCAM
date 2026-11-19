package `in`.yourview.yvgpscam

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import `in`.yourview.yvgpscam.camera.PhotoStampRenderer
import `in`.yourview.yvgpscam.data.local.AppDatabase
import `in`.yourview.yvgpscam.data.model.CoordinateFormat
import `in`.yourview.yvgpscam.data.model.GpsSignalState
import `in`.yourview.yvgpscam.data.model.GpsTelemetry
import `in`.yourview.yvgpscam.data.model.MapStyle
import `in`.yourview.yvgpscam.data.model.StampAccentColor
import `in`.yourview.yvgpscam.data.model.StampConfig
import `in`.yourview.yvgpscam.data.model.StampFont
import `in`.yourview.yvgpscam.data.model.StampPosition
import `in`.yourview.yvgpscam.data.model.StampSize
import `in`.yourview.yvgpscam.data.model.StampTemplate
import `in`.yourview.yvgpscam.map.MiniMapTileRenderer
import `in`.yourview.yvgpscam.map.OfflineMapRepository
import `in`.yourview.yvgpscam.map.toGoogleMapType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [ShadowPdfDocument::class])
class ExampleRobolectricTest {

    @Test
    fun `app name is GPS Camera while package name stays yvgpscam`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("GPS Camera", appName)
        assertEquals("in.yourview.yvgpscam", context.packageName)
    }

    @Test
    fun `telemetry formats decimal and dms coordinates accurately`() {
        val telemetry = GpsTelemetry(
            latitude = 37.422094,
            longitude = -122.084057,
            signalState = GpsSignalState.LOCKED
        )
        val dd = telemetry.formatCoordinates(CoordinateFormat.DECIMAL_DEGREES)
        val dms = telemetry.formatCoordinates(CoordinateFormat.DMS)
        assertTrue(dd.contains("37.422094° N"))
        assertTrue(dd.contains("122.084057° W"))
        assertTrue(dms.contains("37°25'"))
        assertEquals("NE", GpsTelemetry.azimuthToCardinal(45f))
    }

    @Test
    fun `offline map tile estimator and vector fallback work offline`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(context)
        val offlineRepo = OfflineMapRepository(context, db.offlineRegionDao())

        val tiles = offlineRepo.estimateTilesForRegion(
            centerLat = 37.422094,
            centerLon = -122.084057,
            radiusKm = 1.0,
            zoomMin = 14,
            zoomMax = 15
        )
        assertTrue(tiles.isNotEmpty())

        // Write a dummy cached tile and verify disk detection
        offlineRepo.saveTileBytes(15, 5240, 12664, byteArrayOf(1, 2, 3, 4))
        assertTrue(offlineRepo.hasCachedTile(15, 5240, 12664))

        val miniMapRenderer = MiniMapTileRenderer(offlineRepo)
        for (style in listOf(MapStyle.STANDARD, MapStyle.SATELLITE, MapStyle.TERRAIN, MapStyle.HYBRID)) {
            val mapBmp = miniMapRenderer.renderMiniMapBitmap(
                latitude = 37.422094,
                longitude = -122.084057,
                mapStyle = style,
                offlineOnly = true
            )
            assertNotNull(mapBmp)
            assertEquals(260, mapBmp.width)
            mapBmp.recycle()
        }
    }

    @Test
    fun `photo stamp renderer stamps logo and telemetry across positions and styles`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val renderer = PhotoStampRenderer(context)
        val source = Bitmap.createBitmap(1080, 1440, Bitmap.Config.ARGB_8888)
        val miniMap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val telemetry = GpsTelemetry(
            latitude = 37.422094,
            longitude = -122.084057,
            altitudeMeters = 18.0,
            accuracyMeters = 3.5f,
            compassAzimuth = 120f,
            compassCardinal = "SE",
            addressLine = "1600 Amphitheatre Pkwy",
            cityState = "Mountain View, CA",
            country = "USA",
            signalState = GpsSignalState.LOCKED
        )

        val config = StampConfig(
            template = StampTemplate.TACTICAL_DARK,
            stampFont = StampFont.SPACE_GROTESK,
            accentColor = StampAccentColor.YV_GOLD,
            stampSize = StampSize.LARGE,
            stampPosition = StampPosition.TOP_RIGHT,
            customNote = "Field Check"
        )

        val stamped = renderer.stampBitmap(source, telemetry, config, miniMap)
        assertEquals(1080, stamped.width)
        assertEquals(1440, stamped.height)
        stamped.recycle()
        source.recycle()
        miniMap.recycle()
    }

    @Test
    fun `brand logo loads by default and gallery viewMode and dateFilter update`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logoBmp = `in`.yourview.yvgpscam.camera.LogoBitmapProvider.loadStampLogoBitmap(context, 128)
        assertEquals(128, logoBmp.width)
        logoBmp.recycle()

        val db = AppDatabase.getInstance(context)
        val photoRepo = `in`.yourview.yvgpscam.data.local.PhotoRepository(context, db.capturedPhotoDao())
        val offlineRepo = OfflineMapRepository(context, db.offlineRegionDao())
        val galleryVm = `in`.yourview.yvgpscam.ui.viewmodel.GalleryViewModel(
            context,
            photoRepo,
            MiniMapTileRenderer(offlineRepo),
            `in`.yourview.yvgpscam.data.local.StampPreferencesRepository(context)
        )
        assertEquals(`in`.yourview.yvgpscam.ui.viewmodel.GalleryViewMode.GRID, galleryVm.viewMode.value)
        galleryVm.toggleViewMode()
        assertEquals(`in`.yourview.yvgpscam.ui.viewmodel.GalleryViewMode.LIST, galleryVm.viewMode.value)

        galleryVm.applyTodayFilter()
        assertEquals(`in`.yourview.yvgpscam.ui.viewmodel.DateFilterPreset.TODAY, galleryVm.dateFilter.value.preset)
        assertNotNull(galleryVm.dateFilter.value.startMillis)
        galleryVm.clearDateFilter()
        assertEquals(`in`.yourview.yvgpscam.ui.viewmodel.DateFilterPreset.ALL, galleryVm.dateFilter.value.preset)
    }

    @Test
    fun `stamp and logo opacity configuration renders and gallery multi-select updates state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val renderer = PhotoStampRenderer(context)
        val source = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888)
        val miniMap = Bitmap.createBitmap(180, 180, Bitmap.Config.ARGB_8888)
        val telemetry = GpsTelemetry(
            latitude = 37.422094,
            longitude = -122.084057,
            altitudeMeters = 12.0,
            accuracyMeters = 4.0f,
            compassAzimuth = 90f,
            compassCardinal = "E",
            addressLine = "1600 Amphitheatre Pkwy",
            cityState = "Mountain View, CA",
            country = "USA",
            signalState = GpsSignalState.LOCKED
        )

        val translucentConfig = StampConfig(
            stampOpacity = 0.45f,
            logoOpacity = 0.50f
        )
        assertEquals(0.45f, translucentConfig.stampOpacity, 0.001f)
        assertEquals(0.50f, translucentConfig.logoOpacity, 0.001f)

        val stamped = renderer.stampBitmap(source, telemetry, translucentConfig, miniMap)
        assertEquals(800, stamped.width)
        assertEquals(1000, stamped.height)
        stamped.recycle()
        source.recycle()
        miniMap.recycle()

        val db = AppDatabase.getInstance(context)
        val photoRepo = `in`.yourview.yvgpscam.data.local.PhotoRepository(context, db.capturedPhotoDao())
        val offlineRepo = OfflineMapRepository(context, db.offlineRegionDao())
        val galleryVm = `in`.yourview.yvgpscam.ui.viewmodel.GalleryViewModel(
            context,
            photoRepo,
            MiniMapTileRenderer(offlineRepo),
            `in`.yourview.yvgpscam.data.local.StampPreferencesRepository(context)
        )

        assertFalse(galleryVm.isSelectionMode.value)
        assertTrue(galleryVm.selectedPhotoIds.value.isEmpty())

        galleryVm.enterSelectionMode(101L)
        assertTrue(galleryVm.isSelectionMode.value)
        assertEquals(setOf(101L), galleryVm.selectedPhotoIds.value)

        galleryVm.togglePhotoSelection(202L)
        assertEquals(setOf(101L, 202L), galleryVm.selectedPhotoIds.value)

        galleryVm.togglePhotoSelection(101L)
        assertEquals(setOf(202L), galleryVm.selectedPhotoIds.value)

        galleryVm.clearSelection()
        assertFalse(galleryVm.isSelectionMode.value)
        assertTrue(galleryVm.selectedPhotoIds.value.isEmpty())
    }

    @Test
    fun `batch rename template formats date and coordinate placeholders accurately`() {
        val samplePhoto = `in`.yourview.yvgpscam.data.model.CapturedPhotoEntity(
            id = 7L,
            mediaUri = "content://media/external/images/media/7",
            displayName = "IMG_001.jpg",
            capturedAtMillis = 1763141040000L, // Fixed deterministic UTC timestamp
            latitude = 37.422094,
            longitude = -122.084057,
            altitudeMeters = 18.0,
            accuracyMeters = 3.5f,
            compassAzimuth = 90f,
            compassCardinal = "E",
            addressLine = "1600 Amphitheatre Pkwy",
            cityState = "Mountain View, CA",
            country = "USA",
            customNote = "",
            templateName = "TACTICAL_DARK",
            width = 1080,
            height = 1440
        )

        val formatted = `in`.yourview.yvgpscam.data.local.PhotoRepository.formatTemplateFilename(
            photo = samplePhoto,
            template = "Site_{date}_{lat}_{lon}_{city}_{index}",
            indexOneBased = 3
        )
        assertTrue(formatted.startsWith("YVGPSCAM_Site_"))
        assertTrue(formatted.contains("37.4221N"))
        assertTrue(formatted.contains("122.0841W"))
        assertTrue(formatted.contains("Mountain_View_CA"))
        assertTrue(formatted.endsWith("_03.jpg"))
    }

    @Test
    fun `google drive backup repository builds compact metadata and parses drive file list`() {
        val photo = `in`.yourview.yvgpscam.data.model.CapturedPhotoEntity(
            id = 12L,
            mediaUri = "content://media/external/images/media/12",
            displayName = "Site_Survey_12.jpg",
            capturedAtMillis = 1763141040000L,
            latitude = 37.422094,
            longitude = -122.084057,
            altitudeMeters = 24.5,
            accuracyMeters = 2.8f,
            compassAzimuth = 135f,
            compassCardinal = "SE",
            addressLine = "1600 Amphitheatre Pkwy",
            cityState = "Mountain View, CA",
            country = "USA",
            customNote = "Tower inspection",
            templateName = "TACTICAL_DARK",
            width = 1080,
            height = 1440
        )

        val backupId = `in`.yourview.yvgpscam.data.cloud.GoogleDriveBackupRepository
            .buildDeterministicBackupId(photo.capturedAtMillis, photo.displayName)
        val metaJson = `in`.yourview.yvgpscam.data.cloud.GoogleDriveBackupRepository
            .buildUploadMetadataJson("folder_abc", photo.displayName, backupId, photo)

        assertEquals(photo.displayName, metaJson.getString("name"))
        assertEquals("folder_abc", metaJson.getJSONArray("parents").getString(0))
        val props = metaJson.getJSONObject("appProperties")
        assertEquals(backupId, props.getString("bid"))
        assertEquals("Mountain View, CA", props.getString("city"))

        val fakeResponse = org.json.JSONObject().apply {
            put(
                "files",
                org.json.JSONArray().put(
                    org.json.JSONObject().apply {
                        put("id", "drive_file_99")
                        put("name", photo.displayName)
                        put("size", "482910")
                        put("appProperties", props)
                    }
                )
            )
        }.toString()

        val parsed = `in`.yourview.yvgpscam.data.cloud.GoogleDriveBackupRepository.parseBackupsResponse(fakeResponse)
        assertEquals(1, parsed.size)
        val item = parsed.first()
        assertEquals("drive_file_99", item.driveFileId)
        assertEquals(backupId, item.backupId)
        assertEquals(37.422094, item.latitude!!, 0.0001)
        assertEquals(-122.084057, item.longitude!!, 0.0001)
        assertEquals("Mountain View, CA", item.cityState)
        assertEquals("Tower inspection", item.customNote)
    }

    @Test
    fun `camera viewmodel toggles flash mode and front back lens cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(context)
        val offlineRepo = OfflineMapRepository(context, db.offlineRegionDao())
        val stampPrefs = `in`.yourview.yvgpscam.data.local.StampPreferencesRepository(context)
        val photoRepo = `in`.yourview.yvgpscam.data.local.PhotoRepository(context, db.capturedPhotoDao())
        val vm = `in`.yourview.yvgpscam.ui.viewmodel.CameraViewModel(
            locationManager = `in`.yourview.yvgpscam.sensors.LocationTelemetryManager(context),
            stampPrefs = stampPrefs,
            photoRepository = photoRepo,
            miniMapRenderer = MiniMapTileRenderer(offlineRepo),
            mediaStoreSaver = `in`.yourview.yvgpscam.camera.MediaStoreSaver(context, PhotoStampRenderer(context))
        )

        assertEquals(androidx.camera.core.ImageCapture.FLASH_MODE_OFF, vm.hudState.value.flashMode)
        vm.cycleFlashMode()
        assertEquals(androidx.camera.core.ImageCapture.FLASH_MODE_ON, vm.hudState.value.flashMode)
        vm.cycleFlashMode()
        assertEquals(androidx.camera.core.ImageCapture.FLASH_MODE_AUTO, vm.hudState.value.flashMode)
        vm.cycleFlashMode()
        assertEquals(androidx.camera.core.ImageCapture.FLASH_MODE_OFF, vm.hudState.value.flashMode)

        assertEquals(androidx.camera.core.CameraSelector.LENS_FACING_BACK, vm.hudState.value.lensFacing)
        vm.toggleLensFacing()
        assertEquals(androidx.camera.core.CameraSelector.LENS_FACING_FRONT, vm.hudState.value.lensFacing)
        vm.toggleLensFacing()
        assertEquals(androidx.camera.core.CameraSelector.LENS_FACING_BACK, vm.hudState.value.lensFacing)

        assertEquals(`in`.yourview.yvgpscam.ui.viewmodel.CameraAspectRatio.FULL, vm.hudState.value.aspectRatio)
        assertTrue(vm.hudState.value.isFullScreenCamera)
        vm.toggleFullScreenCamera()
        assertFalse(vm.hudState.value.isFullScreenCamera)
        vm.setFullScreenCamera(true)
        assertTrue(vm.hudState.value.isFullScreenCamera)
    }

    @Test
    fun `google drive account store persists login and auto sync skip sets`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = `in`.yourview.yvgpscam.data.cloud.GoogleDriveAccountStore(context)
        store.clearAccount()
        store.saveAccount("field.user@gmail.com", "Field User")
        store.markSkippedForAutoBackup("b_100_photo")
        store.markSkippedForAutoRestore("b_200_photo")

        val profile = store.accountFlow.first()
        assertTrue(profile.isSignedIn)
        assertEquals("field.user@gmail.com", profile.email)
        assertTrue(store.skipAutoBackupIdsFlow.first().contains("b_100_photo"))
        assertTrue(store.skipAutoRestoreIdsFlow.first().contains("b_200_photo"))

        store.unmarkSkippedForAutoBackup("b_100_photo")
        assertFalse(store.skipAutoBackupIdsFlow.first().contains("b_100_photo"))

        store.clearAccount()
        assertFalse(store.accountFlow.first().isSignedIn)
    }

    @Test
    fun `drive folder uses YVGPSCam and google maps helpers map styles and api keys safely`() {
        assertEquals(
            "YVGPSCam",
            `in`.yourview.yvgpscam.data.cloud.GoogleDriveBackupRepository.BACKUP_FOLDER_NAME
        )

        assertEquals(
            com.google.maps.android.compose.MapType.NORMAL,
            MapStyle.STANDARD.toGoogleMapType()
        )
        assertEquals(
            com.google.maps.android.compose.MapType.SATELLITE,
            MapStyle.SATELLITE.toGoogleMapType()
        )
        assertEquals(
            com.google.maps.android.compose.MapType.TERRAIN,
            MapStyle.TERRAIN.toGoogleMapType()
        )
        assertEquals(
            com.google.maps.android.compose.MapType.HYBRID,
            MapStyle.HYBRID.toGoogleMapType()
        )

        assertFalse(`in`.yourview.yvgpscam.map.GoogleMapsConfig.isApiKeyConfigured(""))
        assertFalse(`in`.yourview.yvgpscam.map.GoogleMapsConfig.isApiKeyConfigured("MY_MAPS_API_KEY"))
        assertFalse(`in`.yourview.yvgpscam.map.GoogleMapsConfig.isApiKeyConfigured("null"))
        assertTrue(`in`.yourview.yvgpscam.map.GoogleMapsConfig.isApiKeyConfigured("AIzaSyRealDemoKeyValue"))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(context)
        val offlineRepo = OfflineMapRepository(context, db.offlineRegionDao())
        val galleryVm = `in`.yourview.yvgpscam.ui.viewmodel.GalleryViewModel(
            context,
            `in`.yourview.yvgpscam.data.local.PhotoRepository(context, db.capturedPhotoDao()),
            MiniMapTileRenderer(offlineRepo),
            `in`.yourview.yvgpscam.data.local.StampPreferencesRepository(context)
        )
        galleryVm.setViewMode(`in`.yourview.yvgpscam.ui.viewmodel.GalleryViewMode.MAP)
        assertEquals(`in`.yourview.yvgpscam.ui.viewmodel.GalleryViewMode.MAP, galleryVm.viewMode.value)
        galleryVm.toggleViewMode()
        assertEquals(`in`.yourview.yvgpscam.ui.viewmodel.GalleryViewMode.GRID, galleryVm.viewMode.value)
    }

    @Test
    fun `device capture orientation resolver detects portrait landscape and applies hysteresis`() {
        var current = `in`.yourview.yvgpscam.ui.screens.DeviceCaptureOrientation.PORTRAIT
        current = `in`.yourview.yvgpscam.ui.screens.resolveDeviceCaptureOrientation(5, current)
        assertEquals(`in`.yourview.yvgpscam.ui.screens.DeviceCaptureOrientation.PORTRAIT, current)
        assertFalse(current.isLandscape)

        // Transition zone (45 deg) retains portrait cleanly without jitter
        current = `in`.yourview.yvgpscam.ui.screens.resolveDeviceCaptureOrientation(45, current)
        assertEquals(`in`.yourview.yvgpscam.ui.screens.DeviceCaptureOrientation.PORTRAIT, current)

        // Tilted to 90 deg right -> Landscape
        current = `in`.yourview.yvgpscam.ui.screens.resolveDeviceCaptureOrientation(90, current)
        assertEquals(`in`.yourview.yvgpscam.ui.screens.DeviceCaptureOrientation.LANDSCAPE_RIGHT, current)
        assertTrue(current.isLandscape)
        assertEquals("LANDSCAPE", current.label)

        // Transition zone retains landscape cleanly
        current = `in`.yourview.yvgpscam.ui.screens.resolveDeviceCaptureOrientation(45, current)
        assertEquals(`in`.yourview.yvgpscam.ui.screens.DeviceCaptureOrientation.LANDSCAPE_RIGHT, current)

        // Tilted to 270 deg left -> Landscape
        current = `in`.yourview.yvgpscam.ui.screens.resolveDeviceCaptureOrientation(270, current)
        assertEquals(`in`.yourview.yvgpscam.ui.screens.DeviceCaptureOrientation.LANDSCAPE_LEFT, current)
        assertTrue(current.isLandscape)

        // Unknown sensor reading (-1) preserves current orientation
        current = `in`.yourview.yvgpscam.ui.screens.resolveDeviceCaptureOrientation(-1, current)
        assertEquals(`in`.yourview.yvgpscam.ui.screens.DeviceCaptureOrientation.LANDSCAPE_LEFT, current)
    }

    @Test
    fun `brand logo remains permanently enabled and batch delete removes selected photos`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stampPrefs = `in`.yourview.yvgpscam.data.local.StampPreferencesRepository(context)
        stampPrefs.updateConfig { it.copy(showLogo = false, logoOpacity = 0.85f) }
        val persistedConfig = stampPrefs.stampConfig.first()
        assertTrue(persistedConfig.showLogo)
        assertEquals(0.85f, persistedConfig.logoOpacity, 0.01f)

        val renderer = PhotoStampRenderer(context)
        val source = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888)
        val telemetry = GpsTelemetry(
            latitude = 37.422094,
            longitude = -122.084057,
            signalState = GpsSignalState.LOCKED
        )
        val stamped = renderer.stampBitmap(source, telemetry, StampConfig(showLogo = false), null)
        assertEquals(800, stamped.width)
        stamped.recycle()
        source.recycle()

        val db = AppDatabase.getInstance(context)
        val dao = db.capturedPhotoDao()
        val photoRepo = `in`.yourview.yvgpscam.data.local.PhotoRepository(context, dao)

        val id1 = photoRepo.insertPhoto(
            `in`.yourview.yvgpscam.data.model.CapturedPhotoEntity(
                mediaUri = "file:///tmp/test_del_1.jpg",
                displayName = "YV_Del_1.jpg",
                capturedAtMillis = 1763141041000L,
                latitude = 37.42,
                longitude = -122.08,
                altitudeMeters = 10.0,
                accuracyMeters = 3f,
                compassAzimuth = 0f,
                compassCardinal = "N",
                addressLine = "Addr 1",
                cityState = "City 1",
                country = "USA",
                customNote = "",
                templateName = "TACTICAL_DARK",
                width = 800,
                height = 1000
            )
        )
        val id2 = photoRepo.insertPhoto(
            `in`.yourview.yvgpscam.data.model.CapturedPhotoEntity(
                mediaUri = "file:///tmp/test_del_2.jpg",
                displayName = "YV_Del_2.jpg",
                capturedAtMillis = 1763141042000L,
                latitude = 37.43,
                longitude = -122.09,
                altitudeMeters = 12.0,
                accuracyMeters = 3f,
                compassAzimuth = 90f,
                compassCardinal = "E",
                addressLine = "Addr 2",
                cityState = "City 2",
                country = "USA",
                customNote = "",
                templateName = "TACTICAL_DARK",
                width = 800,
                height = 1000
            )
        )
        val id3 = photoRepo.insertPhoto(
            `in`.yourview.yvgpscam.data.model.CapturedPhotoEntity(
                mediaUri = "file:///tmp/test_keep_3.jpg",
                displayName = "YV_Keep_3.jpg",
                capturedAtMillis = 1763141043000L,
                latitude = 37.44,
                longitude = -122.10,
                altitudeMeters = 15.0,
                accuracyMeters = 3f,
                compassAzimuth = 180f,
                compassCardinal = "S",
                addressLine = "Addr 3",
                cityState = "City 3",
                country = "USA",
                customNote = "",
                templateName = "TACTICAL_DARK",
                width = 800,
                height = 1000
            )
        )

        val stored = dao.getAllPhotos().first()
        val toDelete = stored.filter { it.id == id1 || it.id == id2 }
        assertEquals(2, toDelete.size)

        val result = photoRepo.deletePhotos(toDelete)
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())

        val remainingIds = dao.getAllPhotos().first().map { it.id }.toSet()
        assertFalse(remainingIds.contains(id1))
        assertFalse(remainingIds.contains(id2))
        assertTrue(remainingIds.contains(id3))

        // Clean up remaining test row
        photoRepo.deletePhotos(dao.getAllPhotos().first().filter { it.id == id3 })
        Unit
    }

    @Test
    fun `main camera route locks portrait orientation while secondary screens stay rotatable`() {
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            resolveScreenOrientationForRoute(isMainCameraPage = true)
        )
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            resolveScreenOrientationForRoute(isMainCameraPage = false)
        )
    }

    @Test
    fun `plus code country flag emoji and zoom bounds compute accurately`() {
        val telemetry = GpsTelemetry(
            latitude = 37.422094,
            longitude = -122.084057,
            country = "USA",
            countryCode = "US",
            magneticFieldMicroTesla = 418f,
            signalState = GpsSignalState.LOCKED
        )
        val plus = telemetry.plusCode
        assertNotNull(plus)
        assertTrue(plus!!.contains("+"))
        assertTrue(plus.length >= 10)
        assertEquals("🇺🇸", telemetry.countryFlagEmoji)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(context)
        val offlineRepo = OfflineMapRepository(context, db.offlineRegionDao())
        val vm = `in`.yourview.yvgpscam.ui.viewmodel.CameraViewModel(
            locationManager = `in`.yourview.yvgpscam.sensors.LocationTelemetryManager(context),
            stampPrefs = `in`.yourview.yvgpscam.data.local.StampPreferencesRepository(context),
            photoRepository = `in`.yourview.yvgpscam.data.local.PhotoRepository(context, db.capturedPhotoDao()),
            miniMapRenderer = MiniMapTileRenderer(offlineRepo),
            mediaStoreSaver = `in`.yourview.yvgpscam.camera.MediaStoreSaver(context, PhotoStampRenderer(context))
        )
        vm.updateZoomBounds(0.5f, 8f)
        vm.setZoomRatio(0.5f)
        assertEquals(0.5f, vm.hudState.value.minZoomRatio, 0.01f)
        assertEquals(0.5f, vm.hudState.value.zoomRatio, 0.01f)
    }

    @Test
    fun `custom filename builder formats selected parts and sequence number increments cleanly`(): Unit = runBlocking {
        val telemetry = GpsTelemetry(
            latitude = 64.545127,
            longitude = -19.091384,
            cityState = "Southern Region",
            country = "Iceland",
            addressLine = "Massa Marittima",
            signalState = GpsSignalState.LOCKED
        )
        val compactDefaultName = `in`.yourview.yvgpscam.camera.MediaStoreSaver.buildCustomFileName(
            config = StampConfig(),
            telemetry = telemetry,
            timestampMillis = 1763141040000L
        )
        assertTrue(compactDefaultName.startsWith("YVGPSCAM_"))
        assertTrue(compactDefaultName.endsWith("_1.jpg"))
        assertTrue("Expected short default filename, got: $compactDefaultName", compactDefaultName.length <= 30)

        val config = StampConfig(
            customFileNameEnabled = true,
            fnIncludeDateTime = true,
            fnIncludeSequence = true,
            photoSequenceNumber = 7,
            fnIncludeCustom1 = true,
            fnCustomText1 = "YVGPSCAM",
            fnIncludeCustom2 = true,
            fnCustomText2 = "SurveyA",
            fnIncludeFullAddress = false,
            fnIncludeLatLon = true,
            fnIncludePlusCode = false
        )
        val generated = `in`.yourview.yvgpscam.camera.MediaStoreSaver.buildCustomFileName(
            config = config,
            telemetry = telemetry,
            timestampMillis = 1763141040000L
        )
        assertTrue(generated.endsWith(".jpg"))
        assertTrue(generated.contains("_7_"))
        assertTrue(generated.contains("YVGPSCAM"))
        assertTrue(generated.contains("SurveyA"))
        assertTrue(generated.contains("64.545_-19.091"))
        assertTrue("Expected compact filename <= 52 chars, got: $generated", generated.length <= 52)

        val maxFieldsConfig = config.copy(
            fnIncludeCustom3 = true,
            fnCustomText3 = "DepartmentLongCode",
            fnIncludeFullAddress = true,
            fnIncludePlusCode = true,
            fnIncludeTimeZone = true,
            fnIncludeNote = true,
            customNote = "Very long site inspection note"
        )
        val cappedName = `in`.yourview.yvgpscam.camera.MediaStoreSaver.buildCustomFileName(
            config = maxFieldsConfig,
            telemetry = telemetry,
            timestampMillis = 1763141040000L
        )
        assertTrue(cappedName.endsWith(".jpg"))
        assertTrue(cappedName.contains("YVGPSCAM"))
        assertTrue("Expected capped filename <= 52 chars, got: $cappedName", cappedName.length <= 52)

        val fallbackConfig = config.copy(customFileNameEnabled = false)
        val defaultName = `in`.yourview.yvgpscam.camera.MediaStoreSaver.buildCustomFileName(
            config = fallbackConfig,
            telemetry = telemetry,
            timestampMillis = 1763141040000L
        )
        assertTrue(defaultName.startsWith("YVGPSCAM_"))
        assertTrue(defaultName.endsWith(".jpg"))
        assertTrue("Expected compact fallback filename, got: $defaultName", defaultName.length <= 28)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val stampPrefs = `in`.yourview.yvgpscam.data.local.StampPreferencesRepository(context)
        val beforeSeq = stampPrefs.stampConfig.first().photoSequenceNumber
        stampPrefs.incrementSequenceNumber()
        val afterSeq = stampPrefs.stampConfig.first().photoSequenceNumber
        assertEquals(beforeSeq + 1, afterSeq)

        assertTrue(telemetry.formatTimeZoneOffset(1763141040000L).startsWith("GMT "))
        Unit
    }

    @Test
    fun `all 6 layout templates and QR helper render without error`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val renderer = PhotoStampRenderer(context)
        val source = Bitmap.createBitmap(1080, 1440, Bitmap.Config.ARGB_8888)
        val miniMap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val telemetry = GpsTelemetry(
            latitude = 17.385044,
            longitude = 78.486671,
            altitudeMeters = 505.0,
            accuracyMeters = 3.2f,
            compassAzimuth = 225f,
            compassCardinal = "SW",
            addressLine = "Nagole - Bandlaguda Rd, Hyderabad",
            cityState = "Hyderabad, Telangana",
            country = "India",
            signalState = GpsSignalState.LOCKED
        )

        val qrUrl = `in`.yourview.yvgpscam.util.QrCodeHelper.buildLocationQrUrl(telemetry.latitude, telemetry.longitude)
        assertTrue(qrUrl.contains("17.385044"))
        val qrBmp = `in`.yourview.yvgpscam.util.QrCodeHelper.generateQrBitmap(qrUrl, 160)
        assertEquals(160, qrBmp.width)
        qrBmp.recycle()

        for (layout in `in`.yourview.yvgpscam.data.model.StampLayoutTemplate.entries) {
            val cfg = StampConfig(
                layoutTemplate = layout,
                reportingLabel = "Checked In",
                personName = "Surveyor YV",
                showPersonName = true,
                showCameraIndicator = true
            )
            val stamped = renderer.stampBitmap(source, telemetry, cfg, miniMap, isFrontCamera = true)
            assertEquals(1080, stamped.width)
            assertEquals(1440, stamped.height)
            stamped.recycle()
        }
        source.recycle()
        miniMap.recycle()
    }

    @Test
    fun `custom filename ordering manual locations settings persistence and hidden ads work properly`(): Unit = runBlocking {
        assertFalse(`in`.yourview.yvgpscam.ui.components.AdSlotConfig.AD_SLOT_ENABLED)

        val telemetry = GpsTelemetry(
            latitude = 17.385044,
            longitude = 78.486671,
            cityState = "Hyderabad",
            country = "India",
            signalState = GpsSignalState.LOCKED
        )
        val customOrder = listOf(
            `in`.yourview.yvgpscam.data.model.FileNameField.CUSTOM_3,
            `in`.yourview.yvgpscam.data.model.FileNameField.NOTE,
            `in`.yourview.yvgpscam.data.model.FileNameField.TIME_ZONE,
            `in`.yourview.yvgpscam.data.model.FileNameField.SEQUENCE
        )
        val orderedCfg = StampConfig(
            customFileNameEnabled = true,
            fnIncludeDateTime = false,
            fnIncludeSequence = true,
            photoSequenceNumber = 42,
            fnIncludeCustom1 = false,
            fnCustomText1 = "OtherBrand",
            fnIncludeCustom3 = true,
            fnCustomText3 = "SubstationB",
            fnIncludeLatLon = false,
            fnIncludeTimeZone = true,
            fnIncludeNote = true,
            customNote = "Inspected",
            fnFieldOrderSerialized = StampConfig.serializeFnFieldOrder(customOrder)
        )
        val name = `in`.yourview.yvgpscam.camera.MediaStoreSaver.buildCustomFileName(orderedCfg, telemetry, 1763141040000L)
        assertTrue(name.contains("YVGPSCAM"))
        assertFalse(name.contains("OtherBrand"))
        assertTrue(name.startsWith("SubstationB_Inspected_"))
        assertTrue(name.contains("_42"))
        assertTrue(name.indexOf("SubstationB") < name.indexOf("Inspected"))
        assertTrue(name.indexOf("Inspected") < name.indexOf("_42"))
        assertTrue(name.endsWith(".jpg"))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val stampPrefs = `in`.yourview.yvgpscam.data.local.StampPreferencesRepository(context)
        val manualList = listOf(
            `in`.yourview.yvgpscam.data.model.ManualLocationItem("id_1", "Plant Gate", "Industrial Area", 17.40, 78.50)
        )
        stampPrefs.updateConfig {
            it.copy(
                appThemeMode = `in`.yourview.yvgpscam.data.model.AppThemeMode.LIGHT,
                photoQuality = `in`.yourview.yvgpscam.data.model.PhotoQualityLevel.MEDIUM,
                videoQuality = `in`.yourview.yvgpscam.data.model.VideoQualityLevel.UHD_4K,
                volumeButtonAction = `in`.yourview.yvgpscam.data.model.VolumeButtonAction.ZOOM,
                screenTapAction = `in`.yourview.yvgpscam.data.model.ScreenTapAction.CAPTURE,
                stampOrientation = `in`.yourview.yvgpscam.data.model.StampOrientationMode.LANDSCAPE,
                autoSaveOriginalPhoto = true,
                showLogo = false, // Must remain locked true
                customBrandLabel = "TAMPERED", // Must stay YVGPSCAM
                fnIncludeCustom1 = false, // Must stay locked true
                fnCustomText1 = "TAMPERED", // Must stay YVGPSCAM
                useManualLocation = true,
                selectedManualLocationId = "id_1",
                manualLocationsJson = StampConfig.serializeManualLocations(manualList)
            )
        }
        val persisted = stampPrefs.stampConfig.first()
        assertEquals(`in`.yourview.yvgpscam.data.model.AppThemeMode.LIGHT, persisted.appThemeMode)
        assertEquals(`in`.yourview.yvgpscam.data.model.PhotoQualityLevel.MEDIUM, persisted.photoQuality)
        assertEquals(`in`.yourview.yvgpscam.data.model.VideoQualityLevel.UHD_4K, persisted.videoQuality)
        assertEquals(`in`.yourview.yvgpscam.data.model.VolumeButtonAction.ZOOM, persisted.volumeButtonAction)
        assertEquals(`in`.yourview.yvgpscam.data.model.ScreenTapAction.CAPTURE, persisted.screenTapAction)
        assertEquals(`in`.yourview.yvgpscam.data.model.StampOrientationMode.LANDSCAPE, persisted.stampOrientation)
        assertTrue(persisted.autoSaveOriginalPhoto)
        assertTrue(persisted.showLogo)
        assertEquals(StampConfig.FIXED_BRAND_NAME, persisted.customBrandLabel)
        assertTrue(persisted.fnIncludeCustom1)
        assertEquals(StampConfig.FIXED_BRAND_NAME, persisted.fnCustomText1)
        assertEquals("Plant Gate", persisted.selectedManualLocation()?.shortName)

        // Verify PDF report creation
        val samplePhoto = `in`.yourview.yvgpscam.data.model.CapturedPhotoEntity(
            id = 1L,
            mediaUri = "file:///tmp/non_existent.jpg",
            displayName = "YV_Site_1.jpg",
            capturedAtMillis = 1763141040000L,
            latitude = 17.385044,
            longitude = 78.486671,
            altitudeMeters = 505.0,
            accuracyMeters = 3.2f,
            compassAzimuth = 90f,
            compassCardinal = "E",
            addressLine = "Nagole Rd",
            cityState = "Hyderabad",
            country = "India",
            customNote = "Pole check",
            templateName = "ADVANCE",
            width = 1080,
            height = 1440
        )
        val pdfResult = `in`.yourview.yvgpscam.util.PdfReportGenerator.generateReport(
            context = context,
            title = "Site Audit",
            inspectorName = "Tester",
            summaryNote = "OK",
            photos = listOf(samplePhoto)
        )
        assertTrue(pdfResult.exceptionOrNull()?.stackTraceToString() ?: "PDF failed", pdfResult.isSuccess)
        val report = pdfResult.getOrNull()!!
        assertTrue(report.file.exists())
        assertTrue(report.fileSizeBytes > 0L)
        assertEquals(1, report.pageCount)
        report.file.delete()
        Unit
    }

    @Test
    fun `app update coordinator evaluates new version manifest and respects dismissal rules`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val coordinator = `in`.yourview.yvgpscam.util.AppUpdateCoordinator(context)

        // Same or older version should return null
        val olderResult = coordinator.evaluateManifestJson(
            manifestJson = """{"versionCode":1,"versionName":"1.0","mandatory":false}""",
            currentVersionCode = 1,
            currentVersionName = "1.0"
        )
        assertEquals(null, olderResult)

        // Newer optional release should trigger popup state
        val newerJson = """
            {
              "versionCode": 25,
              "versionName": "2.5.0",
              "releaseNotes": "Faster map tiles and cleaner stamp rendering.",
              "mandatory": false,
              "storeUrl": "https://play.google.com/store/apps/details?id=in.yourview.yvgpscam"
            }
        """.trimIndent()
        val detected = coordinator.evaluateManifestJson(
            manifestJson = newerJson,
            currentVersionCode = 1,
            currentVersionName = "1.0"
        )
        assertNotNull(detected)
        assertEquals(25, detected!!.availableVersionCode)
        assertEquals("2.5.0", detected.availableVersionLabel)
        assertFalse(detected.isImmediateRequired)
        assertEquals(25, coordinator.updateState.value?.availableVersionCode)

        // Dismissing persists skip for build 25 unless forceShow = true
        coordinator.dismissUpdateForNow()
        assertEquals(null, coordinator.updateState.value)

        val suppressed = coordinator.evaluateManifestJson(
            manifestJson = newerJson,
            currentVersionCode = 1,
            currentVersionName = "1.0",
            forceShow = false
        )
        assertEquals(null, suppressed)

        val forced = coordinator.evaluateManifestJson(
            manifestJson = newerJson,
            currentVersionCode = 1,
            currentVersionName = "1.0",
            forceShow = true
        )
        assertNotNull(forced)
        assertEquals(25, forced!!.availableVersionCode)

        // Mandatory update overrides previous dismissal
        val mandatoryJson = """{"versionCode":25,"versionName":"2.5.0","mandatory":true}"""
        val mandatoryState = coordinator.evaluateManifestJson(
            manifestJson = mandatoryJson,
            currentVersionCode = 1,
            currentVersionName = "1.0",
            forceShow = false
        )
        assertNotNull(mandatoryState)
        assertTrue(mandatoryState!!.isImmediateRequired)
        coordinator.dispose()
    }

    @Test
    fun `realistic mini map renders cleanly without fix across all map styles`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(context)
        val offlineRepo = OfflineMapRepository(context, db.offlineRegionDao())
        val renderer = MiniMapTileRenderer(offlineRepo)

        for (style in MapStyle.entries) {
            val bmp = renderer.renderMiniMapBitmap(
                latitude = null,
                longitude = null,
                outputSizePx = 280,
                mapStyle = style,
                offlineOnly = true
            )
            assertEquals(280, bmp.width)
            assertEquals(280, bmp.height)
            bmp.recycle()
        }
    }

    @Test
    fun `offline native gps fix arbitration accepts GPS provider updates without internet`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = `in`.yourview.yvgpscam.sensors.LocationTelemetryManager(context)
        val baseTime = System.currentTimeMillis()

        val coarseNetwork = android.location.Location(android.location.LocationManager.NETWORK_PROVIDER).apply {
            latitude = 37.420000
            longitude = -122.080000
            accuracy = 85f
            time = baseTime - 30_000L
        }
        assertTrue(`in`.yourview.yvgpscam.sensors.LocationTelemetryManager.isBetterLocation(coarseNetwork, null))

        val offlineGpsFix = android.location.Location(android.location.LocationManager.GPS_PROVIDER).apply {
            latitude = 17.385044
            longitude = 78.486671
            altitude = 505.0
            accuracy = 6.5f
            speed = 2.0f
            bearing = 140f
            time = baseTime
        }
        assertTrue(`in`.yourview.yvgpscam.sensors.LocationTelemetryManager.isBetterLocation(offlineGpsFix, coarseNetwork))

        // An older, much less accurate network sample must not override a fresh GPS fix
        val staleLessAccurate = android.location.Location(android.location.LocationManager.NETWORK_PROVIDER).apply {
            latitude = 17.390000
            longitude = 78.490000
            accuracy = 240f
            time = baseTime - 5_000L
        }
        assertFalse(`in`.yourview.yvgpscam.sensors.LocationTelemetryManager.isBetterLocation(staleLessAccurate, offlineGpsFix))

        manager.acceptLocationForTest(offlineGpsFix)
        val telemetry = manager.telemetry.value
        assertEquals(GpsSignalState.LOCKED, telemetry.signalState)
        assertEquals(17.385044, telemetry.latitude!!, 0.000001)
        assertEquals(78.486671, telemetry.longitude!!, 0.000001)
        assertEquals(505.0, telemetry.altitudeMeters!!, 0.1)
        assertEquals("GPS", telemetry.provider)
    }

    @Test
    fun `app update coordinator parses github release tags and persists auto push toggle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val coordinator = `in`.yourview.yvgpscam.util.AppUpdateCoordinator(context)

        assertEquals(10203, `in`.yourview.yvgpscam.util.AppUpdateCoordinator.parseVersionCodeFromTag("v1.2.3"))
        assertEquals(20001, `in`.yourview.yvgpscam.util.AppUpdateCoordinator.parseVersionCodeFromTag("2.0.1"))
        assertEquals(0, `in`.yourview.yvgpscam.util.AppUpdateCoordinator.parseVersionCodeFromTag(""))

        val githubReleaseJson = """
            {
              "tag_name": "v1.2.0",
              "body": "Offline GPS fix + modernised camera toolbar + auto-push alerts.",
              "html_url": "https://github.com/yvgpscam/releases/tag/v1.2.0"
            }
        """.trimIndent()
        val parsed = coordinator.evaluateManifestJson(
            manifestJson = githubReleaseJson,
            currentVersionCode = 2,
            currentVersionName = "1.1.0",
            forceShow = true
        )
        assertNotNull(parsed)
        assertEquals(10200, parsed!!.availableVersionCode)
        assertEquals("1.2.0", parsed.availableVersionLabel)
        assertEquals("https://github.com/yvgpscam/releases/tag/v1.2.0", parsed.storeUrl)

        coordinator.setAutoPushEnabled(false)
        assertFalse(coordinator.autoPushEnabled.value)
        coordinator.setAutoPushEnabled(true)
        assertTrue(coordinator.autoPushEnabled.value)

        coordinator.setManifestFeedUrl("https://example.com/yvgpscam/latest.json")
        assertEquals("https://example.com/yvgpscam/latest.json", coordinator.manifestFeedUrl.value)
        coordinator.setManifestFeedUrl("")
        coordinator.dispose()
    }

    @Test
    fun `camera viewmodel toggles auto capture mode and cycles interval cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(context)
        val offlineRepo = OfflineMapRepository(context, db.offlineRegionDao())
        val vm = `in`.yourview.yvgpscam.ui.viewmodel.CameraViewModel(
            locationManager = `in`.yourview.yvgpscam.sensors.LocationTelemetryManager(context),
            stampPrefs = `in`.yourview.yvgpscam.data.local.StampPreferencesRepository(context),
            photoRepository = `in`.yourview.yvgpscam.data.local.PhotoRepository(context, db.capturedPhotoDao()),
            miniMapRenderer = MiniMapTileRenderer(offlineRepo),
            mediaStoreSaver = `in`.yourview.yvgpscam.camera.MediaStoreSaver(context, PhotoStampRenderer(context))
        )

        assertEquals(`in`.yourview.yvgpscam.ui.viewmodel.CameraCaptureMode.PHOTO, vm.hudState.value.captureMode)
        assertEquals(3, vm.hudState.value.autoCaptureIntervalSeconds)

        vm.toggleAutoCaptureMode()
        assertEquals(`in`.yourview.yvgpscam.ui.viewmodel.CameraCaptureMode.AUTO_CAPTURE, vm.hudState.value.captureMode)

        vm.cycleAutoCaptureInterval()
        assertEquals(5, vm.hudState.value.autoCaptureIntervalSeconds)
        vm.cycleAutoCaptureInterval()
        assertEquals(10, vm.hudState.value.autoCaptureIntervalSeconds)
        vm.cycleAutoCaptureInterval()
        assertEquals(3, vm.hudState.value.autoCaptureIntervalSeconds)

        vm.setAutoCaptureState(isActive = true, count = 4, countdownSec = 2)
        assertTrue(vm.hudState.value.isAutoCapturing)
        assertEquals(4, vm.hudState.value.autoCaptureCount)
        assertEquals(2, vm.hudState.value.autoCaptureCountdownSec)

        // Switching back to PHOTO cleanly clears active Auto Capture status
        vm.setCaptureMode(`in`.yourview.yvgpscam.ui.viewmodel.CameraCaptureMode.PHOTO)
        assertEquals(`in`.yourview.yvgpscam.ui.viewmodel.CameraCaptureMode.PHOTO, vm.hudState.value.captureMode)
        assertFalse(vm.hudState.value.isAutoCapturing)
    }

    @Test
    fun `in app map math converts coordinates, distance, bearing, and fit bounds accurately`() {
        val lat = 17.385044
        val lon = 78.486671
        val (nx, ny) = `in`.yourview.yvgpscam.map.InAppMapMath.latLonToNormalizedMercator(lat, lon)
        val (roundLat, roundLon) = `in`.yourview.yvgpscam.map.InAppMapMath.normalizedMercatorToLatLon(nx, ny)
        assertEquals(lat, roundLat, 0.0005)
        assertEquals(lon, roundLon, 0.0005)

        val (distMeters, cardinal) = `in`.yourview.yvgpscam.map.InAppMapMath.computeDistanceMetersAndBearing(
            fromLat = 17.385044,
            fromLon = 78.486671,
            toLat = 17.394044,
            toLon = 78.486671
        )
        assertTrue(distMeters in 950.0..1050.0)
        assertEquals("N", cardinal)
        assertEquals("1.00 km", `in`.yourview.yvgpscam.map.InAppMapMath.formatDistance(1000.0))
        assertEquals("450 m", `in`.yourview.yvgpscam.map.InAppMapMath.formatDistance(450.2))

        val (_, _, fitZoom) = `in`.yourview.yvgpscam.map.InAppMapMath.computeZoomToFitBounds(
            lats = listOf(17.385044, 17.440081),
            lons = listOf(78.486671, 78.348915),
            viewportWidthPx = 1080f,
            viewportHeightPx = 1440f,
            baseTilePx = 512f
        )
        assertTrue(fitZoom in 3.5f..16.5f)
    }

    @Test
    fun `qr coordinate parser extracts geo and maps links for in-app map routing`() {
        val geoCoords = `in`.yourview.yvgpscam.ui.components.parseMapQrCoordinates("geo:17.385044,78.486671?q=Site+A")
        assertNotNull(geoCoords)
        assertEquals(17.385044, geoCoords!!.first, 0.000001)
        assertEquals(78.486671, geoCoords.second, 0.000001)

        val mapsUrlCoords = `in`.yourview.yvgpscam.ui.components.parseMapQrCoordinates("https://maps.google.com/?q=37.422094,-122.084057")
        assertNotNull(mapsUrlCoords)
        assertEquals(37.422094, mapsUrlCoords!!.first, 0.000001)
        assertEquals(-122.084057, mapsUrlCoords.second, 0.000001)

        assertNull(`in`.yourview.yvgpscam.ui.components.parseMapQrCoordinates("https://example.com/report"))
        assertNull(`in`.yourview.yvgpscam.ui.components.parseMapQrCoordinates("geo:999.0,0.0"))
    }

    @Test
    fun `photo detail zoom math scales clamps pan proportionally and resets at 1x`() {
        val (zoomedScale, zoomedPan) = `in`.yourview.yvgpscam.ui.screens.computeZoomedTransform(
            currentScale = 1f,
            currentPan = androidx.compose.ui.geometry.Offset.Zero,
            zoomDelta = 2.5f,
            panDelta = androidx.compose.ui.geometry.Offset(5000f, -5000f),
            viewportWidth = 1000f,
            viewportHeight = 1400f
        )
        assertEquals(2.5f, zoomedScale, 0.001f)
        // maxX = 1000 * (2.5 - 1) / 2 = 750f; maxY = 1400 * 1.5 / 2 = 1050f
        assertEquals(750f, zoomedPan.x, 0.01f)
        assertEquals(-1050f, zoomedPan.y, 0.01f)

        val (cappedScale, _) = `in`.yourview.yvgpscam.ui.screens.computeZoomedTransform(
            currentScale = 4.5f,
            currentPan = androidx.compose.ui.geometry.Offset.Zero,
            zoomDelta = 2.0f,
            panDelta = androidx.compose.ui.geometry.Offset.Zero,
            viewportWidth = 1000f,
            viewportHeight = 1400f
        )
        assertEquals(5f, cappedScale, 0.001f)

        val (resetScale, resetPan) = `in`.yourview.yvgpscam.ui.screens.computeZoomedTransform(
            currentScale = 2.5f,
            currentPan = androidx.compose.ui.geometry.Offset(200f, 300f),
            zoomDelta = 0.2f,
            panDelta = androidx.compose.ui.geometry.Offset(10f, 10f),
            viewportWidth = 1000f,
            viewportHeight = 1400f
        )
        assertEquals(1f, resetScale, 0.001f)
        assertEquals(androidx.compose.ui.geometry.Offset.Zero, resetPan)
    }

    @Test
    fun `dcim gallery export worker mirrors exif tagged jpeg and preserves gps coordinates`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(context)

        val sourceFile = java.io.File(context.cacheDir, "YVGPSCAM_work_test_src.jpg")
        val bmp = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        java.io.FileOutputStream(sourceFile).use { fos ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, fos)
        }
        bmp.recycle()

        val exif = androidx.exifinterface.media.ExifInterface(sourceFile)
        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_MAKE, "YVGPSCAM")
        exif.setLatLong(17.385044, 78.486671)
        exif.saveAttributes()

        val displayName = "YVGPSCAM_work_exported_${System.currentTimeMillis()}.jpg"
        `in`.yourview.yvgpscam.work.DcimExportScheduler.enqueueAfterExif(
            context = context,
            sourceUri = android.net.Uri.fromFile(sourceFile).toString(),
            displayName = displayName,
            capturedAtMillis = 1763141040000L
        )
        val enqueued = androidx.work.WorkManager.getInstance(context)
            .getWorkInfosByTag(`in`.yourview.yvgpscam.work.DcimExportScheduler.WORK_TAG)
            .get()
        assertTrue(enqueued.isNotEmpty())

        val worker = androidx.work.testing.TestListenableWorkerBuilder<`in`.yourview.yvgpscam.work.DcimGalleryExportWorker>(
            context = context
        ).setInputData(
            androidx.work.workDataOf(
                `in`.yourview.yvgpscam.work.DcimGalleryExportWorker.KEY_SOURCE_URI to android.net.Uri.fromFile(sourceFile).toString(),
                `in`.yourview.yvgpscam.work.DcimGalleryExportWorker.KEY_DISPLAY_NAME to displayName,
                `in`.yourview.yvgpscam.work.DcimGalleryExportWorker.KEY_CAPTURED_AT_MILLIS to 1763141040000L
            )
        ).build()

        val result = worker.doWork()
        assertTrue(result is androidx.work.ListenableWorker.Result.Success)
        val exportedUriStr = (result as androidx.work.ListenableWorker.Result.Success)
            .outputData
            .getString(`in`.yourview.yvgpscam.work.DcimGalleryExportWorker.KEY_EXPORTED_URI)
        assertNotNull(exportedUriStr)

        context.contentResolver.openInputStream(android.net.Uri.parse(exportedUriStr))?.use { stream ->
            val copiedExif = androidx.exifinterface.media.ExifInterface(stream)
            assertEquals("YVGPSCAM", copiedExif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MAKE))
            assertEquals(17.385044, copiedExif.latLong!![0], 0.001)
            assertEquals(78.486671, copiedExif.latLong!![1], 0.001)
        }

        `in`.yourview.yvgpscam.work.DcimGalleryExporter.deleteMirroredDcimCopy(context, displayName)
        sourceFile.delete()
        Unit
    }

    @Test
    fun `location updates listener triggers mini map refresh only when movement exceeds 5 meters`() {
        var refreshTriggers = 0
        var lastLat = 0.0
        var lastLon = 0.0
        val listener = `in`.yourview.yvgpscam.sensors.LocationUpdatesListener { lat, lon, _ ->
            refreshTriggers++
            lastLat = lat
            lastLon = lon
        }

        // First fix always triggers initial anchor
        val originLat = 17.385000
        val originLon = 78.486600
        assertTrue(listener.onCoordinatesUpdated(originLat, originLon))
        assertEquals(1, refreshTriggers)

        // Small movement (~2.8 meters north) must NOT trigger refresh
        assertFalse(listener.onCoordinatesUpdated(originLat + 0.000025, originLon))
        assertEquals(1, refreshTriggers)

        // Another small step (~3.3 meters north -> cumulative ~6.1m from anchor) MUST trigger refresh
        assertTrue(listener.onCoordinatesUpdated(originLat + 0.000055, originLon))
        assertEquals(2, refreshTriggers)
        assertEquals(originLat + 0.000055, lastLat, 0.000001)
        assertEquals(originLon, lastLon, 0.000001)

        // Verify LocationTelemetryManager dispatches to registered LocationUpdatesListener
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mgr = `in`.yourview.yvgpscam.sensors.LocationTelemetryManager(context)
        var mgrTriggerCount = 0
        val mgrListener = `in`.yourview.yvgpscam.sensors.LocationUpdatesListener { _, _, _ ->
            mgrTriggerCount++
        }
        mgr.addLocationUpdatesListener(mgrListener)

        val now = System.currentTimeMillis()
        mgr.acceptLocationForTest(
            android.location.Location(android.location.LocationManager.GPS_PROVIDER).apply {
                latitude = originLat
                longitude = originLon
                accuracy = 4f
                time = now
            }
        )
        assertEquals(1, mgrTriggerCount)

        // 3m jitter -> ignored by mini-map threshold listener
        mgr.acceptLocationForTest(
            android.location.Location(android.location.LocationManager.GPS_PROVIDER).apply {
                latitude = originLat + 0.000027
                longitude = originLon
                accuracy = 4f
                time = now + 1000L
            }
        )
        assertEquals(1, mgrTriggerCount)

        // 8m move from anchor -> exceeds 5m threshold -> triggers refresh
        mgr.acceptLocationForTest(
            android.location.Location(android.location.LocationManager.GPS_PROVIDER).apply {
                latitude = originLat + 0.000099
                longitude = originLon
                accuracy = 4f
                time = now + 2000L
            }
        )
        assertEquals(2, mgrTriggerCount)
        mgr.removeLocationUpdatesListener(mgrListener)

        // Verify CameraViewModel wires LocationUpdatesListener and refreshes miniMapBitmap
        val db = AppDatabase.getInstance(context)
        val offlineRepo = OfflineMapRepository(context, db.offlineRegionDao())
        val stampPrefs = `in`.yourview.yvgpscam.data.local.StampPreferencesRepository(context)
        runBlocking {
            stampPrefs.updateConfig { it.copy(offlineOnlyMapMode = true, useManualLocation = false) }
        }

        val vm = `in`.yourview.yvgpscam.ui.viewmodel.CameraViewModel(
            locationManager = mgr,
            stampPrefs = stampPrefs,
            photoRepository = `in`.yourview.yvgpscam.data.local.PhotoRepository(context, db.capturedPhotoDao()),
            miniMapRenderer = MiniMapTileRenderer(offlineRepo),
            mediaStoreSaver = `in`.yourview.yvgpscam.camera.MediaStoreSaver(context, PhotoStampRenderer(context))
        )

        fun awaitRefresh(minCount: Long, timeoutMs: Long = 5000L): Long {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (vm.miniMapRefreshCount.value < minCount && System.currentTimeMillis() < deadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            return vm.miniMapRefreshCount.value
        }

        val initialCount = awaitRefresh(1L)
        assertTrue(initialCount >= 1L)

        // Move < 5m (~2.2m north) -> should NOT trigger mini-map refresh
        mgr.acceptLocationForTest(
            android.location.Location(android.location.LocationManager.GPS_PROVIDER).apply {
                latitude = originLat + 0.000119
                longitude = originLon
                accuracy = 3.5f
                time = now + 3000L
            }
        )
        Thread.sleep(100L)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(initialCount, vm.miniMapRefreshCount.value)

        // Move > 5m (~12m north from last rendered originLat + 0.000099) -> MUST trigger mini-map refresh
        mgr.acceptLocationForTest(
            android.location.Location(android.location.LocationManager.GPS_PROVIDER).apply {
                latitude = originLat + 0.000210
                longitude = originLon
                accuracy = 3.5f
                time = now + 4000L
            }
        )
        val afterCount = awaitRefresh(initialCount + 1L)
        assertTrue(afterCount > initialCount)
        assertNotNull(vm.miniMapBitmap.value)
    }

    @Test
    fun `offline map repository supports parent zoom fallback circuit breaker and fast fail on network drop`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(context)

        // 1. Save a valid 256x256 PNG at zoom 14 and verify z=15 crops/upscales seamlessly when offline
        val offlineRepo = OfflineMapRepository(
            context = context,
            regionDao = db.offlineRegionDao(),
            isNetworkOnline = { false }
        )
        val tileBmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val baos = java.io.ByteArrayOutputStream()
        tileBmp.compress(Bitmap.CompressFormat.PNG, 90, baos)
        tileBmp.recycle()
        offlineRepo.saveTileBytes(14, 2620, 6332, baos.toByteArray(), MapStyle.STANDARD)

        // Clean exact Z15 target if leftover from other tests
        offlineRepo.getTileFile(15, 5241, 12665, MapStyle.STANDARD).delete()
        val fallbackBmp = offlineRepo.getBestAvailableCachedTileBitmap(15, 5241, 12665, MapStyle.STANDARD)
        assertNotNull(fallbackBmp)
        assertEquals(256, fallbackBmp!!.width)
        assertEquals(256, fallbackBmp.height)
        fallbackBmp.recycle()

        // 2. Sudden network failure in downloadRegion fails immediately when offline
        val downloadRes = offlineRepo.downloadRegion(
            regionName = "Remote Site",
            centerLat = 17.385044,
            centerLon = 78.486671,
            radiusKm = 1.0,
            zoomMin = 14,
            zoomMax = 15,
            onProgress = {}
        )
        assertTrue(downloadRes.isFailure)

        // 3. Sudden DNS / connection failure while OS still thinks Wi-Fi is up trips circuit breaker
        var callCount = 0
        val failingClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor {
                callCount++
                throw java.net.UnknownHostException("offline dead zone")
            }
            .build()
        val flakyRepo = OfflineMapRepository(
            context = context,
            regionDao = db.offlineRegionDao(),
            httpClient = failingClient,
            isNetworkOnline = { true }
        )
        assertTrue(flakyRepo.isNetworkUsableForTiles())
        val fetched = flakyRepo.fetchTileFromNetworkAndCache(15, 5000, 5000, MapStyle.STANDARD)
        assertNull(fetched)
        assertEquals(1, callCount) // Stopped immediately after UnknownHostException instead of retrying 3+ URLs
        assertFalse(flakyRepo.isNetworkUsableForTiles())

        // MiniMapTileRenderer renders immediately without hanging even with offlineOnly = false
        val renderer = MiniMapTileRenderer(flakyRepo)
        val rendered = renderer.renderMiniMapBitmap(
            latitude = 37.422094,
            longitude = -122.084057,
            outputSizePx = 260,
            mapStyle = MapStyle.STANDARD,
            offlineOnly = false
        )
        assertEquals(260, rendered.width)
        rendered.recycle()

        flakyRepo.resetNetworkBackoff()
        assertTrue(flakyRepo.isNetworkUsableForTiles())
    }

    @Test
    fun `location manager maintains gnss geotagging and regional locality when internet disappears`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = `in`.yourview.yvgpscam.sensors.LocationTelemetryManager(context)
        val baseTime = System.currentTimeMillis()

        // Pre-drop Wi-Fi/Network fix followed 25s later by a slightly coarser Fused/GNSS fix offline
        val preDropNetwork = android.location.Location(android.location.LocationManager.NETWORK_PROVIDER).apply {
            latitude = 17.385044
            longitude = 78.486671
            accuracy = 12f
            time = baseTime - 25_000L
        }
        val postDropFused = android.location.Location("fused").apply {
            latitude = 17.385400
            longitude = 78.486900
            accuracy = 28f
            time = baseTime
        }
        assertTrue(
            `in`.yourview.yvgpscam.sensors.LocationTelemetryManager.isBetterLocation(
                postDropFused,
                preDropNetwork
            )
        )

        // Seed cached address around origin (as if geocoded while online before losing signal)
        val originLoc = android.location.Location(android.location.LocationManager.GPS_PROVIDER).apply {
            latitude = 17.385044
            longitude = 78.486671
            accuracy = 5f
            time = baseTime
        }
        manager.saveCachedAddressForTest(
            loc = originLoc,
            street = "Nagole Main Rd",
            cityState = "Hyderabad, Telangana",
            country = "India",
            countryCode = "IN",
            postal = "500068"
        )

        // 1. Within 300m -> preserves full street + regional locality + exact GPS fix offline
        val nearbyFix = android.location.Location(android.location.LocationManager.GPS_PROVIDER).apply {
            latitude = 17.386000 // ~106m north
            longitude = 78.486671
            altitude = 510.0
            accuracy = 6f
            time = baseTime + 5_000L
        }
        manager.acceptLocationForTest(nearbyFix)
        var tel = manager.telemetry.value
        assertEquals(17.386000, tel.latitude!!, 0.000001)
        assertEquals("Nagole Main Rd", tel.addressLine)
        assertEquals("Hyderabad, Telangana", tel.cityState)
        assertEquals("India", tel.country)
        assertNotNull(tel.plusCode)

        // 2. ~1.5km away (outside 300m street radius, inside 5km regional radius) ->
        // clears stale street address while keeping city/country + exact coordinates + Plus Code
        val midRangeFix = android.location.Location(android.location.LocationManager.GPS_PROVIDER).apply {
            latitude = 17.398500 // ~1.5km north
            longitude = 78.486671
            altitude = 518.0
            accuracy = 8f
            time = baseTime + 30_000L
        }
        manager.acceptLocationForTest(midRangeFix)
        tel = manager.telemetry.value
        assertEquals(17.398500, tel.latitude!!, 0.000001)
        assertEquals("", tel.addressLine)
        assertEquals("Hyderabad, Telangana", tel.cityState)
        assertEquals("India", tel.country)
        assertEquals("IN", tel.countryCode)
        assertEquals(GpsSignalState.LOCKED, tel.signalState)
    }

    @Test
    fun `disk cached map tile provider serves exact tiles ancestor tiles and visited area static cache offline`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(context)
        val offlineRepo = OfflineMapRepository(
            context = context,
            regionDao = db.offlineRegionDao(),
            isNetworkOnline = { false }
        )

        val tileBmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val baos = java.io.ByteArrayOutputStream()
        tileBmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
        tileBmp.recycle()
        val pngBytes = baos.toByteArray()

        // Clear target & parent tiles first to guarantee deterministic disk state
        offlineRepo.getTileFile(14, 11761, 7406, MapStyle.STANDARD).delete()
        offlineRepo.getTileFile(15, 23522, 14812, MapStyle.STANDARD).delete()
        offlineRepo.getTileFile(15, 23523, 14813, MapStyle.STANDARD).delete()
        offlineRepo.getTileFile(15, 100, 100, MapStyle.STANDARD).delete()

        // Persist Z14 parent and one Z15 exact tile on disk
        offlineRepo.saveTileBytes(14, 11761, 7406, pngBytes, MapStyle.STANDARD)
        offlineRepo.saveTileBytes(15, 23522, 14812, pngBytes, MapStyle.STANDARD)

        val provider = `in`.yourview.yvgpscam.map.DiskCachedMapTileProvider(
            offlineMapRepository = offlineRepo,
            mapStyle = MapStyle.STANDARD,
            allowNetwork = { false }
        )

        // 1. Exact tile hit on disk
        val exactTile = provider.getTile(23522, 14812, 15)
        assertTrue(exactTile !== com.google.android.gms.maps.model.TileProvider.NO_TILE)
        assertEquals(256, exactTile.width)
        assertEquals(256, exactTile.height)
        assertNotNull(exactTile.data)
        assertTrue(exactTile.data!!.isNotEmpty())

        // 2. Sibling Z15 tile missing on disk -> crops/upscales from Z14 ancestor automatically
        val ancestorTile = provider.getTile(23523, 14813, 15)
        assertTrue(ancestorTile !== com.google.android.gms.maps.model.TileProvider.NO_TILE)
        assertEquals(256, ancestorTile.width)
        assertNotNull(ancestorTile.data)
        assertTrue(ancestorTile.data!!.isNotEmpty())

        // 3. Unvisited area offline -> returns NO_TILE cleanly
        val unvisitedTile = provider.getTile(100, 100, 15)
        assertEquals(com.google.android.gms.maps.model.TileProvider.NO_TILE, unvisitedTile)

        // 4. Nearest cached Google Static Map lookup matches nearby visited location (~90m away)
        val staticDir = java.io.File(context.filesDir, "offline_tiles/google_static").apply { mkdirs() }
        val visitedFile = java.io.File(staticDir, "STANDARD_z15_17.38504_78.48667.png")
        visitedFile.writeBytes(pngBytes)
        val nearbyStatic = offlineRepo.findNearestCachedStaticMapBytes(
            lat = 17.38580,
            lon = 78.48667,
            zoom = 15,
            mapStyle = MapStyle.STANDARD,
            maxDistanceMeters = 350.0
        )
        assertNotNull(nearbyStatic)
        val farStatic = offlineRepo.findNearestCachedStaticMapBytes(
            lat = 28.61390,
            lon = 77.20900,
            zoom = 15,
            mapStyle = MapStyle.STANDARD,
            maxDistanceMeters = 350.0
        )
        assertNull(farStatic)
        visitedFile.delete()
    }

    @Test
    fun `location metadata sync worker enriches offline photos and syncs cached location updates to cloud`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(context)

        val syncStore = `in`.yourview.yvgpscam.data.cloud.LocationMetadataSyncStore.getInstance(context)
        syncStore.clearAll()

        // 1. Offline location update gets cached locally & schedules WorkManager with NetworkType.CONNECTED
        val locationManager = `in`.yourview.yvgpscam.sensors.LocationTelemetryManager(
            context = context,
            isNetworkOnline = { false },
            syncStore = syncStore
        )
        locationManager.acceptLocationForTest(
            android.location.Location(android.location.LocationManager.GPS_PROVIDER).apply {
                latitude = 17.412345
                longitude = 78.456789
                altitude = 525.0
                accuracy = 6.0f
                time = System.currentTimeMillis()
            }
        )
        assertEquals(1, syncStore.pendingCountFlow.value)

        val workInfos = androidx.work.WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(`in`.yourview.yvgpscam.work.LocationMetadataSyncScheduler.UNIQUE_WORK_NAME)
            .get()
        assertTrue(workInfos.isNotEmpty())
        assertEquals(
            androidx.work.NetworkType.CONNECTED,
            `in`.yourview.yvgpscam.work.LocationMetadataSyncScheduler.buildSyncWorkRequest().workSpec.constraints.requiredNetworkType
        )

        // 2. Insert an offline-captured photo missing street/city address
        val db = AppDatabase.getInstance(context)
        val photoRepo = `in`.yourview.yvgpscam.data.local.PhotoRepository(context, db.capturedPhotoDao())
        val tempJpg = java.io.File(context.cacheDir, "offline_sync_test.jpg")
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        java.io.FileOutputStream(tempJpg).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bmp.recycle()

        val photoId = photoRepo.insertPhoto(
            `in`.yourview.yvgpscam.data.model.CapturedPhotoEntity(
                mediaUri = android.net.Uri.fromFile(tempJpg).toString(),
                displayName = "YVGPSCAM_OFFLINE_SYNC.jpg",
                capturedAtMillis = System.currentTimeMillis(),
                latitude = 17.412345,
                longitude = 78.456789,
                altitudeMeters = 525.0,
                accuracyMeters = 6.0f,
                compassAzimuth = 90f,
                compassCardinal = "E",
                addressLine = "",
                cityState = "",
                country = "",
                customNote = "Field site",
                templateName = "Classic",
                width = 64,
                height = 64
            )
        )

        // 3. Configure signed-in account + mock OkHttp Drive backend for worker
        val accountStore = `in`.yourview.yvgpscam.data.cloud.GoogleDriveAccountStore(context)
        accountStore.saveAccount("surveyor@example.com", "Field Surveyor")

        var uploadedMetadataJsonBody: String? = null
        val mockDriveClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                val jsonResponse = when {
                    url.contains("mimeType") && req.method == "GET" && url.contains("folder") ->
                        """{"files":[{"id":"folder_yv_1","name":"YVGPSCam"}]}"""
                    req.method == "GET" ->
                        """{"files":[]}"""
                    req.method == "POST" && url.contains("uploadType=multipart") -> {
                        val buffer = okio.Buffer()
                        req.body?.writeTo(buffer)
                        val bodyText = buffer.readUtf8()
                        if (bodyText.contains("location_metadata_sync")) {
                            uploadedMetadataJsonBody = bodyText
                            """{"id":"meta_file_1"}"""
                        } else {
                            """{"id":"photo_file_1","name":"YVGPSCAM_OFFLINE_SYNC.jpg","size":"1234","appProperties":{"bid":"b_1","cap":"1700000000000"}}"""
                        }
                    }
                    else -> """{"id":"ok_1"}"""
                }
                okhttp3.Response.Builder()
                    .request(req)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(jsonResponse.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val mockDriveRepo = `in`.yourview.yvgpscam.data.cloud.GoogleDriveBackupRepository(mockDriveClient)

        try {
            // Verify worker returns retry() while still offline
            `in`.yourview.yvgpscam.work.LocationMetadataSyncWorker.testOverrides =
                `in`.yourview.yvgpscam.work.LocationMetadataSyncWorker.TestDependencies(
                    isOnline = { false },
                    syncStore = syncStore
                )
            val offlineWorker = androidx.work.testing.TestListenableWorkerBuilder<`in`.yourview.yvgpscam.work.LocationMetadataSyncWorker>(context)
                .build()
            assertEquals(androidx.work.ListenableWorker.Result.retry(), offlineWorker.doWork())

            // Now simulate network restoration -> worker geocodes & uploads metadata + photo to Drive
            `in`.yourview.yvgpscam.work.LocationMetadataSyncWorker.testOverrides =
                `in`.yourview.yvgpscam.work.LocationMetadataSyncWorker.TestDependencies(
                    isOnline = { true },
                    syncStore = syncStore,
                    accountStore = accountStore,
                    driveRepository = mockDriveRepo,
                    photoRepository = photoRepo,
                    tokenProvider = { "mock_drive_token_123" },
                    geocoderOverride = { _, _ -> Triple("Banjara Hills Rd 12", "Hyderabad, Telangana", "India") }
                )

            val onlineWorker = androidx.work.testing.TestListenableWorkerBuilder<`in`.yourview.yvgpscam.work.LocationMetadataSyncWorker>(context)
                .build()
            val result = onlineWorker.doWork()
            assertTrue(result is androidx.work.ListenableWorker.Result.Success)
            val output = (result as androidx.work.ListenableWorker.Result.Success).outputData
            assertTrue(output.getInt(`in`.yourview.yvgpscam.work.LocationMetadataSyncWorker.KEY_GEOCODED_PHOTOS, 0) >= 1)
            assertEquals(1, output.getInt(`in`.yourview.yvgpscam.work.LocationMetadataSyncWorker.KEY_SYNCED_METADATA_ENTRIES, 0))
            assertEquals(0, syncStore.pendingCountFlow.value)
            assertNotNull(uploadedMetadataJsonBody)
            assertTrue(uploadedMetadataJsonBody!!.contains("YVGPSCAM"))
            assertTrue(uploadedMetadataJsonBody!!.contains("Banjara Hills Rd 12"))

            val enrichedPhoto = photoRepo.getPhotoById(photoId).first()
            assertEquals("Banjara Hills Rd 12", enrichedPhoto?.addressLine)
            assertEquals("Hyderabad, Telangana", enrichedPhoto?.cityState)
        } finally {
            `in`.yourview.yvgpscam.work.LocationMetadataSyncWorker.testOverrides = null
            accountStore.clearAccount()
            syncStore.clearAll()
            photoRepo.getPhotoById(photoId).first()?.let { photoRepo.deletePhoto(it) }
            tempJpg.delete()
        }
    }

    @Test
    fun `camera viewmodel transitions offline mode sync pending and online synced status indicator states`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(context)
        val syncStore = `in`.yourview.yvgpscam.data.cloud.LocationMetadataSyncStore.getInstance(context)
        syncStore.clearAll()

        val stampPrefs = `in`.yourview.yvgpscam.data.local.StampPreferencesRepository(context)
        runBlocking { stampPrefs.updateConfig { it.copy(offlineOnlyMapMode = false) } }

        val offlineRepo = OfflineMapRepository(context, db.offlineRegionDao(), isNetworkOnline = { false })
        val vm = `in`.yourview.yvgpscam.ui.viewmodel.CameraViewModel(
            locationManager = `in`.yourview.yvgpscam.sensors.LocationTelemetryManager(
                context = context,
                isNetworkOnline = { false },
                syncStore = syncStore
            ),
            stampPrefs = stampPrefs,
            photoRepository = `in`.yourview.yvgpscam.data.local.PhotoRepository(context, db.capturedPhotoDao()),
            miniMapRenderer = MiniMapTileRenderer(offlineRepo),
            mediaStoreSaver = `in`.yourview.yvgpscam.camera.MediaStoreSaver(context, PhotoStampRenderer(context)),
            connectivityObserver = null,
            locationSyncStore = syncStore
        )

        val collected = mutableListOf<`in`.yourview.yvgpscam.ui.viewmodel.CameraSyncIndicatorState>()
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
            vm.offlineSyncUiState.collect { collected.add(it) }
        }
        fun awaitState(predicate: (`in`.yourview.yvgpscam.ui.viewmodel.CameraSyncIndicatorState) -> Boolean) {
            val deadline = System.currentTimeMillis() + 3_000L
            while (!predicate(vm.offlineSyncUiState.value) && System.currentTimeMillis() < deadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }

        try {
            // 1. Default online with 0 pending -> ONLINE_SYNCED
            awaitState { it.mode == `in`.yourview.yvgpscam.ui.viewmodel.CameraConnectivitySyncMode.ONLINE_SYNCED }
            assertEquals(
                `in`.yourview.yvgpscam.ui.viewmodel.CameraConnectivitySyncMode.ONLINE_SYNCED,
                vm.offlineSyncUiState.value.mode
            )

            // 2. Network drops + offline location cached -> OFFLINE_MODE with pendingCount == 1
            vm.onNetworkConnectivityChanged(isOnline = false, hasLocationPermission = false)
            val entry = syncStore.recordOfflineLocation(
                latitude = 17.385,
                longitude = 78.486,
                forceRecord = true
            )
            assertNotNull(entry)
            awaitState {
                it.mode == `in`.yourview.yvgpscam.ui.viewmodel.CameraConnectivitySyncMode.OFFLINE_MODE &&
                    it.pendingCount == 1
            }
            assertEquals(
                `in`.yourview.yvgpscam.ui.viewmodel.CameraConnectivitySyncMode.OFFLINE_MODE,
                vm.offlineSyncUiState.value.mode
            )
            assertEquals(1, vm.offlineSyncUiState.value.pendingCount)

            // 3. Network restores while pending count > 0 -> SYNC_PENDING
            vm.onNetworkConnectivityChanged(isOnline = true, hasLocationPermission = false)
            awaitState { it.mode == `in`.yourview.yvgpscam.ui.viewmodel.CameraConnectivitySyncMode.SYNC_PENDING }
            assertEquals(
                `in`.yourview.yvgpscam.ui.viewmodel.CameraConnectivitySyncMode.SYNC_PENDING,
                vm.offlineSyncUiState.value.mode
            )

            // 4. Sync completes -> returns to ONLINE_SYNCED
            syncStore.markEntriesSynced(listOf(entry!!.id))
            awaitState {
                it.mode == `in`.yourview.yvgpscam.ui.viewmodel.CameraConnectivitySyncMode.ONLINE_SYNCED &&
                    it.pendingCount == 0
            }
            assertEquals(
                `in`.yourview.yvgpscam.ui.viewmodel.CameraConnectivitySyncMode.ONLINE_SYNCED,
                vm.offlineSyncUiState.value.mode
            )
            assertEquals(0, vm.offlineSyncUiState.value.pendingCount)
        } finally {
            job.cancel()
            syncStore.clearAll()
        }
    }
}

@Implements(android.graphics.pdf.PdfDocument::class)
class ShadowPdfDocument {
    private var renderedPages = 0

    @Implementation
    fun __constructor__() {}

    @Implementation
    fun startPage(pageInfo: android.graphics.pdf.PdfDocument.PageInfo): android.graphics.pdf.PdfDocument.Page {
        val bitmap = Bitmap.createBitmap(
            pageInfo.pageWidth,
            pageInfo.pageHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        val ctor = android.graphics.pdf.PdfDocument.Page::class.java.declaredConstructors.first {
            it.parameterCount == 2
        }
        ctor.isAccessible = true
        renderedPages++
        return ctor.newInstance(canvas, pageInfo) as android.graphics.pdf.PdfDocument.Page
    }

    @Implementation
    fun finishPage(page: android.graphics.pdf.PdfDocument.Page) {}

    @Implementation
    fun writeTo(out: java.io.OutputStream) {
        out.write("%PDF-1.4\n%%YVGPSCAM Robolectric Test Pages: $renderedPages\n%%EOF\n".toByteArray())
    }

    @Implementation
    fun close() {}
}
