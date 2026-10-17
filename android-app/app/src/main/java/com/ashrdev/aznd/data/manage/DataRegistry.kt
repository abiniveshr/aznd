package com.ashrdev.aznd.data.manage

import android.content.Context
import com.ashrdev.aznd.data.AppDatabase
import com.ashrdev.aznd.data.macro.MacroTargetsStore
import com.ashrdev.aznd.data.workout.TrainingRepository

class DataRegistry(val modules: List<DataModule>) {

    fun module(id: String): DataModule? = modules.firstOrNull { it.id == id }

    fun forApp(app: DataApp?): List<DataModule> =
        if (app == null) modules else modules.filter { it.app == app }

    /** Modules of any of [apps]; an empty set means every module. */
    fun forApps(apps: Set<DataApp>): List<DataModule> =
        if (apps.isEmpty()) modules else modules.filter { it.app in apps }

    val apps: List<DataApp> get() = DataApp.entries.filter { app -> modules.any { it.app == app } }

    companion object {
        fun create(
            context: Context,
            db: AppDatabase,
            training: TrainingRepository,
            macroTargets: MacroTargetsStore
        ): DataRegistry {
            val photos = PhotoFiles(context.applicationContext)
            return DataRegistry(
                listOf(
                    WorkoutRoutinesModule(db, training),
                    WorkoutHistoryModule(db, photos),
                    CustomExercisesModule(db, training, photos),

                    StreaksModule(db, photos),
                    StreakSavedDaysModule(db, photos),

                    BodyweightModule(db),
                    BodyMeasurementsModule(db, bodyFat = true),
                    BodyMeasurementsModule(db, bodyFat = false),

                    FoodLogModule(db),
                    CustomIngredientsModule(db),
                    MacroGoalsModule(macroTargets)

                    // FUTURE: register every new feature's data here, one DataModule per kind of
                    // data (e.g. MealPlansModule(db), MealPresetsModule(db), AppSettingsModule(...)).
                    // Add its DataApp entry first. Once registered it appears on the Delete, Export
                    // and Import pages, in the Home menu and in its app's pre-filtered menu.
                )
            )
        }
    }
}
