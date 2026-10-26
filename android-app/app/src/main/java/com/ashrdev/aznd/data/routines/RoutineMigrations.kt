package com.ashrdev.aznd.data.routines

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `streaks` ADD COLUMN `trackMode` TEXT NOT NULL DEFAULT 'manual_break'")
        db.execSQL("ALTER TABLE `streaks` ADD COLUMN `milestoneAlerts` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `streaks` ADD COLUMN `routineId` INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `streak_checkins` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`streakId` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
                "FOREIGN KEY(`streakId`) REFERENCES `streaks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_streak_checkins_streakId_date` ON `streak_checkins` (`streakId`, `date`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `routines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `type` TEXT NOT NULL, `workoutId` INTEGER NOT NULL, `frequency` TEXT NOT NULL, " +
                "`weekdays` INTEGER NOT NULL, `perWeek` INTEGER NOT NULL, `startDate` TEXT NOT NULL, " +
                "`photoUri` TEXT, `bannerUri` TEXT, `milestones` TEXT NOT NULL, `milestoneAlerts` INTEGER NOT NULL, " +
                "`trackAsStreak` INTEGER NOT NULL, `trackMode` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `routine_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`routineId` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
                "FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_routine_logs_routineId_date` ON `routine_logs` (`routineId`, `date`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerType` TEXT NOT NULL, `ownerId` INTEGER NOT NULL, `minuteOfDay` INTEGER NOT NULL, " +
                "`weekdays` INTEGER NOT NULL, `message` TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_ownerType_ownerId` ON `reminders` (`ownerType`, `ownerId`)")
    }
}

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `routines` ADD COLUMN `everyCount` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `routines` ADD COLUMN `everyUnit` TEXT NOT NULL DEFAULT 'days'")
        db.execSQL("ALTER TABLE `routine_logs` ADD COLUMN `at` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `streak_checkins` ADD COLUMN `at` INTEGER NOT NULL DEFAULT 0")
    }
}
