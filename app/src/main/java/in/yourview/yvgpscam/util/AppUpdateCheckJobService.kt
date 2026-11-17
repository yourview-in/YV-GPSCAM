package `in`.yourview.yvgpscam.util

import android.annotation.SuppressLint
import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("SpecifyJobSchedulerIdRange")
class AppUpdateCheckJobService : JobService() {

    private var jobScope: CoroutineScope? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        jobScope = scope
        val coordinator = AppUpdateCoordinator(applicationContext)

        if (!coordinator.autoPushEnabled.value) {
            return false
        }

        scope.launch {
            runCatching {
                coordinator.checkForUpdates(
                    forceShowIfDismissed = false,
                    fromResume = false,
                    pushNotificationIfFound = true,
                    onUpToDate = {}
                )
                // Allow Play Core / OkHttp async callbacks to settle before finishing background job
                delay(4500L)
            }
            coordinator.dispose()
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        jobScope?.cancel()
        jobScope = null
        return false
    }
}
