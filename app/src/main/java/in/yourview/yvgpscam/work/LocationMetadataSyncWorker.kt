package `in`.yourview.yvgpscam.work

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import `in`.yourview.yvgpscam.data.cloud.CloudMediaGateway
import `in`.yourview.yvgpscam.data.cloud.DriveUnauthorizedException
import `in`.yourview.yvgpscam.data.cloud.GoogleDriveAccountStore
import `in`.yourview.yvgpscam.data.cloud.GoogleDriveAuthHelper
import `in`.yourview.yvgpscam.data.cloud.GoogleDriveBackupRepository
import `in`.yourview.yvgpscam.data.cloud.LocationMetadataSyncStore
import `in`.yourview.yvgpscam.data.cloud.NetworkConnectivityObserver
import `in`.yourview.yvgpscam.data.local.AppDatabase
import `in`.yourview.yvgpscam.data.local.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class LocationMetadataSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @VisibleForTesting
    internal data class TestDependencies(
        val isOnline: (() -> Boolean)? = null,
        val syncStore: LocationMetadataSyncStore? = null,
        val accountStore: GoogleDriveAccountStore? = null,
        val driveRepository: GoogleDriveBackupRepository? = null,
        val photoRepository: PhotoRepository? = null,
        val mediaGateway: CloudMediaGateway? = null,
        val tokenProvider: (suspend (Context) -> String?)? = null,
        val geocoderOverride: (suspend (Double, Double) -> Triple<String, String, String>?)? = null
    )

    override suspend fun doWork(): Result {
        val overrides = testOverrides
        val isOnline = overrides?.isOnline?.invoke()
            ?: NetworkConnectivityObserver.isNetworkConnected(applicationContext)
        if (!isOnline) {
            return Result.retry()
        }

        val syncStore = overrides?.syncStore ?: LocationMetadataSyncStore.getInstance(applicationContext)
        val accountStore = overrides?.accountStore ?: GoogleDriveAccountStore(applicationContext)
        val driveRepo = overrides?.driveRepository ?: GoogleDriveBackupRepository()
        val photoRepo = overrides?.photoRepository ?: PhotoRepository(
            applicationContext,
            AppDatabase.getInstance(applicationContext).capturedPhotoDao()
        )
        val mediaGateway = overrides?.mediaGateway ?: CloudMediaGateway(applicationContext)
        val tokenProvider = overrides?.tokenProvider ?: { ctx ->
            GoogleDriveAuthHelper.tryGetDriveAccessTokenSilently(ctx)
        }

        syncStore.setSyncing(true)
        return try {
            var geocodedPhotosCount = 0
            var geocodedEntriesCount = 0
            var uploadedPhotosCount = 0
            var syncedMetadataEntriesCount = 0

            // 1. Reverse-geocode offline-captured photos missing street/city address
            val missingPhotos = photoRepo.getPhotosMissingAddress(limit = 40)
            for (photo in missingPhotos) {
                val lat = photo.latitude ?: continue
                val lon = photo.longitude ?: continue
                val resolved = resolveAddress(lat, lon, overrides?.geocoderOverride)
                if (resolved != null) {
                    val (street, city, country) = resolved
                    if (street.isNotBlank() || city.isNotBlank()) {
                        photoRepo.updatePhotoAddress(
                            id = photo.id,
                            addressLine = street.ifBlank { photo.addressLine },
                            cityState = city.ifBlank { photo.cityState },
                            country = country.ifBlank { photo.country }
                        )
                        geocodedPhotosCount++
                    }
                }
            }

            // 2. Enrich pending cached location telemetry entries missing address metadata
            val pendingBefore = syncStore.getPendingEntries()
            for (entry in pendingBefore) {
                if (entry.needsGeocode) {
                    val resolved = resolveAddress(entry.latitude, entry.longitude, overrides?.geocoderOverride)
                    if (resolved != null) {
                        val (street, city, country) = resolved
                        syncStore.updateGeocodedEntry(entry.id, street, city, country)
                        geocodedEntriesCount++
                    }
                }
            }

            val pendingEntries = syncStore.getPendingEntries()

            // 3. Cloud re-sync via Google Drive when signed in
            val account = accountStore.accountFlow.first()
            if (account.isSignedIn) {
                val accessToken = tokenProvider(applicationContext)
                if (!accessToken.isNullOrBlank()) {
                    // 3a. Sync any local geotagged photos not yet present on Google Drive
                    val existingDriveBackups = driveRepo.listBackups(accessToken).getOrElse { err ->
                        if (err is DriveUnauthorizedException) throw err
                        if (err is IOException) throw err
                        emptyList()
                    }
                    val existingIds = existingDriveBackups.map { it.backupId }.toMutableSet()
                    val skipIds = accountStore.skipAutoBackupIdsFlow.first()
                    val localPhotos = photoRepo.getAllPhotosSnapshot()

                    for (photo in localPhotos) {
                        val detId = GoogleDriveBackupRepository.buildDeterministicBackupId(
                            photo.capturedAtMillis,
                            photo.displayName
                        )
                        if (detId !in existingIds && detId !in skipIds) {
                            val jpegBytes = mediaGateway.readJpegBytesForDrive(photo).getOrNull()
                            if (jpegBytes != null) {
                                driveRepo.uploadPhotoBackup(accessToken, photo, jpegBytes).fold(
                                    onSuccess = { uploaded ->
                                        existingIds.add(uploaded.backupId)
                                        uploadedPhotosCount++
                                    },
                                    onFailure = { err ->
                                        if (err is DriveUnauthorizedException || err is IOException) throw err
                                    }
                                )
                            }
                        }
                    }

                    // 3b. Upload locally cached offline location telemetry batch JSON to Drive
                    if (pendingEntries.isNotEmpty()) {
                        val payloadJson = syncStore.buildSyncPayloadJson(pendingEntries)
                        val fileName = "YVGPSCAM_LocationMetadata_${System.currentTimeMillis()}.json"
                        driveRepo.uploadLocationMetadataLog(
                            accessToken = accessToken,
                            jsonPayload = payloadJson,
                            fileName = fileName
                        ).fold(
                            onSuccess = {
                                val ids = pendingEntries.map { it.id }
                                syncStore.markEntriesSynced(ids)
                                syncedMetadataEntriesCount = ids.size
                            },
                            onFailure = { err ->
                                if (err is DriveUnauthorizedException || err is IOException) throw err
                            }
                        )
                    }
                }
            } else if (pendingEntries.isNotEmpty()) {
                // Local-only mode (not signed in to Drive): local geocode enrichment completed above,
                // mark cached breadcrumbs reconciled so offline badge returns cleanly to online state.
                val ids = pendingEntries.map { it.id }
                syncStore.markEntriesSynced(ids)
                syncedMetadataEntriesCount = ids.size
            }

            Result.success(
                workDataOf(
                    KEY_GEOCODED_PHOTOS to geocodedPhotosCount,
                    KEY_GEOCODED_ENTRIES to geocodedEntriesCount,
                    KEY_UPLOADED_PHOTOS to uploadedPhotosCount,
                    KEY_SYNCED_METADATA_ENTRIES to syncedMetadataEntriesCount
                )
            )
        } catch (_: DriveUnauthorizedException) {
            Result.failure()
        } catch (_: IOException) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } catch (_: Throwable) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } finally {
            syncStore.setSyncing(false)
        }
    }

    private suspend fun resolveAddress(
        lat: Double,
        lon: Double,
        override: (suspend (Double, Double) -> Triple<String, String, String>?)?
    ): Triple<String, String, String>? {
        if (override != null) return override(lat, lon)
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(applicationContext, Locale.getDefault())
        return withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(4_000L) {
                    suspendCancellableCoroutine { cont ->
                        runCatching {
                            geocoder.getFromLocation(
                                lat,
                                lon,
                                1,
                                object : Geocoder.GeocodeListener {
                                    override fun onGeocode(addresses: MutableList<Address>) {
                                        val first = addresses.firstOrNull()
                                        if (cont.isActive) {
                                            cont.resume(first?.let { formatAddress(it) })
                                        }
                                    }

                                    override fun onError(errorMessage: String?) {
                                        if (cont.isActive) cont.resume(null)
                                    }
                                }
                            )
                        }.onFailure {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }
            } else {
                runCatching {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()?.let { formatAddress(it) }
                }.getOrNull()
            }
        }
    }

    private fun formatAddress(addr: Address): Triple<String, String, String> {
        val street = addr.getAddressLine(0) ?: listOfNotNull(
            addr.subThoroughfare,
            addr.thoroughfare,
            addr.subLocality
        ).joinToString(" ")
        val cityState = listOfNotNull(addr.locality, addr.adminArea)
            .distinct()
            .joinToString(", ")
        val country = addr.countryName.orEmpty()
        return Triple(street, cityState, country)
    }

    companion object {
        const val KEY_GEOCODED_PHOTOS = "geocoded_photos"
        const val KEY_GEOCODED_ENTRIES = "geocoded_entries"
        const val KEY_UPLOADED_PHOTOS = "uploaded_photos"
        const val KEY_SYNCED_METADATA_ENTRIES = "synced_metadata_entries"

        @VisibleForTesting
        @Volatile
        internal var testOverrides: TestDependencies? = null
    }
}

object LocationMetadataSyncScheduler {
    const val UNIQUE_WORK_NAME = "yvgpscam_location_metadata_sync"
    const val WORK_TAG = "location_metadata_sync"

    fun buildSyncWorkRequest(): OneTimeWorkRequest {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        return OneTimeWorkRequestBuilder<LocationMetadataSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()
    }

    fun enqueueWhenConnected(
        context: Context,
        replaceExisting: Boolean = false
    ) {
        runCatching {
            val wm = WorkManager.getInstance(context.applicationContext)
            val policy = if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
            wm.enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                policy,
                buildSyncWorkRequest()
            )
        }
    }
}
