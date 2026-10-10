package com.ashrdev.aznd.data.macro

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** One thing eaten. [eatenAt] is epoch millis; calories in kcal, macros in grams. */
@Entity(tableName = "food_entry", indices = [Index("eatenAt")])
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eatenAt: Long,
    val name: String,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double
)

@Dao
interface FoodDao {
    @Insert
    suspend fun insert(entry: FoodEntry): Long

    @Query("SELECT * FROM food_entry ORDER BY eatenAt DESC, id DESC")
    fun observeAll(): Flow<List<FoodEntry>>

    @Query("DELETE FROM food_entry WHERE id = :id")
    suspend fun delete(id: Long)
}

/** Version 14 -> 15: adds the food log, keeps every existing row. */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `food_entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`eatenAt` INTEGER NOT NULL, `name` TEXT NOT NULL, `calories` REAL NOT NULL, " +
                "`proteinG` REAL NOT NULL, `carbsG` REAL NOT NULL, `fatG` REAL NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_entry_eatenAt` ON `food_entry` (`eatenAt`)")
    }
}

/** Daily targets. 0 = not set (the screen then just shows totals). */
class MacroTargetsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("macro_targets", Context.MODE_PRIVATE)

    var calories: Int by mutableIntStateOf(prefs.getInt("kcal", 0))
        private set
    var proteinG: Int by mutableIntStateOf(prefs.getInt("protein", 0))
        private set
    var carbsG: Int by mutableIntStateOf(prefs.getInt("carbs", 0))
        private set
    var fatG: Int by mutableIntStateOf(prefs.getInt("fat", 0))
        private set

    fun save(calories: Int, proteinG: Int, carbsG: Int, fatG: Int) {
        this.calories = calories.coerceAtLeast(0)
        this.proteinG = proteinG.coerceAtLeast(0)
        this.carbsG = carbsG.coerceAtLeast(0)
        this.fatG = fatG.coerceAtLeast(0)
        prefs.edit()
            .putInt("kcal", this.calories).putInt("protein", this.proteinG)
            .putInt("carbs", this.carbsG).putInt("fat", this.fatG)
            .apply()
    }
}
