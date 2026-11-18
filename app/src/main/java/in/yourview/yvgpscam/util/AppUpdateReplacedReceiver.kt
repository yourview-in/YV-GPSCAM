package `in`.yourview.yvgpscam.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import `in`.yourview.yvgpscam.MainActivity
import `in`.yourview.yvgpscam.R

class AppUpdateReplacedReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val prefs = context.getSharedPreferences(AppUpdateCoordinator.PREFS_NAME, Context.MODE_PRIVATE)
        val autoOpenEnabled = prefs.getBoolean(AppUpdateCoordinator.KEY_AUTO_OPEN_AFTER_UPDATE, true)
        val pendingRelaunch = prefs.getBoolean(AppUpdateCoordinator.KEY_PENDING_AUTO_RELAUNCH, false)
        prefs.edit().putBoolean(AppUpdateCoordinator.KEY_PENDING_AUTO_RELAUNCH, false).apply()

        if (!autoOpenEnabled && !pendingRelaunch) return

        val installed = AppUpdateCoordinator.resolveInstalledBuildInfo(context)
        prefs.edit()
            .putInt(AppUpdateCoordinator.KEY_LAST_SEEN_VERSION_CODE, installed.versionCode)
            .putString(AppUpdateCoordinator.KEY_LAST_SEEN_VERSION_NAME, installed.versionName)
            .apply()

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AppUpdateCoordinator.EXTRA_POST_UPDATE_LAUNCH, true)
        }

        // Attempt direct auto-open when permitted by OS background-start exemptions
        val launchedDirectly = runCatching {
            context.startActivity(launchIntent)
        }.isSuccess

        if (!launchedDirectly || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            postUpdateCompletedNotification(context, installed, launchIntent)
        }
    }

    @SuppressLint("MissingPermission")
    private fun postUpdateCompletedNotification(
        context: Context,
        installed: InstalledBuildInfo,
        launchIntent: Intent
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (mgr != null && mgr.getNotificationChannel(AppUpdateCoordinator.UPDATE_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    AppUpdateCoordinator.UPDATE_CHANNEL_ID,
                    "App Updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when YVGPSCAM finishes installing a new update."
                }
                mgr.createNotificationChannel(channel)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            POST_UPDATE_NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, AppUpdateCoordinator.UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_yvgpscam)
            .setContentTitle("YVGPSCAM Updated to v${installed.versionName}")
            .setContentText("Build ${installed.versionCode} installed. Tap to open YVGPSCAM.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(POST_UPDATE_NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val POST_UPDATE_NOTIFICATION_ID = 9404
    }
}
