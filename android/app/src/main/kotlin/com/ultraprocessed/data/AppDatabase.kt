package com.ultraprocessed.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ultraprocessed.data.dao.ConsumptionLogDao
import com.ultraprocessed.data.dao.FastingProfileDao
import com.ultraprocessed.data.dao.FoodEntryDao
import com.ultraprocessed.data.entities.ConsumptionLog
import com.ultraprocessed.data.entities.FastingProfile
import com.ultraprocessed.data.entities.FoodEntry

@Database(
    entities = [
        FoodEntry::class,
        ConsumptionLog::class,
        FastingProfile::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun consumptionLogDao(): ConsumptionLogDao
    abstract fun fastingProfileDao(): FastingProfileDao

    companion object {
        private const val DB_NAME = "ultraprocessed.db"

        /**
         * v2 -> v3: gram-based portions. Adds package_grams + grams_per_unit
         * to food_entry and grams_eaten to consumption_log. Existing rows
         * keep working through their percentage_eaten + kcal_consumed_snapshot
         * fields; new rows populate the new columns.
         */
        private val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE food_entry ADD COLUMN grams_per_unit REAL")
                db.execSQL("ALTER TABLE food_entry ADD COLUMN package_grams REAL")
                db.execSQL("ALTER TABLE consumption_log ADD COLUMN grams_eaten REAL")
            }
        }

        /**
         * v3 -> v4: multi-day fasting patterns (5:2 / 4:3 / ADF). Adds
         * restricted_days_mask + restricted_kcal_target to fasting_profile.
         * No data migration needed; existing TRE-style rows keep mask=0.
         */
        private val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fasting_profile ADD COLUMN restricted_days_mask INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE fasting_profile ADD COLUMN restricted_kcal_target INTEGER")
            }
        }

        /**
         * v4 -> v5: image upload tracking. Adds image_synced flag to
         * food_entry so the sync coordinator can find foods whose local
         * imagePath still needs pushing to the backend.
         */
        private val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE food_entry ADD COLUMN image_synced INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun build(context: Context): AppDatabase = Room
            .databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
    }
}
