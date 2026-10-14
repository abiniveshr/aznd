package com.ashrdev.aznd.data.macro

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** A custom ingredient. Calories and macros are per 100 g, so any amount can be scaled from them. */
@Entity(tableName = "ingredient")
data class Ingredient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val caloriesPer100: Double,
    val proteinPer100: Double,
    val carbsPer100: Double,
    val fatPer100: Double
)

@Dao
interface IngredientDao {
    @Insert
    suspend fun insert(entry: Ingredient): Long

    @Query("SELECT * FROM ingredient ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Ingredient>>

    @Query("DELETE FROM ingredient WHERE id = :id")
    suspend fun delete(id: Long)
}

/** Version 15 -> 16: adds the custom ingredients table, keeps every existing row. */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ingredient` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `caloriesPer100` REAL NOT NULL, `proteinPer100` REAL NOT NULL, " +
                "`carbsPer100` REAL NOT NULL, `fatPer100` REAL NOT NULL)"
        )
    }
}
