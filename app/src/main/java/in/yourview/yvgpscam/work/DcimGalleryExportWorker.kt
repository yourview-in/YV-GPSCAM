package `in`.yourview.yvgpscam.work

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class DcimGalleryExportWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sourceUri = inputData.getString(KEY_SOURCE_URI)?.trim().orEmpty()
        val displayName = inputData.getString(KEY_DISPLAY_NAME)?.trim().orEmpty()
        val capturedAtMillis = inputData.getLong(KEY_CAPTURED_AT_MILLIS, System.currentTimeMillis())

        if (sourceUri.isBlank() || displayName.isBlank()) {
            return Result.failure()
        }

        return try {
            val exportedUri = DcimGalleryExporter.exportExifTaggedPhotoToPublicDcim(
                context = applicationContext,
                sourceUriString = sourceUri,
                displayName = displayName,
                capturedAtMillis = capturedAtMillis
            )
            if (exportedUri != null) {
                Result.success(workDataOf(KEY_EXPORTED_URI to exportedUri.toString()))
            } else if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        } catch (_: SecurityException) {
            Result.failure()
        } catch (_: IllegalArgumentException) {
            Result.failure()
        } catch (_: Throwable) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_SOURCE_URI = "source_uri"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_CAPTURED_AT_MILLIS = "captured_at_millis"
        const val KEY_EXPORTED_URI = "exported_uri"
    }
}

object DcimExportScheduler {
    const val WORK_TAG = "dcim_gallery_export"

    fun uniqueWorkName(displayName: String): String =
        "dcim_export_${displayName.hashCode()}"

    fun buildWorkRequest(
        sourceUri: String,
        displayName: String,
        capturedAtMillis: Long
    ): OneTimeWorkRequest {
        val input: Data = workDataOf(
            DcimGalleryExportWorker.KEY_SOURCE_URI to sourceUri,
            DcimGalleryExportWorker.KEY_DISPLAY_NAME to displayName,
            DcimGalleryExportWorker.KEY_CAPTURED_AT_MILLIS to capturedAtMillis
        )
        return OneTimeWorkRequestBuilder<DcimGalleryExportWorker>()
            .setInputData(input)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()
    }

    fun enqueueAfterExif(
        context: Context,
        sourceUri: String,
        displayName: String,
        capturedAtMillis: Long
    ) {
        if (sourceUri.isBlank() || displayName.isBlank()) return
        runCatching {
            val workManager = WorkManager.getInstance(context.applicationContext)
            val request = buildWorkRequest(sourceUri, displayName, capturedAtMillis)
            workManager.enqueueUniqueWork(
                uniqueWorkName(displayName),
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}

object DcimGalleryExporter {
    const val PUBLIC_DCIM_SUBDIR = "YVGPSCAM"

    fun exportExifTaggedPhotoToPublicDcim(
        context: Context,
        sourceUriString: String,
        displayName: String,
        capturedAtMillis: Long
    ): Uri? {
        val sourceUri = Uri.parse(sourceUriString)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findExistingDcimUri(context, displayName)?.let { return it }

            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/$PUBLIC_DCIM_SUBDIR")
                put(MediaStore.Images.Media.DATE_TAKEN, capturedAtMillis)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val insertedUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null

            var completed = false
            try {
                openSourceStream(context, sourceUri)?.use { input ->
                    resolver.openOutputStream(insertedUri)?.use { out ->
                        input.copyTo(out)
                        out.flush()
                    } ?: throw IllegalStateException("Unable to open DCIM output stream")
                } ?: throw IllegalStateException("Unable to read EXIF-tagged source photo")

                val publishValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(insertedUri, publishValues, null, null)
                completed = true
                insertedUri
            } finally {
                if (!completed) {
                    runCatching { resolver.delete(insertedUri, null, null) }
                }
            }
        } else {
            val outDir = resolveLegacyDcimDirectory(context)
            val outFile = File(outDir, displayName)
            if (outFile.exists() && outFile.length() > 0L) {
                return runCatching {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
                }.getOrElse { Uri.fromFile(outFile) }
            }
            openSourceStream(context, sourceUri)?.use { input ->
                FileOutputStream(outFile).use { fos ->
                    input.copyTo(fos)
                    fos.flush()
                }
            } ?: return null

            runCatching {
                MediaScannerConnection.scanFile(
                    context.applicationContext,
                    arrayOf(outFile.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )
            }
            runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            }.getOrElse { Uri.fromFile(outFile) }
        }
    }

    fun deleteMirroredDcimCopy(context: Context, displayName: String) {
        if (displayName.isBlank()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val resolver = context.contentResolver
                val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf(displayName, "${Environment.DIRECTORY_DCIM}/$PUBLIC_DCIM_SUBDIR%")
                resolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection, selectionArgs)
            }
        } else {
            runCatching {
                val publicFile = File(
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), PUBLIC_DCIM_SUBDIR),
                    displayName
                )
                if (publicFile.exists()) publicFile.delete()
                val scopedFile = File(
                    File(context.getExternalFilesDir(Environment.DIRECTORY_DCIM), PUBLIC_DCIM_SUBDIR),
                    displayName
                )
                if (scopedFile.exists()) scopedFile.delete()
            }
        }
    }

    fun renameMirroredDcimCopy(context: Context, oldDisplayName: String, newDisplayName: String) {
        if (oldDisplayName.isBlank() || newDisplayName.isBlank() || oldDisplayName == newDisplayName) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val existing = findExistingDcimUri(context, oldDisplayName) ?: return@runCatching
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, newDisplayName)
                }
                context.contentResolver.update(existing, values, null, null)
            }
        } else {
            runCatching {
                val dir = resolveLegacyDcimDirectory(context)
                val oldFile = File(dir, oldDisplayName)
                if (oldFile.exists()) {
                    oldFile.renameTo(File(dir, newDisplayName))
                }
            }
        }
    }

    private fun findExistingDcimUri(context: Context, displayName: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.IS_PENDING
            )
            val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val args = arrayOf(displayName, "${Environment.DIRECTORY_DCIM}/$PUBLIC_DCIM_SUBDIR%")
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))
                    val pending = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.IS_PENDING))
                    if (pending == 0 && size > 0L) {
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun openSourceStream(context: Context, uri: Uri): InputStream? {
        return when (uri.scheme) {
            "file" -> uri.path?.let { path ->
                val f = File(path)
                if (f.exists()) FileInputStream(f) else null
            }
            else -> context.contentResolver.openInputStream(uri)
        }
    }

    private fun resolveLegacyDcimDirectory(context: Context): File {
        val publicDcim = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            PUBLIC_DCIM_SUBDIR
        )
        if ((publicDcim.exists() || publicDcim.mkdirs()) && publicDcim.canWrite()) {
            return publicDcim
        }
        return File(context.getExternalFilesDir(Environment.DIRECTORY_DCIM), PUBLIC_DCIM_SUBDIR).apply {
            mkdirs()
        }
    }
}
