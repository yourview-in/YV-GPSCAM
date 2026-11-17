package `in`.yourview.yvgpscam.di

import android.content.Context
import `in`.yourview.yvgpscam.camera.MediaStoreSaver
import `in`.yourview.yvgpscam.camera.PhotoStampRenderer
import `in`.yourview.yvgpscam.data.cloud.CloudMediaGateway
import `in`.yourview.yvgpscam.data.cloud.GoogleDriveAccountStore
import `in`.yourview.yvgpscam.data.cloud.GoogleDriveAutoSyncCoordinator
import `in`.yourview.yvgpscam.data.cloud.GoogleDriveBackupRepository
import `in`.yourview.yvgpscam.data.cloud.NetworkConnectivityObserver
import `in`.yourview.yvgpscam.data.local.AppDatabase
import `in`.yourview.yvgpscam.data.local.PhotoRepository
import `in`.yourview.yvgpscam.data.local.StampPreferencesRepository
import `in`.yourview.yvgpscam.map.MiniMapTileRenderer
import `in`.yourview.yvgpscam.map.OfflineMapRepository
import `in`.yourview.yvgpscam.sensors.LocationTelemetryManager
import `in`.yourview.yvgpscam.util.AppUpdateCoordinator
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class YvGpsCamContainer(context: Context) {
    private val appContext = context.applicationContext

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }
    val stampPreferencesRepository: StampPreferencesRepository by lazy {
        StampPreferencesRepository(appContext)
    }
    val photoRepository: PhotoRepository by lazy {
        PhotoRepository(appContext, database.capturedPhotoDao())
    }
    val offlineMapRepository: OfflineMapRepository by lazy {
        OfflineMapRepository(appContext, database.offlineRegionDao(), httpClient)
    }
    val miniMapTileRenderer: MiniMapTileRenderer by lazy {
        MiniMapTileRenderer(offlineMapRepository)
    }
    val locationTelemetryManager: LocationTelemetryManager by lazy {
        LocationTelemetryManager(appContext)
    }
    val photoStampRenderer: PhotoStampRenderer by lazy {
        PhotoStampRenderer(appContext)
    }
    val mediaStoreSaver: MediaStoreSaver by lazy {
        MediaStoreSaver(appContext, photoStampRenderer)
    }
    val googleDriveAccountStore: GoogleDriveAccountStore by lazy {
        GoogleDriveAccountStore(appContext)
    }
    val googleDriveBackupRepository: GoogleDriveBackupRepository by lazy {
        GoogleDriveBackupRepository(httpClient)
    }
    val networkConnectivityObserver: NetworkConnectivityObserver by lazy {
        NetworkConnectivityObserver(appContext)
    }
    val cloudMediaGateway: CloudMediaGateway by lazy {
        CloudMediaGateway(appContext)
    }
    val autoSyncCoordinator: GoogleDriveAutoSyncCoordinator by lazy {
        GoogleDriveAutoSyncCoordinator(
            appContext = appContext,
            accountStore = googleDriveAccountStore,
            driveRepository = googleDriveBackupRepository,
            photoRepository = photoRepository,
            mediaGateway = cloudMediaGateway,
            connectivityObserver = networkConnectivityObserver
        )
    }

    val appUpdateCoordinator: AppUpdateCoordinator by lazy {
        AppUpdateCoordinator(appContext, httpClient)
    }
}
