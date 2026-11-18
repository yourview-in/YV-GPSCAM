package `in`.yourview.yvgpscam.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import `in`.yourview.yvgpscam.data.model.CapturedPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CapturedPhotoDao {
    @Query("SELECT * FROM captured_photos ORDER BY capturedAtMillis DESC")
    fun getAllPhotos(): Flow<List<CapturedPhotoEntity>>

    @Query("SELECT * FROM captured_photos WHERE id = :id LIMIT 1")
    fun getPhotoById(id: Long): Flow<CapturedPhotoEntity?>

    @Query(
        """
        SELECT * FROM captured_photos
        WHERE addressLine LIKE '%' || :query || '%'
           OR cityState LIKE '%' || :query || '%'
           OR country LIKE '%' || :query || '%'
           OR customNote LIKE '%' || :query || '%'
           OR displayName LIKE '%' || :query || '%'
        ORDER BY capturedAtMillis DESC
        """
    )
    fun searchPhotos(query: String): Flow<List<CapturedPhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: CapturedPhotoEntity): Long

    @Query("UPDATE captured_photos SET displayName = :newName, mediaUri = :newUri WHERE id = :id")
    suspend fun updatePhotoDisplayNameAndUri(id: Long, newName: String, newUri: String)

    @Query("UPDATE captured_photos SET displayName = :newName WHERE id = :id")
    suspend fun updatePhotoDisplayName(id: Long, newName: String)

    @Query("SELECT * FROM captured_photos ORDER BY capturedAtMillis DESC")
    suspend fun getAllPhotosSnapshot(): List<CapturedPhotoEntity>

    @Query(
        """
        SELECT * FROM captured_photos
        WHERE latitude IS NOT NULL
          AND longitude IS NOT NULL
          AND (addressLine = '' OR cityState = '')
        ORDER BY capturedAtMillis DESC
        LIMIT :limit
        """
    )
    suspend fun getPhotosMissingAddress(limit: Int = 50): List<CapturedPhotoEntity>

    @Query("UPDATE captured_photos SET addressLine = :addressLine, cityState = :cityState, country = :country WHERE id = :id")
    suspend fun updatePhotoAddress(id: Long, addressLine: String, cityState: String, country: String)

    @Query("DELETE FROM captured_photos WHERE id = :id")
    suspend fun deletePhotoById(id: Long)

    @Query("DELETE FROM captured_photos WHERE id IN (:ids)")
    suspend fun deletePhotosByIds(ids: List<Long>)
}
