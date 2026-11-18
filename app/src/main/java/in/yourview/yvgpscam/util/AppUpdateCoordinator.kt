package `in`.yourview.yvgpscam.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import `in`.yourview.yvgpscam.BuildConfig
import `in`.yourview.yvgpscam.MainActivity
import `in`.yourview.yvgpscam.R
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

data class InstalledBuildInfo(
    val versionCode: Int,
    val versionName: String,
    val packageName: String,
    val deviceModel: String,
    val androidVersion: String,
    val buildDisplay: String
)

data class AppUpdateInfoState(
    val currentVersionName: String,
    val currentVersionCode: Int,
    val availableVersionCode: Int,
    val availableVersionLabel: String,
    val releaseNotes: String = DEFAULT_RELEASE_NOTES,
    val isImmediateRequired: Boolean = false,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgressPercent: Int? = null,
    val statusDetail: String? = null,
    val storeUrl: String? = null,
    val playAppUpdateInfo: AppUpdateInfo? = null
) {
    companion object {
        const val DEFAULT_RELEASE_NOTES =
            "Includes performance upgrades, sharper map rendering, offline GPS fixes, and latest camera tools."
    }
}

class AppUpdateCoordinator(
    private val appContext: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val appUpdateManager: AppUpdateManager? by lazy {
        runCatching { AppUpdateManagerFactory.create(appContext) }.getOrNull()
    }

    private val _updateState = MutableStateFlow<AppUpdateInfoState?>(null)
    val updateState: StateFlow<AppUpdateInfoState?> = _updateState.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _autoPushEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_PUSH_ENABLED, true))
    val autoPushEnabled: StateFlow<Boolean> = _autoPushEnabled.asStateFlow()

    private val _autoOpenAfterUpdateEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_OPEN_AFTER_UPDATE, true)
    )
    val autoOpenAfterUpdateEnabled: StateFlow<Boolean> = _autoOpenAfterUpdateEnabled.asStateFlow()

    private val _manifestFeedUrl = MutableStateFlow(
        prefs.getString(KEY_MANIFEST_FEED_URL, "").orEmpty()
            .ifBlank { BuildConfig.DEFAULT_UPDATE_MANIFEST_URL }
    )
    val manifestFeedUrl: StateFlow<String> = _manifestFeedUrl.asStateFlow()

    private val _postUpdateBanner = MutableStateFlow<String?>(null)
    val postUpdateBanner: StateFlow<String?> = _postUpdateBanner.asStateFlow()

    private var listenerRegistered = false
    private var lastAutoCheckMillis = 0L

    private val installStateListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING -> {
                val total = state.totalBytesToDownload().coerceAtLeast(1L)
                val pct = ((state.bytesDownloaded() * 100L) / total).toInt().coerceIn(0, 100)
                _updateState.value = _updateState.value?.copy(
                    isDownloading = true,
                    downloadProgressPercent = pct,
                    statusDetail = "Downloading update inside YVGPSCAM ($pct%)..."
                )
            }
            InstallStatus.DOWNLOADED -> {
                val updated = (_updateState.value ?: AppUpdateInfoState(
                    currentVersionName = BuildConfig.VERSION_NAME,
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    availableVersionCode = BuildConfig.VERSION_CODE + 1,
                    availableVersionLabel = "Latest",
                    isDownloaded = true
                )).copy(
                    isDownloading = false,
                    downloadProgressPercent = 100,
                    isDownloaded = true,
                    statusDetail = "Download complete — installing and restarting app..."
                )
                _updateState.value = updated
                if (_autoOpenAfterUpdateEnabled.value) {
                    prefs.edit().putBoolean(KEY_PENDING_AUTO_RELAUNCH, true).apply()
                    completeFlexibleUpdate()
                }
            }
            InstallStatus.INSTALLED -> {
                _updateState.value = null
                unregisterListenerIfNeeded()
            }
            InstallStatus.FAILED, InstallStatus.CANCELED -> {
                _updateState.value = _updateState.value?.copy(
                    isDownloading = false,
                    downloadProgressPercent = null,
                    statusDetail = "Update paused. Tap Update Now to retry."
                )
            }
            else -> Unit
        }
    }

    fun checkAndRecordInstalledVersionChange(openedFromReceiver: Boolean): String? {
        val installed = resolveInstalledBuildInfo(appContext)
        val prevCode = prefs.getInt(KEY_LAST_SEEN_VERSION_CODE, -1)
        val prevName = prefs.getString(KEY_LAST_SEEN_VERSION_NAME, "").orEmpty()
        val hasChanged = (prevCode != -1 && prevCode != installed.versionCode) ||
            (prevName.isNotBlank() && prevName != installed.versionName)

        prefs.edit()
            .putInt(KEY_LAST_SEEN_VERSION_CODE, installed.versionCode)
            .putString(KEY_LAST_SEEN_VERSION_NAME, installed.versionName)
            .putBoolean(KEY_PENDING_AUTO_RELAUNCH, false)
            .apply()

        if (openedFromReceiver || hasChanged) {
            val message = "YVGPSCAM updated to v${installed.versionName} (Build ${installed.versionCode})"
            _postUpdateBanner.value = message
            return message
        }
        return null
    }

    fun clearPostUpdateBanner() {
        _postUpdateBanner.value = null
    }

    fun setAutoPushEnabled(enabled: Boolean) {
        _autoPushEnabled.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_PUSH_ENABLED, enabled).apply()
        if (enabled) {
            schedulePeriodicUpdateChecks()
        } else {
            cancelPeriodicUpdateChecks()
        }
    }

    fun setAutoOpenAfterUpdateEnabled(enabled: Boolean) {
        _autoOpenAfterUpdateEnabled.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_OPEN_AFTER_UPDATE, enabled).apply()
    }

    fun setManifestFeedUrl(url: String) {
        val clean = url.trim()
        prefs.edit().putString(KEY_MANIFEST_FEED_URL, clean).apply()
        _manifestFeedUrl.value = clean.ifBlank { BuildConfig.DEFAULT_UPDATE_MANIFEST_URL }
        if (_manifestFeedUrl.value.startsWith("https://", ignoreCase = true)) {
            checkForUpdates(forceShowIfDismissed = true)
        }
    }

    fun schedulePeriodicUpdateChecks() {
        if (!_autoPushEnabled.value) return
        val scheduler = appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        val existing = scheduler.allPendingJobs.any { it.id == JOB_ID_PERIODIC_UPDATE }
        if (existing) return

        val component = ComponentName(appContext, AppUpdateCheckJobService::class.java)
        val jobInfo = JobInfo.Builder(JOB_ID_PERIODIC_UPDATE, component)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(TimeUnit.HOURS.toMillis(6))
            .setRequiresBatteryNotLow(true)
            .setPersisted(false)
            .build()
        runCatching { scheduler.schedule(jobInfo) }
    }

    fun cancelPeriodicUpdateChecks() {
        val scheduler = appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        runCatching { scheduler.cancel(JOB_ID_PERIODIC_UPDATE) }
    }

    fun checkForUpdates(
        forceShowIfDismissed: Boolean = false,
        fromResume: Boolean = false,
        pushNotificationIfFound: Boolean = false,
        onUpToDate: () -> Unit = {}
    ) {
        val now = System.currentTimeMillis()
        if (fromResume && !forceShowIfDismissed && (now - lastAutoCheckMillis) < AUTO_CHECK_THROTTLE_MS) {
            return
        }
        lastAutoCheckMillis = now
        _isChecking.value = true

        val installed = resolveInstalledBuildInfo(appContext)
        val manager = appUpdateManager
        if (manager == null) {
            checkRemoteManifestFallback(installed, forceShowIfDismissed, pushNotificationIfFound, onUpToDate)
            return
        }

        runCatching {
            manager.appUpdateInfo
                .addOnSuccessListener { info ->
                    if (info.installStatus() == InstallStatus.DOWNLOADED) {
                        _isChecking.value = false
                        val downloadedState = AppUpdateInfoState(
                            currentVersionName = installed.versionName,
                            currentVersionCode = installed.versionCode,
                            availableVersionCode = info.availableVersionCode(),
                            availableVersionLabel = "Build ${info.availableVersionCode()}",
                            isDownloaded = true,
                            statusDetail = "Download complete — ready to install & reopen app.",
                            playAppUpdateInfo = info
                        )
                        _updateState.value = downloadedState
                        if (pushNotificationIfFound) {
                            pushUpdateNotificationIfNeeded(downloadedState)
                        }
                        return@addOnSuccessListener
                    }

                    if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                        val availableCode = info.availableVersionCode()
                        val staleness = info.clientVersionStalenessDays() ?: 0
                        val immediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                        val flexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                        if (immediateAllowed || flexibleAllowed) {
                            _isChecking.value = false
                            val dismissedCode = prefs.getInt(KEY_DISMISSED_VERSION_CODE, -1)
                            val isMandatory = staleness >= MANDATORY_STALENESS_DAYS && immediateAllowed
                            val updateObj = AppUpdateInfoState(
                                currentVersionName = installed.versionName,
                                currentVersionCode = installed.versionCode,
                                availableVersionCode = availableCode,
                                availableVersionLabel = "Build $availableCode",
                                isImmediateRequired = isMandatory,
                                playAppUpdateInfo = info
                            )
                            if (forceShowIfDismissed || isMandatory || dismissedCode != availableCode) {
                                _updateState.value = updateObj
                            }
                            if (pushNotificationIfFound) {
                                pushUpdateNotificationIfNeeded(updateObj)
                            }
                            return@addOnSuccessListener
                        }
                    }

                    checkRemoteManifestFallback(installed, forceShowIfDismissed, pushNotificationIfFound, onUpToDate)
                }
                .addOnFailureListener {
                    checkRemoteManifestFallback(installed, forceShowIfDismissed, pushNotificationIfFound, onUpToDate)
                }
        }.onFailure {
            checkRemoteManifestFallback(installed, forceShowIfDismissed, pushNotificationIfFound, onUpToDate)
        }
    }

    private fun checkRemoteManifestFallback(
        installed: InstalledBuildInfo,
        forceShowIfDismissed: Boolean,
        pushNotificationIfFound: Boolean,
        onUpToDate: () -> Unit
    ) {
        val feedUrl = _manifestFeedUrl.value.trim()
        if (feedUrl.isBlank() || !feedUrl.startsWith("https://", ignoreCase = true)) {
            _isChecking.value = false
            if (forceShowIfDismissed) onUpToDate()
            return
        }

        scope.launch {
            val foundState = withContext(Dispatchers.IO) {
                runCatching {
                    val req = Request.Builder()
                        .url(feedUrl)
                        .header("Cache-Control", "no-cache")
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        val body = resp.body?.string().orEmpty()
                        if (body.isBlank()) return@use null
                        evaluateManifestJson(
                            manifestJson = body,
                            currentVersionCode = installed.versionCode,
                            currentVersionName = installed.versionName,
                            forceShow = forceShowIfDismissed
                        )
                    }
                }.getOrNull()
            }
            _isChecking.value = false
            if (foundState != null) {
                if (pushNotificationIfFound) {
                    pushUpdateNotificationIfNeeded(foundState)
                }
            } else if (forceShowIfDismissed) {
                onUpToDate()
            }
        }
    }

    fun onResumeCheck(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        val manager = appUpdateManager
        if (manager != null) {
            runCatching {
                manager.appUpdateInfo.addOnSuccessListener { info ->
                    val installed = resolveInstalledBuildInfo(appContext)
                    when {
                        info.installStatus() == InstallStatus.DOWNLOADED -> {
                            _updateState.value = (_updateState.value ?: AppUpdateInfoState(
                                currentVersionName = installed.versionName,
                                currentVersionCode = installed.versionCode,
                                availableVersionCode = info.availableVersionCode(),
                                availableVersionLabel = "Build ${info.availableVersionCode()}",
                                playAppUpdateInfo = info
                            )).copy(
                                isDownloaded = true,
                                isDownloading = false,
                                downloadProgressPercent = 100,
                                statusDetail = "Download complete — ready to install & reopen app.",
                                playAppUpdateInfo = info
                            )
                        }
                        info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                            runCatching {
                                manager.startUpdateFlowForResult(
                                    info,
                                    launcher,
                                    AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE)
                                )
                            }
                        }
                    }
                }
            }
        }
        if (_autoPushEnabled.value) {
            checkForUpdates(fromResume = true)
        }
    }

    fun startUpdate(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        val current = _updateState.value ?: return
        val manager = appUpdateManager
        val playInfo = current.playAppUpdateInfo

        if (current.isDownloaded && manager != null) {
            completeFlexibleUpdate()
            return
        }

        if (_autoOpenAfterUpdateEnabled.value) {
            prefs.edit().putBoolean(KEY_PENDING_AUTO_RELAUNCH, true).apply()
        }

        if (manager != null && playInfo != null) {
            // Prefer IMMEDIATE when allowed so Play handles in-app download, install & auto-restart seamlessly;
            // otherwise use FLEXIBLE with live progress and auto-complete on DOWNLOADED.
            val updateType = when {
                playInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> AppUpdateType.IMMEDIATE
                playInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> AppUpdateType.FLEXIBLE
                else -> AppUpdateType.IMMEDIATE
            }
            if (updateType == AppUpdateType.FLEXIBLE && !listenerRegistered) {
                manager.registerListener(installStateListener)
                listenerRegistered = true
            }
            val started = runCatching {
                manager.startUpdateFlowForResult(
                    playInfo,
                    launcher,
                    AppUpdateOptions.defaultOptions(updateType)
                )
            }.getOrDefault(false)
            if (started) {
                _updateState.value = current.copy(
                    isDownloading = true,
                    statusDetail = "Downloading and installing update in app..."
                )
                return
            }
        }

        val targetUrl = current.storeUrl?.trim().orEmpty()
        if (targetUrl.startsWith("https://", ignoreCase = true) && targetUrl.lowercase(Locale.US).contains(".apk")) {
            startInAppApkDownloadAndHandOff(activity, current, targetUrl)
        } else {
            openStoreListing(activity, current.storeUrl)
        }
    }

    private fun startInAppApkDownloadAndHandOff(
        activity: Activity,
        current: AppUpdateInfoState,
        apkUrl: String
    ) {
        if (current.isDownloading) return
        _updateState.value = current.copy(
            isDownloading = true,
            downloadProgressPercent = 0,
            statusDetail = "Downloading YVGPSCAM ${current.availableVersionLabel}..."
        )

        scope.launch {
            val cachedApk = withContext(Dispatchers.IO) {
                downloadApkWithProgress(apkUrl) { pct ->
                    scope.launch {
                        _updateState.value = _updateState.value?.copy(
                            isDownloading = true,
                            downloadProgressPercent = pct,
                            statusDetail = if (pct != null) {
                                "Downloading update ($pct%)..."
                            } else {
                                "Downloading update..."
                            }
                        )
                    }
                }
            }

            _updateState.value = _updateState.value?.copy(
                isDownloading = false,
                downloadProgressPercent = if (cachedApk != null) 100 else null,
                statusDetail = "Opening installer — YVGPSCAM will reopen after update finishes."
            )

            // Compliant handoff via ACTION_VIEW (no restricted REQUEST_INSTALL_PACKAGES permission)
            openStoreListing(activity, apkUrl)
        }
    }

    private fun downloadApkWithProgress(
        apkUrl: String,
        onProgress: (Int?) -> Unit
    ): File? = runCatching {
        val updatesDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        val outFile = File(updatesDir, "yvgpscam-update-preview.apk")
        val req = Request.Builder().url(apkUrl).build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching null
            val body = resp.body ?: return@runCatching null
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L
            var lastReportedPct = -1

            body.byteStream().use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0L) {
                            val pct = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            if (pct != lastReportedPct && (pct == 100 || pct - lastReportedPct >= 3)) {
                                lastReportedPct = pct
                                onProgress(pct)
                            }
                        } else if (lastReportedPct == -1) {
                            lastReportedPct = 0
                            onProgress(null)
                        }
                    }
                }
            }
            if (outFile.exists() && outFile.length() > 0L) outFile else null
        }
    }.getOrNull()

    fun completeFlexibleUpdate() {
        if (_autoOpenAfterUpdateEnabled.value) {
            prefs.edit().putBoolean(KEY_PENDING_AUTO_RELAUNCH, true).apply()
        }
        runCatching { appUpdateManager?.completeUpdate() }
        _updateState.value = null
        unregisterListenerIfNeeded()
    }

    fun dismissUpdateForNow() {
        val current = _updateState.value ?: return
        if (current.isImmediateRequired) return
        prefs.edit()
            .putInt(KEY_DISMISSED_VERSION_CODE, current.availableVersionCode)
            .putString(KEY_DISMISSED_VERSION_NAME, current.availableVersionLabel)
            .apply()
        _updateState.value = null
    }

    fun evaluateManifestJson(
        manifestJson: String,
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        currentVersionName: String = BuildConfig.VERSION_NAME,
        forceShow: Boolean = false
    ): AppUpdateInfoState? {
        return runCatching {
            val root = JSONObject(manifestJson)
            val explicitCode = root.optInt("versionCode", 0).takeIf { it > 0 }
                ?: root.optInt("buildNumber", 0)
            val tagName = root.optString("tag_name", "")
                .ifBlank { root.optString("tag", "") }
            val explicitName = root.optString("versionName", "")
                .ifBlank { tagName.removePrefix("v").removePrefix("V") }
                .trim()

            val remoteCode = if (explicitCode > 0) {
                explicitCode
            } else {
                parseVersionCodeFromTag(explicitName.ifBlank { tagName })
            }
            val remoteLabel = explicitName.ifBlank { "Build $remoteCode" }

            if (!shouldOfferUpdate(remoteCode, remoteLabel, currentVersionCode, currentVersionName)) {
                return null
            }

            val mandatory = root.optBoolean("mandatory", false)
            val dismissedCode = prefs.getInt(KEY_DISMISSED_VERSION_CODE, -1)
            val dismissedName = prefs.getString(KEY_DISMISSED_VERSION_NAME, "").orEmpty()
            val alreadyDismissed = (remoteCode > 0 && dismissedCode == remoteCode) &&
                (dismissedName.isBlank() || dismissedName == remoteLabel)
            if (!forceShow && !mandatory && alreadyDismissed) return null

            val notes = root.optString("releaseNotes", "")
                .ifBlank { root.optString("notes", "") }
                .ifBlank { root.optString("body", "") }
                .ifBlank { AppUpdateInfoState.DEFAULT_RELEASE_NOTES }

            val assetApkUrl = root.optJSONArray("assets")?.let { arr ->
                (0 until arr.length())
                    .mapNotNull { idx -> arr.optJSONObject(idx)?.optString("browser_download_url") }
                    .firstOrNull { url -> url.endsWith(".apk", ignoreCase = true) }
            }.orEmpty()

            val url = root.optString("apkUrl", "")
                .ifBlank { assetApkUrl }
                .ifBlank { root.optString("downloadUrl", "") }
                .ifBlank { root.optString("storeUrl", "") }
                .ifBlank { root.optString("releaseUrl", "") }
                .ifBlank { root.optString("html_url", "") }
                .ifBlank { null }

            val state = AppUpdateInfoState(
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode,
                availableVersionCode = remoteCode,
                availableVersionLabel = remoteLabel,
                releaseNotes = notes,
                isImmediateRequired = mandatory,
                storeUrl = url
            )
            _updateState.value = state
            state
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    fun pushUpdateNotificationIfNeeded(state: AppUpdateInfoState) {
        if (!_autoPushEnabled.value) return
        val lastNotified = prefs.getInt(KEY_LAST_NOTIFIED_VERSION_CODE, -1)
        if (lastNotified == state.availableVersionCode) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureNotificationChannel()

        val tapIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_UPDATE_POPUP, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            UPDATE_NOTIFICATION_ID,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (state.isDownloaded) {
            "YVGPSCAM ${state.availableVersionLabel} Ready to Install"
        } else {
            "New YVGPSCAM Update (${state.availableVersionLabel})"
        }
        val body = if (state.isDownloaded) {
            "Tap to restart YVGPSCAM and finish installing the update."
        } else {
            state.releaseNotes
        }

        val notification = NotificationCompat.Builder(appContext, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_yvgpscam)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(appContext).notify(UPDATE_NOTIFICATION_ID, notification)
            prefs.edit().putInt(KEY_LAST_NOTIFIED_VERSION_CODE, state.availableVersionCode).apply()
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = manager.getNotificationChannel(UPDATE_CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    UPDATE_CHANNEL_ID,
                    "App Updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when a new YVGPSCAM release is available to install."
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun openStoreListing(context: Context = appContext, customUrl: String? = null) {
        val targetUrl = customUrl?.takeIf { it.isNotBlank() }
        val playMarketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(targetUrl ?: "market://details?id=${appContext.packageName}")
        ).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching { context.startActivity(playMarketIntent) }.isSuccess
        if (!launched) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(targetUrl ?: "https://play.google.com/store/apps/details?id=${appContext.packageName}")
            ).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(webIntent) }
        }
    }

    fun dispose() {
        unregisterListenerIfNeeded()
    }

    private fun unregisterListenerIfNeeded() {
        if (listenerRegistered) {
            runCatching { appUpdateManager?.unregisterListener(installStateListener) }
            listenerRegistered = false
        }
    }

    companion object {
        internal const val PREFS_NAME = "yvgpscam_updates"
        private const val KEY_DISMISSED_VERSION_CODE = "dismissed_version_code"
        private const val KEY_DISMISSED_VERSION_NAME = "dismissed_version_name"
        private const val KEY_LAST_NOTIFIED_VERSION_CODE = "last_notified_version_code"
        private const val KEY_AUTO_PUSH_ENABLED = "auto_push_enabled"
        internal const val KEY_AUTO_OPEN_AFTER_UPDATE = "auto_open_after_update"
        internal const val KEY_PENDING_AUTO_RELAUNCH = "pending_auto_relaunch"
        internal const val KEY_LAST_SEEN_VERSION_CODE = "last_seen_version_code"
        internal const val KEY_LAST_SEEN_VERSION_NAME = "last_seen_version_name"
        private const val KEY_MANIFEST_FEED_URL = "manifest_feed_url"
        private const val MANDATORY_STALENESS_DAYS = 7
        private const val AUTO_CHECK_THROTTLE_MS = 30_000L
        private const val JOB_ID_PERIODIC_UPDATE = 9402
        private const val UPDATE_NOTIFICATION_ID = 9403
        internal const val UPDATE_CHANNEL_ID = "yvgpscam_updates"
        const val EXTRA_OPEN_UPDATE_POPUP = "extra_open_update_popup"
        const val EXTRA_POST_UPDATE_LAUNCH = "extra_post_update_launch"

        fun resolveDeviceBuildModel(): String {
            val manufacturer = Build.MANUFACTURER.orEmpty().trim()
            val model = Build.MODEL.orEmpty().trim()
            if (manufacturer.isBlank()) return model.ifBlank { "Android Device" }
            if (model.isBlank()) return manufacturer
            val formattedManuf = manufacturer.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
            }
            return if (model.startsWith(formattedManuf, ignoreCase = true)) {
                model
            } else {
                "$formattedManuf $model"
            }
        }

        fun resolveInstalledBuildInfo(context: Context): InstalledBuildInfo {
            val pkgInfo = runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }.getOrNull()

            val liveCode = pkgInfo?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    it.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    it.versionCode
                }
            }?.takeIf { it > 0 } ?: BuildConfig.VERSION_CODE

            val liveName = pkgInfo?.versionName
                ?.takeIf { it.isNotBlank() }
                ?: BuildConfig.VERSION_NAME

            return InstalledBuildInfo(
                versionCode = liveCode,
                versionName = liveName,
                packageName = context.packageName,
                deviceModel = resolveDeviceBuildModel(),
                androidVersion = Build.VERSION.RELEASE.orEmpty().ifBlank { Build.VERSION.SDK_INT.toString() },
                buildDisplay = Build.DISPLAY.orEmpty().ifBlank { "SDK ${Build.VERSION.SDK_INT}" }
            )
        }

        internal fun shouldOfferUpdate(
            remoteCode: Int,
            remoteVersionName: String,
            currentCode: Int,
            currentVersionName: String
        ): Boolean {
            if (remoteCode > currentCode) return true
            val cleanRemote = remoteVersionName.trim().removePrefix("v").removePrefix("V")
            val cleanCurrent = currentVersionName.trim().removePrefix("v").removePrefix("V")
            if (cleanRemote.isNotBlank() && cleanRemote != cleanCurrent && !cleanRemote.startsWith("Build ")) {
                val cmp = compareSemVer(cleanRemote, cleanCurrent)
                if (cmp > 0) return true
                if (remoteCode == currentCode && cmp == 0) return true
            }
            return false
        }

        internal fun compareSemVer(left: String, right: String): Int {
            val leftParts = left.trim().removePrefix("v").removePrefix("V")
                .split(".", "-", "_")
                .mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
            val rightParts = right.trim().removePrefix("v").removePrefix("V")
                .split(".", "-", "_")
                .mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
            val maxLen = maxOf(leftParts.size, rightParts.size)
            for (i in 0 until maxLen) {
                val l = leftParts.getOrElse(i) { 0 }
                val r = rightParts.getOrElse(i) { 0 }
                if (l != r) return l.compareTo(r)
            }
            return left.compareTo(right)
        }

        internal fun parseVersionCodeFromTag(tag: String): Int {
            val digits = tag.trim().removePrefix("v").removePrefix("V")
                .split(".")
                .mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
            if (digits.isEmpty()) return 0
            val major = digits.getOrElse(0) { 0 }
            val minor = digits.getOrElse(1) { 0 }
            val patch = digits.getOrElse(2) { 0 }
            return major * 10000 + minor * 100 + patch
        }
    }
}
