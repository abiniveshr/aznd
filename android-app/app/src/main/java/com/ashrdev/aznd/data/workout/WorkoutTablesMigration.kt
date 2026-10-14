package com.ashrdev.aznd.data.workout

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "CREATE TEMP TABLE `_old_workouts` AS SELECT `id`, `name` FROM `Workouts`",
            "CREATE TEMP TABLE `_old_exercises` AS SELECT `id`, `WorkoutId`, `name`, `orderIndex` FROM `exercises`",
            "CREATE TEMP TABLE `_old_exercise_sets` AS SELECT `id`, `exerciseId`, `mode`, `orderIndex` FROM `exercise_sets`",
            "CREATE TEMP TABLE `_old_sessions` AS SELECT `id`, `WorkoutId`, `WorkoutName`, `startedAt`, `finishedAt`, `photoUri` FROM `sessions`",
            "CREATE TEMP TABLE `_old_logged_sets` AS SELECT `id`, `sessionId`, `exerciseName`, `mode`, `value`, `weight`, `rpe`, `orderIndex` FROM `logged_sets`",
            "CREATE TEMP TABLE `_old_active_session` AS SELECT `id`, `WorkoutId`, `WorkoutName`, `startedAt`, `photoUri` FROM `active_session`",
            "CREATE TEMP TABLE `_old_active_sets` AS SELECT `id`, `sessionId`, `exerciseName`, `exerciseIndex`, `setIndex`, `mode`, `valueText`, `weightText`, `rpeText` FROM `active_sets`",
            "CREATE TEMP TABLE `_old_sequences` AS SELECT CASE WHEN `name` = 'Workouts' COLLATE NOCASE THEN 'workouts' ELSE `name` END AS `name`, `seq` FROM `sqlite_sequence` WHERE `name` COLLATE NOCASE IN ('Workouts', 'exercises', 'exercise_sets', 'sessions', 'logged_sets', 'active_sets')",
            "DROP TABLE `active_sets`",
            "DROP TABLE `logged_sets`",
            "DROP TABLE `exercise_sets`",
            "DROP TABLE `active_session`",
            "DROP TABLE `sessions`",
            "DROP TABLE `exercises`",
            "DROP TABLE `Workouts`",
            "CREATE TABLE IF NOT EXISTS `workouts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `workoutId` INTEGER NOT NULL, `name` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, FOREIGN KEY(`workoutId`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_exercises_workoutId` ON `exercises` (`workoutId`)",
            "CREATE TABLE IF NOT EXISTS `exercise_sets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `exerciseId` INTEGER NOT NULL, `mode` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_exercise_sets_exerciseId` ON `exercise_sets` (`exerciseId`)",
            "CREATE TABLE IF NOT EXISTS `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `workoutId` INTEGER NOT NULL, `workoutName` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `finishedAt` INTEGER NOT NULL, `photoUri` TEXT)",
            "CREATE TABLE IF NOT EXISTS `logged_sets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `exerciseName` TEXT NOT NULL, `mode` TEXT NOT NULL, `value` INTEGER NOT NULL, `weight` REAL NOT NULL, `rpe` REAL, `orderIndex` INTEGER NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_logged_sets_sessionId` ON `logged_sets` (`sessionId`)",
            "CREATE TABLE IF NOT EXISTS `active_session` (`id` INTEGER NOT NULL, `workoutId` INTEGER NOT NULL, `workoutName` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `photoUri` TEXT, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `active_sets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `exerciseName` TEXT NOT NULL, `exerciseIndex` INTEGER NOT NULL, `setIndex` INTEGER NOT NULL, `mode` TEXT NOT NULL, `valueText` TEXT NOT NULL, `weightText` TEXT NOT NULL, `rpeText` TEXT NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `active_session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_active_sets_sessionId` ON `active_sets` (`sessionId`)",
            "INSERT INTO `workouts` (`id`, `name`) SELECT `id`, `name` FROM `_old_workouts`",
            "INSERT INTO `exercises` (`id`, `workoutId`, `name`, `orderIndex`) SELECT `id`, `WorkoutId`, `name`, `orderIndex` FROM `_old_exercises`",
            "INSERT INTO `exercise_sets` (`id`, `exerciseId`, `mode`, `orderIndex`) SELECT `id`, `exerciseId`, `mode`, `orderIndex` FROM `_old_exercise_sets`",
            "INSERT INTO `sessions` (`id`, `workoutId`, `workoutName`, `startedAt`, `finishedAt`, `photoUri`) SELECT `id`, `WorkoutId`, `WorkoutName`, `startedAt`, `finishedAt`, `photoUri` FROM `_old_sessions`",
            "INSERT INTO `logged_sets` (`id`, `sessionId`, `exerciseName`, `mode`, `value`, `weight`, `rpe`, `orderIndex`) SELECT `id`, `sessionId`, `exerciseName`, `mode`, `value`, `weight`, `rpe`, `orderIndex` FROM `_old_logged_sets`",
            "INSERT INTO `active_session` (`id`, `workoutId`, `workoutName`, `startedAt`, `photoUri`) SELECT `id`, `WorkoutId`, `WorkoutName`, `startedAt`, `photoUri` FROM `_old_active_session`",
            "INSERT INTO `active_sets` (`id`, `sessionId`, `exerciseName`, `exerciseIndex`, `setIndex`, `mode`, `valueText`, `weightText`, `rpeText`) SELECT `id`, `sessionId`, `exerciseName`, `exerciseIndex`, `setIndex`, `mode`, `valueText`, `weightText`, `rpeText` FROM `_old_active_sets`",
            "UPDATE `sqlite_sequence` SET `seq` = (SELECT `seq` FROM `_old_sequences` WHERE `_old_sequences`.`name` = `sqlite_sequence`.`name`) WHERE `name` IN (SELECT `name` FROM `_old_sequences`) AND `seq` < (SELECT `seq` FROM `_old_sequences` WHERE `_old_sequences`.`name` = `sqlite_sequence`.`name`)",
            "INSERT INTO `sqlite_sequence` (`name`, `seq`) SELECT `name`, `seq` FROM `_old_sequences` WHERE `name` NOT IN (SELECT `name` FROM `sqlite_sequence`)",
            "DROP TABLE `_old_workouts`",
            "DROP TABLE `_old_exercises`",
            "DROP TABLE `_old_exercise_sets`",
            "DROP TABLE `_old_sessions`",
            "DROP TABLE `_old_logged_sets`",
            "DROP TABLE `_old_active_session`",
            "DROP TABLE `_old_active_sets`",
            "DROP TABLE `_old_sequences`"
        ).forEach { db.execSQL(it) }
    }
}
