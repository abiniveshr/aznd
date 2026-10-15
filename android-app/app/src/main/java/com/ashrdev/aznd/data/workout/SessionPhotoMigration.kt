package com.ashrdev.aznd.data.workout

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds the optional photo of a running/finished session to logged_session. */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `logged_session` ADD COLUMN `photoUri` TEXT")
    }
}
