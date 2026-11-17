package in.yourview.yvgpscam.util

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
import in.yourview.yvgpscam.BuildConfig
import in.yourview.yvgpscam.MainActivity
import in.yourview.yvgpscam.R
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
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
import java.util.concurrent.TimeUnit

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
        .readTimeout(10, TimeUnit.SECONDS)
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

    private val _manifestFeedUrl = MutableStateFlow(prefs.getString(KEY_MANIFEST_FEED_URL, "").orEmpty())
    val manifestFeedUrl: StateFlow<String> = _manifestFeedUrl.asStateFlow()

    private var listenerRegistered = false
    private var lastAutoCheckMillis = 0L

    private val installStateListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING -> {
                val total = state.totalBytesToDownload().coerceAtLeast(1L)
                val pct = ((state.bytesDownloaded() * 100L) / total).toInt().coerceIn(0, 100)
                _updateState.value = _updateState.value?.copy(
                    isDownloading = true,
                    downloadProgressPercent = pct
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
                    isDownloaded = true
                )
                _updateState.value = updated
            }
            InstallStatus.INSTALLED -> {
                _updateState.value = null
                unregisterListenerIfNeeded()
            }
            InstallStatus.FAILED, InstallStatus.CANCELED -> {
                _updateState.value = _updateState.value?.copy(
                    isDownloading = false,
                    downloadProgressPercent = null
                )
            }
            else -> Unit
        }
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

    fun setManifestFeedUrl(url: String) {
        val clean = url.trim()
        _manifestFeedUrl.value = clean
        prefs.edit().putString(KEY_MANIFEST_FEED_URL, clean).apply()
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

        val manager = appUpdateManager
        if (manager == null) {
            checkRemoteManifestFallback(forceShowIfDismissed, pushNotificationIfFound, onUpToDate)
            return
        }

        runCatching {
            manager.appUpdateInfo
                .addOnSuccessListener { info ->
                    if (info.installStatus() == InstallStatus.DOWNLOADED) {
                        _isChecking.value = false
                        val downloadedState = AppUpdateInfoState(
                            currentVersionName = BuildConfig.VERSION_NAME,
                            currentVersionCode = BuildConfig.VERSION_CODE,
                            availableVersionCode = info.availableVersionCode(),
                            availableVersionLabel = "Build ${info.availableVersionCode()}",
                            isDownloaded = true,
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
                                currentVersionName = BuildConfig.VERSION_NAME,
                                currentVersionCode = BuildConfig.VERSION_CODE,
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

                    checkRemoteManifestFallback(forceShowIfDismissed, pushNotificationIfFound, onUpToDate)
                }
                .addOnFailureListener {
                    checkRemoteManifestFallback(forceShowIfDismissed, pushNotificationIfFound, onUpToDate)
                }
        }.onFailure {
            checkRemoteManifestFallback(forceShowIfDismissed, pushNotificationIfFound, onUpToDate)
        }
    }

    private fun checkRemoteManifestFallback(
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
                    val req = Request.Builder().url(feedUrl).build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        val body = resp.body?.string().orEmpty()
                        if (body.isBlank()) return@use null
                        evaluateManifestJson(
                            manifestJson = body,
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
                    when {
                        info.installStatus() == InstallStatus.DOWNLOADED -> {
                            _updateState.value = (_updateState.value ?: AppUpdateInfoState(
                                currentVersionName = BuildConfig.VERSION_NAME,
                                currentVersionCode = BuildConfig.VERSION_CODE,
                                availableVersionCode = info.availableVersionCode(),
                                availableVersionLabel = "Build ${info.availableVersionCode()}",
                                playAppUpdateInfo = info
                            )).copy(
                                isDownloaded = true,
                                isDownloading = false,
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

        if (manager != null && playInfo != null) {
            val updateType = when {
                current.isImmediateRequired && playInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> AppUpdateType.IMMEDIATE
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
                if (updateType == AppUpdateType.FLEXIBLE) {
                    _updateState.value = current.copy(isDownloading = true)
                }
                return
            }
        }

        openStoreListing(activity, current.storeUrl)
    }

    fun completeFlexibleUpdate() {
        runCatching { appUpdateManager?.completeUpdate() }
        _updateState.value = null
        unregisterListenerIfNeeded()
    }

    fun dismissUpdateForNow() {
        val current = _updateState.value ?: return
        if (current.isImmediateRequired) return
        prefs.edit().putInt(KEY_DISMISSED_VERSION_CODE, current.availableVersionCode).apply()
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
            // Supports both standard {versionCode, versionName} JSON and GitHub Releases {tag_name, body, html_url}
            val explicitCode = root.optInt("versionCode", 0)
            val tagName = root.optString("tag_name", "")
            val remoteCode = if (explicitCode > 0) {
                explicitCode
            } else {
                parseVersionCodeFromTag(tagName)
            }
            if (remoteCode <= currentVersionCode) return null

            val mandatory = root.optBoolean("mandatory", false)
            val dismissedCode = prefs.getInt(KEY_DISMISSED_VERSION_CODE, -1)
            if (!forceShow && !mandatory && dismissedCode == remoteCode) return null

            val label = root.optString("versionName", "")
                .ifBlank { tagName.removePrefix("v").removePrefix("V") }
                .ifBlank { "Build $remoteCode" }

            val notes = root.optString("releaseNotes", "")
                .ifBlank { root.optString("body", "") }
                .ifBlank { AppUpdateInfoState.DEFAULT_RELEASE_NOTES }

            val url = root.optString("storeUrl", "")
                .ifBlank { root.optString("downloadUrl", "") }
                .ifBlank { root.optString("html_url", "") }
                .ifBlank { null }

            val state = AppUpdateInfoState(
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode,
                availableVersionCode = remoteCode,
                availableVersionLabel = label,
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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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
        private const val PREFS_NAME = "yvgpscam_updates"
        private const val KEY_DISMISSED_VERSION_CODE = "dismissed_version_code"
        private const val KEY_LAST_NOTIFIED_VERSION_CODE = "last_notified_version_code"
        private const val KEY_AUTO_PUSH_ENABLED = "auto_push_enabled"
        private const val KEY_MANIFEST_FEED_URL = "manifest_feed_url"
        private const val MANDATORY_STALENESS_DAYS = 7
        private const val AUTO_CHECK_THROTTLE_MS = 10 * 60 * 1000L
        private const val JOB_ID_PERIODIC_UPDATE = 9402
        private const val UPDATE_NOTIFICATION_ID = 9403
        private const val UPDATE_CHANNEL_ID = "yvgpscam_updates"
        const val EXTRA_OPEN_UPDATE_POPUP = "extra_open_update_popup"

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
