package com.ashrdev.aznd.data.body

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ashrdev.aznd.domain.BodyweightProvider
import com.ashrdev.aznd.domain.PLACEHOLDER_BODYWEIGHT_KG
import kotlinx.coroutines.flow.Flow

/** One bodyweight reading. [measuredAt] is epoch millis, [weightKg] in kilograms. */
@Entity(tableName = "body_weight", indices = [Index("measuredAt")])
data class BodyWeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val measuredAt: Long,
    val weightKg: Double
)

/**
 * One tape measurement. [site] is the name of a MeasurementSite (a plain string so an unknown
 * value can never crash a read), [valueCm] in centimetres.
 */
@Entity(tableName = "body_measurement", indices = [Index("site"), Index("measuredAt")])
data class BodyMeasurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val site: String,
    val measuredAt: Long,
    val valueCm: Double
)

@Dao
interface BodyDao {
    @Insert
    suspend fun insertWeight(entry: BodyWeightEntry): Long

    @Query("SELECT * FROM body_weight ORDER BY measuredAt DESC, id DESC")
    fun observeWeights(): Flow<List<BodyWeightEntry>>

    @Query("DELETE FROM body_weight WHERE id = :id")
    suspend fun deleteWeight(id: Long)

    @Query("SELECT weightKg FROM body_weight ORDER BY measuredAt DESC, id DESC LIMIT 1")
    suspend fun latestWeightKg(): Double?

    @Insert
    suspend fun insertMeasurement(entry: BodyMeasurement): Long

    @Query("SELECT * FROM body_measurement ORDER BY measuredAt DESC, id DESC")
    fun observeMeasurements(): Flow<List<BodyMeasurement>>

    @Query("DELETE FROM body_measurement WHERE id = :id")
    suspend fun deleteMeasurement(id: Long)
}

/** Sessions snapshot the latest logged bodyweight when they start (75 kg until one is logged). */
class RoomBodyweightProvider(private val dao: BodyDao) : BodyweightProvider {
    override suspend fun currentKg(): Double = dao.latestWeightKg() ?: PLACEHOLDER_BODYWEIGHT_KG
}

/** Version 13 -> 14: adds the two body tables, keeps every existing row. */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `body_weight` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`measuredAt` INTEGER NOT NULL, `weightKg` REAL NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_weight_measuredAt` ON `body_weight` (`measuredAt`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `body_measurement` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`site` TEXT NOT NULL, `measuredAt` INTEGER NOT NULL, `valueCm` REAL NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_measurement_site` ON `body_measurement` (`site`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_measurement_measuredAt` ON `body_measurement` (`measuredAt`)")
    }
}
