package `in`.yourview.yvgpscam.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Videocam
import androidx.core.content.ContextCompat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.yourview.yvgpscam.BuildConfig
import `in`.yourview.yvgpscam.R
import `in`.yourview.yvgpscam.data.model.AppThemeMode
import `in`.yourview.yvgpscam.data.model.PhotoQualityLevel
import `in`.yourview.yvgpscam.data.model.ScreenTapAction
import `in`.yourview.yvgpscam.data.model.StampOrientationMode
import `in`.yourview.yvgpscam.data.model.VideoQualityLevel
import `in`.yourview.yvgpscam.data.model.VolumeButtonAction
import `in`.yourview.yvgpscam.map.GoogleMapsConfig
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import `in`.yourview.yvgpscam.ui.components.AdBannerSlot
import `in`.yourview.yvgpscam.ui.components.ModernGlassCircleButton
import `in`.yourview.yvgpscam.ui.theme.ShutterGold
import `in`.yourview.yvgpscam.ui.theme.TelemetryEmerald
import `in`.yourview.yvgpscam.ui.viewmodel.CameraViewModel
import `in`.yourview.yvgpscam.util.AppUpdateCoordinator
import `in`.yourview.yvgpscam.util.AppUpdateInfoState
import `in`.yourview.yvgpscam.util.LocaleHelper

private enum class SettingsChoiceDialog {
    NONE,
    LANGUAGE,
    THEME,
    PHOTO_QUALITY,
    VIDEO_QUALITY,
    VOLUME_BUTTON,
    SCREEN_TAP,
    STAMP_ORIENTATION,
    EMAIL_ADDRESS,
    UPDATE_FEED_URL,
    ABOUT_APP
}

@Composable
fun SettingsScreen(
    viewModel: CameraViewModel,
    onBack: () -> Unit,
    onOpenPdfReport: () -> Unit,
    onOpenImageOnMap: () -> Unit,
    onOpenOfflineMaps: () -> Unit,
    onOpenCloudSync: () -> Unit,
    autoPushUpdatesEnabled: Boolean = true,
    autoOpenAfterUpdateEnabled: Boolean = true,
    manifestFeedUrl: String = "",
    updateState: AppUpdateInfoState? = null,
    onToggleAutoPushUpdates: (Boolean) -> Unit = {},
    onToggleAutoOpenAfterUpdate: (Boolean) -> Unit = {},
    onUpdateManifestFeedUrl: (String) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val installedBuild = remember(context) { AppUpdateCoordinator.resolveInstalledBuildInfo(context) }
    val config by viewModel.stampConfig.collectAsStateWithLifecycle()
    var activeDialog by remember { mutableStateOf(SettingsChoiceDialog.NONE) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onToggleAutoPushUpdates(granted)
        if (!granted) {
            Toast.makeText(
                context,
                "Notification permission denied. In-app update pop-ups will still appear on launch.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header: < Settings
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModernGlassCircleButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.settings_back),
                inactiveIconTint = MaterialTheme.colorScheme.onBackground,
                onClick = onBack,
                testTag = "settings_back_button"
            )
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 48.dp)
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .testTag("settings_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // GENERAL
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_general))
                SettingsCardContainer {
                    SettingsNavigationRow(
                        icon = Icons.Default.Language,
                        title = stringResource(R.string.settings_language),
                        subtitle = config.appLanguage,
                        onClick = { activeDialog = SettingsChoiceDialog.LANGUAGE },
                        testTag = "settings_language_row"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsNavigationRow(
                        icon = Icons.Default.Palette,
                        title = stringResource(R.string.settings_app_theme),
                        subtitle = config.appThemeMode.displayName,
                        onClick = { activeDialog = SettingsChoiceDialog.THEME },
                        testTag = "settings_theme_row"
                    )
                }
            }

            // CAMERA
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_camera))
                SettingsCardContainer {
                    SettingsNavigationRow(
                        icon = Icons.Default.HighQuality,
                        title = stringResource(R.string.settings_photo_quality),
                        subtitle = config.photoQuality.displayName,
                        onClick = { activeDialog = SettingsChoiceDialog.PHOTO_QUALITY },
                        testTag = "settings_photo_quality_row"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsNavigationRow(
                        icon = Icons.Default.Videocam,
                        title = stringResource(R.string.settings_video_quality),
                        subtitle = config.videoQuality.displayName,
                        onClick = { activeDialog = SettingsChoiceDialog.VIDEO_QUALITY },
                        testTag = "settings_video_quality_row"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsNavigationRow(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        title = stringResource(R.string.settings_volume_button),
                        subtitle = config.volumeButtonAction.displayName,
                        onClick = { activeDialog = SettingsChoiceDialog.VOLUME_BUTTON },
                        testTag = "settings_volume_button_row"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsNavigationRow(
                        icon = Icons.Default.TouchApp,
                        title = stringResource(R.string.settings_screen_tap),
                        subtitle = config.screenTapAction.displayName,
                        onClick = { activeDialog = SettingsChoiceDialog.SCREEN_TAP },
                        testTag = "settings_screen_tap_row"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsSwitchRow(
                        icon = Icons.Default.QrCodeScanner,
                        title = stringResource(R.string.settings_qr_detection),
                        subtitle = stringResource(R.string.settings_qr_detection_sub),
                        checked = config.qrDetectionEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.updateStampConfig { it.copy(qrDetectionEnabled = enabled) }
                        },
                        testTag = "settings_qr_detection_switch"
                    )
                }
            }

            // STAMP
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_stamp))
                SettingsCardContainer {
                    SettingsNavigationRow(
                        icon = Icons.Default.ScreenRotation,
                        title = stringResource(R.string.settings_stamp_orientation),
                        subtitle = config.stampOrientation.displayName,
                        onClick = { activeDialog = SettingsChoiceDialog.STAMP_ORIENTATION },
                        testTag = "settings_stamp_orientation_row"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // Locked YVGPSCAM Brand Logo row (non-removable)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Toast.makeText(
                                    context,
                                    "YVGPSCAM brand logo is permanently enabled on stamped media.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("settings_brand_logo_locked_row"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_brand_yv_logo),
                            contentDescription = "YVGPSCAM brand logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .border(1.dp, ShutterGold, RoundedCornerShape(7.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_show_app_logo),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Required brand logo",
                                    tint = ShutterGold,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = stringResource(R.string.settings_show_app_logo_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            color = ShutterGold.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "ON",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ShutterGold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsSwitchRow(
                        icon = Icons.Default.CameraAlt,
                        title = stringResource(R.string.settings_show_camera_indicator),
                        subtitle = stringResource(R.string.settings_show_camera_indicator_sub),
                        checked = config.showCameraIndicator,
                        onCheckedChange = { enabled ->
                            viewModel.updateStampConfig { it.copy(showCameraIndicator = enabled) }
                        },
                        testTag = "settings_camera_indicator_switch"
                    )
                }
            }

            // STORAGE
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_storage))
                SettingsCardContainer {
                    SettingsSwitchRow(
                        icon = Icons.Default.SaveAlt,
                        title = stringResource(R.string.settings_auto_save_original),
                        subtitle = stringResource(R.string.settings_auto_save_original_sub),
                        checked = config.autoSaveOriginalPhoto,
                        onCheckedChange = { enabled ->
                            viewModel.updateStampConfig { it.copy(autoSaveOriginalPhoto = enabled) }
                        },
                        testTag = "settings_auto_save_original_switch"
                    )
                }
            }

            // REPORT AFTER CAPTURE
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_report))
                SettingsCardContainer {
                    SettingsSwitchRow(
                        icon = Icons.Default.Email,
                        title = stringResource(R.string.settings_email_photo),
                        subtitle = if (config.defaultReportEmail.isNotBlank()) {
                            "Send to ${config.defaultReportEmail}"
                        } else {
                            "Open email composer after capturing"
                        },
                        checked = config.emailAfterCapture,
                        onCheckedChange = { enabled ->
                            viewModel.updateStampConfig { it.copy(emailAfterCapture = enabled) }
                        },
                        testTag = "settings_email_photo_switch"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsNavigationRow(
                        icon = Icons.Default.Description,
                        title = stringResource(R.string.settings_default_report_email),
                        subtitle = config.defaultReportEmail.ifBlank { "Tap to set recipient email" },
                        onClick = { activeDialog = SettingsChoiceDialog.EMAIL_ADDRESS },
                        testTag = "settings_report_email_row"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsNavigationRow(
                        icon = Icons.Default.PictureAsPdf,
                        title = stringResource(R.string.settings_pdf_report_generator),
                        subtitle = stringResource(R.string.settings_pdf_report_sub),
                        onClick = onOpenPdfReport,
                        testTag = "settings_pdf_report_row"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsSwitchRow(
                        icon = Icons.Default.Share,
                        title = stringResource(R.string.settings_share_photo),
                        subtitle = stringResource(R.string.settings_share_photo_sub),
                        checked = config.autoShareAfterCapture,
                        onCheckedChange = { enabled ->
                            viewModel.updateStampConfig { it.copy(autoShareAfterCapture = enabled) }
                        },
                        testTag = "settings_auto_share_switch"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsSwitchRow(
                        icon = Icons.Default.PictureAsPdf,
                        title = stringResource(R.string.settings_reports_mode),
                        subtitle = stringResource(R.string.settings_reports_mode_sub),
                        checked = config.showReportsModeOnCamera,
                        onCheckedChange = { enabled ->
                            viewModel.updateStampConfig { it.copy(showReportsModeOnCamera = enabled) }
                        },
                        testTag = "settings_reports_mode_switch"
                    )
                }
            }

            // HELP & SUPPORT
            item {
                val mapsConfigured = remember { GoogleMapsConfig.isApiKeyConfigured() }
                SettingsSectionHeader(stringResource(R.string.settings_section_help))
                SettingsCardContainer {
                    SettingsNavigationRow(
                        icon = Icons.Default.Map,
                        title = stringResource(R.string.settings_image_on_map),
                        subtitle = stringResource(R.string.settings_image_on_map_sub),
                        onClick = onOpenImageOnMap,
                        testTag = "settings_image_on_map_row"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsNavigationRow(
                        icon = Icons.Default.OfflinePin,
                        title = stringResource(R.string.settings_offline_maps),
                        subtitle = stringResource(R.string.settings_offline_maps_sub),
                        onClick = onOpenOfflineMaps,
                        testTag = "settings_offline_maps_row"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsNavigationRow(
                        icon = Icons.Default.CloudUpload,
                        title = stringResource(R.string.settings_cloud_sync),
                        subtitle = stringResource(R.string.settings_cloud_sync_sub),
                        onClick = onOpenCloudSync,
                        testTag = "settings_cloud_sync_row"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsNavigationRow(
                        icon = Icons.Default.Map,
                        title = stringResource(R.string.settings_maps_sdk_status),
                        subtitle = stringResource(
                            if (mapsConfigured) R.string.settings_maps_sdk_active else R.string.settings_maps_sdk_fallback
                        ),
                        onClick = {
                            Toast.makeText(
                                context,
                                if (mapsConfigured) {
                                    "Google Maps SDK key is active via BuildConfig.MAPS_API_KEY."
                                } else {
                                    "Add MAPS_API_KEY in AI Studio Secrets or GitHub CI secrets to enable interactive Google Maps tiles."
                                },
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        testTag = "settings_maps_status_row"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsSwitchRow(
                        icon = Icons.Default.NotificationsActive,
                        title = stringResource(R.string.settings_auto_push_updates),
                        subtitle = stringResource(R.string.settings_auto_push_updates_sub),
                        checked = autoPushUpdatesEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onToggleAutoPushUpdates(enabled)
                            }
                        },
                        testTag = "settings_auto_push_updates_switch"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsSwitchRow(
                        icon = Icons.Default.SystemUpdate,
                        title = stringResource(R.string.settings_auto_open_update),
                        subtitle = stringResource(R.string.settings_auto_open_update_sub),
                        checked = autoOpenAfterUpdateEnabled,
                        onCheckedChange = onToggleAutoOpenAfterUpdate,
                        testTag = "settings_auto_open_after_update_switch"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsNavigationRow(
                        icon = Icons.Default.Link,
                        title = stringResource(R.string.settings_update_feed_url),
                        subtitle = manifestFeedUrl.ifBlank { "Uses Google Play Store (tap to add custom APK feed)" },
                        onClick = { activeDialog = SettingsChoiceDialog.UPDATE_FEED_URL },
                        testTag = "settings_update_feed_url_row"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsNavigationRow(
                        icon = Icons.Default.SystemUpdate,
                        title = stringResource(R.string.settings_check_updates),
                        subtitle = when {
                            updateState?.isDownloading == true ->
                                updateState.statusDetail ?: "Downloading update..."
                            updateState != null ->
                                "Update available: ${updateState.availableVersionLabel} (Tap to install)"
                            else ->
                                "Installed v${installedBuild.versionName} (Build ${installedBuild.versionCode}) • ${installedBuild.deviceModel}"
                        },
                        onClick = onCheckForUpdates,
                        testTag = "settings_check_updates_row"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsNavigationRow(
                        icon = Icons.Default.Info,
                        title = stringResource(R.string.settings_about_app),
                        subtitle = "v${installedBuild.versionName} (Build ${installedBuild.versionCode}) • ${installedBuild.deviceModel}",
                        onClick = { activeDialog = SettingsChoiceDialog.ABOUT_APP },
                        testTag = "settings_about_row"
                    )
                }
            }
        }

        AdBannerSlot(placementId = "settings")
    }

    // Selection Dialogs
    when (activeDialog) {
        SettingsChoiceDialog.LANGUAGE -> {
            SingleChoiceSettingsDialog(
                title = stringResource(R.string.settings_language),
                options = LocaleHelper.SUPPORTED_LANGUAGES,
                selected = config.appLanguage,
                labelMapper = { it },
                onSelect = { lang ->
                    LocaleHelper.syncSystemPerAppLocale(context, lang)
                    viewModel.updateStampConfig { it.copy(appLanguage = lang) }
                    activeDialog = SettingsChoiceDialog.NONE
                },
                onDismiss = { activeDialog = SettingsChoiceDialog.NONE }
            )
        }

        SettingsChoiceDialog.THEME -> {
            SingleChoiceSettingsDialog(
                title = "App Theme",
                options = AppThemeMode.entries,
                selected = config.appThemeMode,
                labelMapper = { it.displayName },
                onSelect = { mode ->
                    viewModel.updateStampConfig { it.copy(appThemeMode = mode) }
                    activeDialog = SettingsChoiceDialog.NONE
                },
                onDismiss = { activeDialog = SettingsChoiceDialog.NONE }
            )
        }

        SettingsChoiceDialog.PHOTO_QUALITY -> {
            SingleChoiceSettingsDialog(
                title = "Photo Quality",
                options = PhotoQualityLevel.entries,
                selected = config.photoQuality,
                labelMapper = { it.displayName },
                onSelect = { quality ->
                    viewModel.updateStampConfig { it.copy(photoQuality = quality) }
                    activeDialog = SettingsChoiceDialog.NONE
                },
                onDismiss = { activeDialog = SettingsChoiceDialog.NONE }
            )
        }

        SettingsChoiceDialog.VIDEO_QUALITY -> {
            SingleChoiceSettingsDialog(
                title = "Video Quality",
                options = VideoQualityLevel.entries,
                selected = config.videoQuality,
                labelMapper = { it.displayName },
                onSelect = { quality ->
                    viewModel.updateStampConfig { it.copy(videoQuality = quality) }
                    activeDialog = SettingsChoiceDialog.NONE
                },
                onDismiss = { activeDialog = SettingsChoiceDialog.NONE }
            )
        }

        SettingsChoiceDialog.VOLUME_BUTTON -> {
            SingleChoiceSettingsDialog(
                title = "Volume Button Action",
                options = VolumeButtonAction.entries,
                selected = config.volumeButtonAction,
                labelMapper = { it.displayName },
                onSelect = { action ->
                    viewModel.updateStampConfig { it.copy(volumeButtonAction = action) }
                    activeDialog = SettingsChoiceDialog.NONE
                },
                onDismiss = { activeDialog = SettingsChoiceDialog.NONE }
            )
        }

        SettingsChoiceDialog.SCREEN_TAP -> {
            SingleChoiceSettingsDialog(
                title = "Screen Tap Action",
                options = ScreenTapAction.entries,
                selected = config.screenTapAction,
                labelMapper = { it.displayName },
                onSelect = { action ->
                    viewModel.updateStampConfig { it.copy(screenTapAction = action) }
                    activeDialog = SettingsChoiceDialog.NONE
                },
                onDismiss = { activeDialog = SettingsChoiceDialog.NONE }
            )
        }

        SettingsChoiceDialog.STAMP_ORIENTATION -> {
            SingleChoiceSettingsDialog(
                title = "Stamp Orientation",
                options = StampOrientationMode.entries,
                selected = config.stampOrientation,
                labelMapper = { it.displayName },
                onSelect = { orientation ->
                    viewModel.updateStampConfig { it.copy(stampOrientation = orientation) }
                    activeDialog = SettingsChoiceDialog.NONE
                },
                onDismiss = { activeDialog = SettingsChoiceDialog.NONE }
            )
        }

        SettingsChoiceDialog.EMAIL_ADDRESS -> {
            var emailDraft by remember(config.defaultReportEmail) {
                mutableStateOf(config.defaultReportEmail)
            }
            AlertDialog(
                onDismissRequest = { activeDialog = SettingsChoiceDialog.NONE },
                title = { Text("Default Report Email") },
                text = {
                    OutlinedTextField(
                        value = emailDraft,
                        onValueChange = { emailDraft = it.trim() },
                        label = { Text("Recipient Email") },
                        placeholder = { Text("reports@company.com") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_email_input")
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateStampConfig { it.copy(defaultReportEmail = emailDraft) }
                            activeDialog = SettingsChoiceDialog.NONE
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ShutterGold,
                            contentColor = Color(0xFF141619)
                        )
                    ) {
                        Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activeDialog = SettingsChoiceDialog.NONE }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        SettingsChoiceDialog.UPDATE_FEED_URL -> {
            var urlDraft by remember(manifestFeedUrl) { mutableStateOf(manifestFeedUrl) }
            AlertDialog(
                onDismissRequest = { activeDialog = SettingsChoiceDialog.NONE },
                title = { Text(stringResource(R.string.settings_update_feed_url)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Optional HTTPS JSON manifest or GitHub Releases API URL to auto-push updates to direct-APK users outside Google Play.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = urlDraft,
                            onValueChange = { urlDraft = it.trim() },
                            label = { Text("HTTPS Manifest URL") },
                            placeholder = { Text("https://example.com/yvgpscam-version.json") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_update_feed_input")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onUpdateManifestFeedUrl(urlDraft)
                            activeDialog = SettingsChoiceDialog.NONE
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ShutterGold,
                            contentColor = Color(0xFF141619)
                        )
                    ) {
                        Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activeDialog = SettingsChoiceDialog.NONE }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        SettingsChoiceDialog.ABOUT_APP -> {
            AlertDialog(
                onDismissRequest = { activeDialog = SettingsChoiceDialog.NONE },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_brand_yv_logo),
                            contentDescription = "YVGPSCAM Logo",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(9.dp))
                        )
                        Text("GPS Camera • YVGPSCAM")
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "GPS Camera (YVGPSCAM) captures verified geotagged photos and videos with live GPS coordinates, Plus Codes, Google Maps tiles, compass azimuth, horizon leveling, QR telemetry, and multi-photo PDF field reports.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = "App Version: v${installedBuild.versionName} (Build ${installedBuild.versionCode})\nPackage: ${installedBuild.packageName}\nBuild Model: ${installedBuild.deviceModel}\nAndroid OS: Android ${installedBuild.androidVersion} (${installedBuild.buildDisplay})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = SettingsChoiceDialog.NONE }) {
                        Text("Close", color = TelemetryEmerald, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        SettingsChoiceDialog.NONE -> Unit
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = ShutterGold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCardContainer(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ShutterGold.copy(alpha = 0.14f))
                .border(0.8.dp, ShutterGold.copy(alpha = 0.30f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ShutterGold,
                modifier = Modifier.size(19.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open $title",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val activeAccent = if (checked) TelemetryEmerald else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (checked) TelemetryEmerald.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant
                )
                .border(
                    width = 0.8.dp,
                    color = if (checked) TelemetryEmerald.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = activeAccent,
                modifier = Modifier.size(19.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF141619),
                checkedTrackColor = ShutterGold
            )
        )
    }
}

@Composable
private fun <T> SingleChoiceSettingsDialog(
    title: String,
    options: List<T>,
    selected: T,
    labelMapper: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { option ->
                    val isSelected = option == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(option) }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(option) },
                            colors = RadioButtonDefaults.colors(selectedColor = ShutterGold)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = labelMapper(option),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
