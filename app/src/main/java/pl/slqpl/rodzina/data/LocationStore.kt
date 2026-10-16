package pl.slqpl.rodzina.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

@Entity(tableName = "samples", indices = [Index(value = ["ownerUid", "uploaded"])])
data class LocationSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerUid: String?,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val measuredAt: Long,
    val batteryPercent: Int,
    val uploaded: Boolean = false,
    @ColumnInfo(defaultValue = "''") val pointKey: String = UUID.randomUUID().toString(),
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
    @Query("SELECT * FROM samples WHERE uploaded = 0 AND ownerUid = :uid ORDER BY id ASC LIMIT :limit")
    suspend fun pending(uid: String, limit: Int): List<LocationSample>
    @Query("UPDATE samples SET uploaded = 1 WHERE ownerUid = :uid AND id <= :throughId")
    suspend fun acknowledge(uid: String, throughId: Long)
    @Query("DELETE FROM samples WHERE measuredAt < :cutoff OR id NOT IN (SELECT id FROM samples ORDER BY id DESC LIMIT :limit)")
    suspend fun prune(cutoff: Long, limit: Int)
    @Query("DELETE FROM samples") suspend fun clear()
    @Query("DELETE FROM samples WHERE uploaded = 0 AND ownerUid = :uid") suspend fun discardPending(uid: String)
    @Query("DELETE FROM samples WHERE ownerUid = :uid AND measuredAt <= :through") suspend fun deleteThrough(uid: String, through: Long)
}

@Database(entities = [LocationSample::class], version = 2, exportSchema = true)
abstract class LocationDatabase : RoomDatabase() {
    abstract fun locations(): LocationDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE samples ADD COLUMN pointKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE samples SET pointKey = lower(hex(randomblob(16)))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_samples_ownerUid_uploaded ON samples(ownerUid, uploaded)")
            }
        }
    }
}
