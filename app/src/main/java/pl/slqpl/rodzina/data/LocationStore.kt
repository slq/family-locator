package pl.slqpl.rodzina.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "samples")
data class LocationSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerUid: String?,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val measuredAt: Long,
    val batteryPercent: Int,
    val uploaded: Boolean = false,
)

@Dao
interface LocationDao {
    @Insert suspend fun insert(sample: LocationSample)
    @Query("SELECT * FROM samples ORDER BY id DESC LIMIT 1")
    fun observeLatest(): Flow<LocationSample?>
    @Query("SELECT COUNT(*) FROM samples WHERE uploaded = 0 AND ownerUid IS NOT NULL")
    fun observePendingCount(): Flow<Int>
    @Query("SELECT * FROM samples WHERE uploaded = 0 AND ownerUid = :uid ORDER BY id DESC LIMIT 1")
    suspend fun newestPending(uid: String): LocationSample?
    @Query("UPDATE samples SET uploaded = 1 WHERE ownerUid = :uid AND id <= :throughId")
    suspend fun acknowledge(uid: String, throughId: Long)
    @Query("DELETE FROM samples WHERE measuredAt < :cutoff OR id NOT IN (SELECT id FROM samples ORDER BY id DESC LIMIT :limit)")
    suspend fun prune(cutoff: Long, limit: Int)
    @Query("DELETE FROM samples") suspend fun clear()
}

@Database(entities = [LocationSample::class], version = 1, exportSchema = true)
abstract class LocationDatabase : RoomDatabase() { abstract fun locations(): LocationDao }
