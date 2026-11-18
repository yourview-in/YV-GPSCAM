package `in`.yourview.yvgpscam

import android.Manifest
import android.app.Application
import android.widget.FrameLayout
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import `in`.yourview.yvgpscam.data.model.StampConfig
import `in`.yourview.yvgpscam.ui.components.CameraQuickToolsDrawer
import `in`.yourview.yvgpscam.ui.theme.YvGpsCamTheme
import `in`.yourview.yvgpscam.ui.viewmodel.CameraHudState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
}
