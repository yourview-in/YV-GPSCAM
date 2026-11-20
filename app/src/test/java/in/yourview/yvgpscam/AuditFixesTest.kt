package `in`.yourview.yvgpscam

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import `in`.yourview.yvgpscam.camera.MediaStoreSaver
import `in`.yourview.yvgpscam.camera.PhotoStampRenderer
import `in`.yourview.yvgpscam.data.local.AppDatabase
import `in`.yourview.yvgpscam.data.local.PhotoRepository
import `in`.yourview.yvgpscam.data.local.StampPreferencesRepository
import `in`.yourview.yvgpscam.data.model.GpsSignalState
import `in`.yourview.yvgpscam.data.model.GpsTelemetry
import `in`.yourview.yvgpscam.data.model.StampConfig
import `in`.yourview.yvgpscam.data.model.StampLayoutTemplate
import `in`.yourview.yvgpscam.map.GoogleMapsConfig
import `in`.yourview.yvgpscam.map.MiniMapTileRenderer
import `in`.yourview.yvgpscam.map.OfflineMapRepository
import `in`.yourview.yvgpscam.sensors.LocationTelemetryManager
import `in`.yourview.yvgpscam.ui.components.StampPreviewCard
import `in`.yourview.yvgpscam.ui.components.findActivity
import `in`.yourview.yvgpscam.ui.screens.SettingsScreen
import `in`.yourview.yvgpscam.ui.theme.YvGpsCamTheme
import `in`.yourview.yvgpscam.ui.viewmodel.CameraAspectRatio
import `in`.yourview.yvgpscam.ui.viewmodel.CameraViewModel
import `in`.yourview.yvgpscam.util.LocaleHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuditFixesTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `LocaleHelper maps all languages and preserves Activity across ContextWrapper`() {
        assertEquals("en", LocaleHelper.languageTagForName("English"))
        assertEquals("hi", LocaleHelper.languageTagForName("Hindi"))
        assertEquals("te", LocaleHelper.languageTagForName("Telugu"))
        assertEquals("es", LocaleHelper.languageTagForName("Spanish"))
        assertEquals("fr", LocaleHelper.languageTagForName("French"))

        assertEquals("Hindi", LocaleHelper.languageNameForTag("hi"))
        assertEquals("Telugu", LocaleHelper.languageNameForTag("te-IN"))
        assertEquals("Spanish", LocaleHelper.languageNameForTag("es"))
        assertEquals("French", LocaleHelper.languageNameForTag("fr-FR"))
        assertEquals("English", LocaleHelper.languageNameForTag("en"))

        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()

        val enCtx = LocaleHelper.applyLocaleToContext(activity, "English")
        val hiCtx = LocaleHelper.applyLocaleToContext(activity, "Hindi")
        val frCtx = LocaleHelper.applyLocaleToContext(activity, "French")

        assertEquals("Settings", enCtx.getString(R.string.settings_title))
        assertTrue(hiCtx.getString(R.string.settings_title) != "Settings")
        assertTrue(frCtx.getString(R.string.settings_title) != "Settings")

        // Critical: wrapping for localization must NOT strip Activity resolution for CredentialManager / OAuth
        assertSame(activity, hiCtx.findActivity())
        assertSame(activity, frCtx.findActivity())

        // Restore default English locale for subsequent tests
        LocaleHelper.applyLocaleToContext(activity, "English")
        controller.destroy()
    }

    @Test
    fun `cropBitmapToAspectRatio enforces exact proportions for 1_1, 3_4, and 16_9`() {
        val portraitSource = Bitmap.createBitmap(1000, 1400, Bitmap.Config.ARGB_8888)

        val fullWide = MediaStoreSaver.cropBitmapToAspectRatio(portraitSource, CameraAspectRatio.FULL)
        assertSame(portraitSource, fullWide)
        assertEquals(1000, fullWide.width)
        assertEquals(1400, fullWide.height)

        val square = MediaStoreSaver.cropBitmapToAspectRatio(portraitSource, CameraAspectRatio.RATIO_1_1)
        assertEquals(1000, square.width)
        assertEquals(1000, square.height)

        val tall16x9 = MediaStoreSaver.cropBitmapToAspectRatio(portraitSource, CameraAspectRatio.RATIO_16_9)
        assertEquals(788, tall16x9.width)
        assertEquals(1400, tall16x9.height)

        val ratio4x3 = MediaStoreSaver.cropBitmapToAspectRatio(portraitSource, CameraAspectRatio.RATIO_4_3)
        assertEquals(1000, ratio4x3.width)
        assertEquals(1333, ratio4x3.height)

        if (square !== portraitSource) square.recycle()
        if (tall16x9 !== portraitSource) tall16x9.recycle()
        if (ratio4x3 !== portraitSource) ratio4x3.recycle()
        portraitSource.recycle()
    }

    @Test
    fun `PhotoStampRenderer renderStampCardOnly produces valid card bitmaps for every template`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val renderer = PhotoStampRenderer(context)
        val telemetry = sampleTelemetry()

        StampLayoutTemplate.entries.forEach { layout ->
            val config = StampConfig(
                layoutTemplate = layout,
                showTimeZone = true,
                showNumbering = true,
                photoSequenceNumber = 42,
                showPersonName = true,
                personName = "Surveyor 1",
                showNote = true,
                customNote = "Substation Check",
                showPressure = true,
                showWeather = true,
                showCameraIndicator = true
            )
            val cardBitmap = renderer.renderStampCardOnly(
                frameWidth = 1080,
                frameHeight = 1920,
                telemetry = telemetry,
                config = config,
                miniMapBitmap = null,
                isFrontCamera = false
            )
            assertTrue("Width too small for $layout", cardBitmap.width > 120)
            assertTrue("Height too small for $layout", cardBitmap.height > 60)
            cardBitmap.recycle()
        }
    }

    @Test
    fun `StampPreviewCard honors personName, note, numbering, and telemetry across all templates`() {
        val telemetry = sampleTelemetry()
        val templates = listOf(
            StampLayoutTemplate.SCAN_LOCATION,
            StampLayoutTemplate.DATETIME,
            StampLayoutTemplate.REPORTING,
            StampLayoutTemplate.NAVIGATIONAL_COMPASS
        )

        val activeLayout = androidx.compose.runtime.mutableStateOf(StampLayoutTemplate.SCAN_LOCATION)
        composeRule.setContent {
            val config = StampConfig(
                layoutTemplate = activeLayout.value,
                showNumbering = true,
                photoSequenceNumber = 7,
                showPersonName = true,
                personName = "AlexSurvey",
                showNote = true,
                customNote = "Gate #4",
                showAltitude = true,
                showCompassAndSpeed = true,
                showAccuracy = true,
                showTimeZone = true
            )
            YvGpsCamTheme {
                StampPreviewCard(
                    telemetry = telemetry,
                    config = config,
                    miniMapBitmap = null
                )
            }
        }

        templates.forEach { layout ->
            activeLayout.value = layout
            composeRule.waitForIdle()
            assertTrue(
                "Expected sequence #7 on $layout",
                composeRule.onAllNodesWithText("#7", substring = true).fetchSemanticsNodes().isNotEmpty()
            )
            assertTrue(
                "Expected person name on $layout",
                composeRule.onAllNodesWithText("AlexSurvey", substring = true).fetchSemanticsNodes().isNotEmpty()
            )
            assertTrue(
                "Expected note on $layout",
                composeRule.onAllNodesWithText("Gate #4", substring = true).fetchSemanticsNodes().isNotEmpty()
            )
            assertTrue(
                "Expected altitude metric on $layout",
                composeRule.onAllNodesWithText("512 m", substring = true).fetchSemanticsNodes().isNotEmpty()
            )
        }
    }

    @Test
    fun `SettingsScreen displays Maps API Status diagnostic row and GoogleMapsConfig validates keys`() {
        assertFalse(GoogleMapsConfig.isApiKeyConfigured(""))
        assertFalse(GoogleMapsConfig.isApiKeyConfigured("MY_MAPS_KEY_PLACEHOLDER"))
        assertFalse(GoogleMapsConfig.isApiKeyConfigured("null"))
        assertTrue(GoogleMapsConfig.isApiKeyConfigured("AIzaSyTestExampleKey12345"))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(context)
        val offlineRepo = OfflineMapRepository(context, db.offlineRegionDao())
        val vm = CameraViewModel(
            locationManager = LocationTelemetryManager(context),
            stampPrefs = StampPreferencesRepository(context),
            photoRepository = PhotoRepository(context, db.capturedPhotoDao()),
            miniMapRenderer = MiniMapTileRenderer(offlineRepo),
            mediaStoreSaver = MediaStoreSaver(context, PhotoStampRenderer(context))
        )

        composeRule.setContent {
            YvGpsCamTheme {
                SettingsScreen(
                    viewModel = vm,
                    onBack = {},
                    onOpenPdfReport = {},
                    onOpenImageOnMap = {},
                    onOpenOfflineMaps = {},
                    onOpenCloudSync = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_list").performScrollToIndex(5)
        composeRule.onNodeWithTag("settings_maps_status_row")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Map Engine Status").assertIsDisplayed()

        val statusStrings = listOf(
            context.getString(R.string.settings_maps_sdk_status),
            context.getString(R.string.settings_maps_sdk_active),
            context.getString(R.string.settings_maps_sdk_fallback)
        ).joinToString(" ")
        val forbiddenCredits = listOf("AI Studio", "aistudio", "CartoDB", "ArcGIS", "OSM", "GitHub", "Google Maps", "Google Play")
        forbiddenCredits.forEach { forbidden ->
            assertFalse(
                "Found forbidden third-party reference '$forbidden' in map status strings: $statusStrings",
                statusStrings.contains(forbidden, ignoreCase = true)
            )
        }
    }

    private fun sampleTelemetry() = GpsTelemetry(
        latitude = 17.385044,
        longitude = 78.486671,
        altitudeMeters = 512.0,
        accuracyMeters = 3.2f,
        speedKmh = 12f,
        compassAzimuth = 135f,
        compassCardinal = "SE",
        magneticFieldMicroTesla = 46f,
        pressureHpa = 1009f,
        weatherTempCelsius = 28f,
        weatherHumidityPercent = 54,
        addressLine = "Plot 12, Jubilee Hills",
        cityState = "Hyderabad",
        country = "India",
        countryCode = "IN",
        signalState = GpsSignalState.LOCKED
    )
}
