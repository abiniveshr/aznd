package com.ashrdev.aznd.data.streaks

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds the optional quote and the per-streak milestone list to streaks. */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `streaks` ADD COLUMN `quote` TEXT")
        db.execSQL(
            "ALTER TABLE `streaks` ADD COLUMN `milestones` TEXT NOT NULL DEFAULT '10,25,50,100,200,300,400,500,1000'"
        )
    }
}

/**
 * Adds the Details-page banner photo. Images stored before this were 16:9 banners, so they are
 * copied across and keep showing where they did.
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `streaks` ADD COLUMN `bannerUri` TEXT")
        db.execSQL("UPDATE `streaks` SET `bannerUri` = `photoUri`")
    }
}
