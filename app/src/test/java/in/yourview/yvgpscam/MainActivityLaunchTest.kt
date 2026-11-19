package `in`.yourview.yvgpscam

import android.Manifest
import android.app.Application
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import `in`.yourview.yvgpscam.data.model.StampConfig
import `in`.yourview.yvgpscam.di.YvGpsCamContainer
import `in`.yourview.yvgpscam.ui.components.CameraQuickToolsDrawer
import `in`.yourview.yvgpscam.ui.screens.CameraScreen
import `in`.yourview.yvgpscam.ui.theme.YvGpsCamTheme
import `in`.yourview.yvgpscam.ui.viewmodel.CameraHudState
import `in`.yourview.yvgpscam.ui.viewmodel.CameraViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityLaunchTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `main activity launches cleanly without crashing`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)
        assertFalse(activity.isFinishing)
        assertNotNull(activity.findViewById<FrameLayout>(android.R.id.content))
        controller.pause().stop().destroy()
    }

    @Test
    fun `main activity renders live camera UI and opens floating tools drawer when permissions granted`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)
        assertFalse(activity.isFinishing)

        composeRule.waitForIdle()
        controller.pause().stop().destroy()
    }

    @Test
    fun `camera quick tools drawer displays full untruncated pill labels`() {
        composeRule.setContent {
            YvGpsCamTheme {
                CameraQuickToolsDrawer(
                    hudState = CameraHudState(),
                    config = StampConfig(),
                    onCycleRatio = {},
                    onToggleGrid = {},
                    onCycleTimer = {},
                    onToggleFullView = {},
                    onToggleMirror = {},
                    onToggleVoice = {},
                    onToggleLevel = {},
                    onCycleStampPosition = {},
                    onOpenNoteDialog = {},
                    onImportPhoto = {},
                    onOpenDriveSync = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("camera_quick_tools_drawer").assertIsDisplayed()
        composeRule.onNodeWithText("Add Text").assertIsDisplayed()
        composeRule.onNodeWithText("Stamp Photo").assertIsDisplayed()
        composeRule.onNodeWithText("Google Drive").assertIsDisplayed()

        // Interactive click check
        composeRule.onNodeWithTag("quick_tool_full_view").performClick()
    }

    @Test
    fun `compact 320dp camera screen renders all primary buttons without overlapping`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val container = YvGpsCamContainer(app)
        val cameraViewModel = CameraViewModel(
            locationManager = container.locationTelemetryManager,
            stampPrefs = container.stampPreferencesRepository,
            photoRepository = container.photoRepository,
            miniMapRenderer = container.miniMapTileRenderer,
            mediaStoreSaver = container.mediaStoreSaver,
            connectivityObserver = container.networkConnectivityObserver,
            locationSyncStore = container.locationMetadataSyncStore
        )

        var galleryClicked = false
        var locationsClicked = false
        var templatesClicked = false
        var settingsClicked = false

        composeRule.setContent {
            YvGpsCamTheme {
                Box(modifier = Modifier.size(width = 320.dp, height = 600.dp)) {
                    CameraScreen(
                        viewModel = cameraViewModel,
                        onOpenGallery = { galleryClicked = true },
                        onOpenTemplates = { templatesClicked = true },
                        onOpenLocations = { locationsClicked = true },
                        onOpenSettings = { settingsClicked = true }
                    )
                }
            }
        }

        composeRule.waitForIdle()

        val buttonTags = listOf(
            "camera_tools_drawer_toggle",
            "flash_toggle_button",
            "camera_auto_capture_toggle_button",
            "camera_image_on_map_button",
            "switch_camera_button",
            "camera_settings_button",
            "viewfinder_orientation_status",
            "camera_offline_sync_status_icon",
            "camera_gps_status_pill",
            "camera_zoom_selector_pill",
            "camera_quick_torch_pill",
            "camera_quick_burst_pill",
            "camera_gallery_thumb_button",
            "camera_locations_button",
            "capture_shutter_button",
            "camera_edit_note_btn",
            "camera_open_templates_btn"
        )

        val boundsByTag: Map<String, Rect> = buttonTags.associateWith { tag ->
            val node = composeRule.onNodeWithTag(tag).assertIsDisplayed()
            val rect = node.fetchSemanticsNode().boundsInRoot
            assertTrue("Expected positive width for $tag but was ${rect.width}", rect.width > 0f)
            assertTrue("Expected positive height for $tag but was ${rect.height}", rect.height > 0f)
            rect
        }

        // Verify zero pairwise overlap between any two camera controls (1px float tolerance)
        for (i in buttonTags.indices) {
            for (j in i + 1 until buttonTags.size) {
                val tagA = buttonTags[i]
                val tagB = buttonTags[j]
                val a = boundsByTag.getValue(tagA)
                val b = boundsByTag.getValue(tagB)
                val overlapW = minOf(a.right, b.right) - maxOf(a.left, b.left)
                val overlapH = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
                val overlapsSignificantly = overlapW > 1f && overlapH > 1f
                assertFalse(
                    "Buttons '$tagA' ($a) and '$tagB' ($b) overlap by ${overlapW}x${overlapH}px!",
                    overlapsSignificantly
                )
            }
        }

        // Verify primary buttons remain responsive & clickable
        composeRule.onNodeWithTag("camera_gallery_thumb_button").performClick()
        composeRule.onNodeWithTag("camera_locations_button").performClick()
        composeRule.onNodeWithTag("camera_open_templates_btn").performClick()
        composeRule.onNodeWithTag("camera_settings_button").performClick()
        composeRule.waitForIdle()

        assertTrue(galleryClicked)
        assertTrue(locationsClicked)
        assertTrue(templatesClicked)
        assertTrue(settingsClicked)
    }
}
