package `in`.yourview.yvgpscam

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import `in`.yourview.yvgpscam.data.local.AppDatabase
import `in`.yourview.yvgpscam.data.local.PhotoRepository
import `in`.yourview.yvgpscam.data.local.StampPreferencesRepository
import `in`.yourview.yvgpscam.camera.MediaStoreSaver
import `in`.yourview.yvgpscam.camera.PhotoStampRenderer
import `in`.yourview.yvgpscam.map.MiniMapTileRenderer
import `in`.yourview.yvgpscam.map.OfflineMapRepository
import `in`.yourview.yvgpscam.sensors.LocationTelemetryManager
import `in`.yourview.yvgpscam.ui.components.AppUpdateDialog
import `in`.yourview.yvgpscam.ui.screens.SettingsScreen
import `in`.yourview.yvgpscam.ui.theme.YvGpsCamTheme
import `in`.yourview.yvgpscam.ui.viewmodel.CameraViewModel
import `in`.yourview.yvgpscam.util.AppUpdateCoordinator
import `in`.yourview.yvgpscam.util.AppUpdateInfoState
import `in`.yourview.yvgpscam.util.AppUpdateReplacedReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppUpdateTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `evaluateManifestJson parses CI update-manifest and GitHub release assets and detects versionName bumps`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val coordinator = AppUpdateCoordinator(context)

        // 1. CI update-manifest.json format with apkUrl, notes, tag, releaseUrl
        val ciManifest = """
            {
              "versionCode": 42,
              "versionName": "1.1.42",
              "tag": "v1.1.42",
              "notes": "Fix CI build model & auto relaunch",
              "apkUrl": "https://github.com/yourview/yvgpscam/releases/download/v1.1.42/yvgpscam.apk",
              "releaseUrl": "https://github.com/yourview/yvgpscam/releases/tag/v1.1.42",
              "mandatory": false
            }
        """.trimIndent()
        val ciState = coordinator.evaluateManifestJson(
            manifestJson = ciManifest,
            currentVersionCode = 2,
            currentVersionName = "1.1.0",
            forceShow = true
        )
        assertNotNull(ciState)
        assertEquals(42, ciState!!.availableVersionCode)
        assertEquals("1.1.42", ciState.availableVersionLabel)
        assertEquals("Fix CI build model & auto relaunch", ciState.releaseNotes)
        assertEquals("https://github.com/yourview/yvgpscam/releases/download/v1.1.42/yvgpscam.apk", ciState.storeUrl)

        // 2. GitHub Releases API response with assets[].browser_download_url
        val ghReleaseApi = """
            {
              "tag_name": "v1.2.5",
              "body": "Direct APK asset attached",
              "html_url": "https://github.com/yourview/yvgpscam/releases/tag/v1.2.5",
              "assets": [
                { "name": "checksums.txt", "browser_download_url": "https://example.com/checksums.txt" },
                { "name": "app-release.apk", "browser_download_url": "https://example.com/app-release.apk" }
              ]
            }
        """.trimIndent()
        val ghState = coordinator.evaluateManifestJson(
            manifestJson = ghReleaseApi,
            currentVersionCode = 2,
            currentVersionName = "1.1.0",
            forceShow = true
        )
        assertNotNull(ghState)
        assertEquals("https://example.com/app-release.apk", ghState!!.storeUrl)

        // 3. VersionName-only bump at same versionCode still triggers update prompt
        val nameBumpOnly = """
            {
              "versionCode": 2,
              "versionName": "1.1.1",
              "releaseNotes": "Hotfix build"
            }
        """.trimIndent()
        val bumpedState = coordinator.evaluateManifestJson(
            manifestJson = nameBumpOnly,
            currentVersionCode = 2,
            currentVersionName = "1.1.0",
            forceShow = true
        )
        assertNotNull(bumpedState)
        assertEquals("1.1.1", bumpedState!!.availableVersionLabel)

        // Exact same code and name does not trigger update popup
        val identical = coordinator.evaluateManifestJson(
            manifestJson = """{"versionCode":2,"versionName":"1.1.0"}""",
            currentVersionCode = 2,
            currentVersionName = "1.1.0",
            forceShow = true
        )
        assertNull(identical)

        coordinator.dispose()
    }

    @Test
    fun `resolveInstalledBuildInfo and post-update banner tracking work accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = AppUpdateCoordinator.resolveInstalledBuildInfo(context)
        assertTrue(info.versionCode > 0)
        assertTrue(info.versionName.isNotBlank())
        assertTrue(info.deviceModel.isNotBlank())
        assertEquals(context.packageName, info.packageName)

        val coordinator = AppUpdateCoordinator(context)
        val prefs = context.getSharedPreferences("yvgpscam_updates", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("pending_auto_relaunch", true).apply()

        val banner = coordinator.checkAndRecordInstalledVersionChange(openedFromReceiver = true)
        assertNotNull(banner)
        assertTrue(banner!!.contains(info.versionName))
        assertTrue(banner.contains("Build ${info.versionCode}"))
        assertEquals(banner, coordinator.postUpdateBanner.value)
        assertFalse(prefs.getBoolean("pending_auto_relaunch", true))

        coordinator.setAutoOpenAfterUpdateEnabled(false)
        assertFalse(coordinator.autoOpenAfterUpdateEnabled.value)
        coordinator.setAutoOpenAfterUpdateEnabled(true)
        assertTrue(coordinator.autoOpenAfterUpdateEnabled.value)

        coordinator.clearPostUpdateBanner()
        assertNull(coordinator.postUpdateBanner.value)
        coordinator.dispose()
    }

    @Test
    fun `AppUpdateReplacedReceiver handles MY_PACKAGE_REPLACED and clears pending relaunch`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("yvgpscam_updates", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("auto_open_after_update", true)
            .putBoolean("pending_auto_relaunch", true)
            .apply()

        val receiver = AppUpdateReplacedReceiver()
        receiver.onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        assertFalse(prefs.getBoolean("pending_auto_relaunch", true))
        val expected = AppUpdateCoordinator.resolveInstalledBuildInfo(context)
        assertEquals(expected.versionCode, prefs.getInt("last_seen_version_code", -1))
        assertEquals(expected.versionName, prefs.getString("last_seen_version_name", ""))
    }

    @Test
    fun `SettingsScreen shows live build model and auto-open toggle and AppUpdateDialog renders progress`() {
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
        val installed = AppUpdateCoordinator.resolveInstalledBuildInfo(context)

        composeRule.setContent {
            YvGpsCamTheme {
                SettingsScreen(
                    viewModel = vm,
                    onBack = {},
                    onOpenPdfReport = {},
                    onOpenImageOnMap = {},
                    onOpenOfflineMaps = {},
                    onOpenCloudSync = {},
                    autoPushUpdatesEnabled = true,
                    autoOpenAfterUpdateEnabled = true
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_list").performScrollToIndex(5)
        composeRule.onNodeWithTag("settings_auto_open_after_update_switch")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("settings_check_updates_row")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            "Installed v${installed.versionName} (Build ${installed.versionCode}) • ${installed.deviceModel}"
        ).assertIsDisplayed()
    }

    @Test
    fun `AppUpdateDialog shows live download progress and version details`() {
        composeRule.setContent {
            YvGpsCamTheme {
                AppUpdateDialog(
                    updateState = AppUpdateInfoState(
                        currentVersionName = "1.1.0",
                        currentVersionCode = 2,
                        availableVersionCode = 15,
                        availableVersionLabel = "1.1.15",
                        isDownloading = true,
                        downloadProgressPercent = 64,
                        statusDetail = "Downloading update (64%)..."
                    ),
                    onUpdateNow = {},
                    onRestartToInstall = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("update_popup_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("v1.1.0 (B2)").assertIsDisplayed()
        composeRule.onNodeWithText("1.1.15 (B15)").assertIsDisplayed()
        composeRule.onNodeWithText("Downloading update (64%)...").assertIsDisplayed()
    }
}
