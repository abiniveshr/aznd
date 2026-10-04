package com.ashrdev.aznd.data.workout

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.ashrdev.aznd.domain.SetRow
import com.ashrdev.aznd.domain.SetTree
import kotlinx.coroutines.flow.Flow

/** Data access object for workout template sets. */
@Dao
interface TemplateDao {

    @Insert
    suspend fun insert(row: TemplateSet): Long

    @Query("SELECT * FROM template_set WHERE workoutId = :workoutId")
    suspend fun forWorkout(workoutId: Long): List<TemplateSet>

    @Query("SELECT * FROM template_set WHERE workoutId = :workoutId")
    fun observeForWorkout(workoutId: Long): Flow<List<TemplateSet>>

    @Query("DELETE FROM template_set WHERE workoutId = :workoutId")
    suspend fun deleteForWorkout(workoutId: Long)

    /**
     * Save the builder's rows as the workout's template. Rows with no exercise are dropped, ids
     * (temporary ones included) are replaced by real ones and parent links follow.
     */
    @Transaction
    suspend fun replaceTemplate(workoutId: Long, rows: List<SetRow>) {
        deleteForWorkout(workoutId)
        val ids = HashMap<Long, Long>()
        for (r in SetTree.dropUnsetRows(rows)) {
            ids[r.id] = insert(
                TemplateSet(
                    workoutId = workoutId,
                    position = r.position,
                    exerciseId = r.exerciseId,
                    kind = r.kind,
                    parentSetId = r.parentId?.let { ids[it] },
                    targetMin = r.targetMin,
                    targetMax = r.targetMax
                )
            )
        }
    }
}
