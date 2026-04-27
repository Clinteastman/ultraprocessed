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
    version = 3,
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

        fun build(context: Context): AppDatabase = Room
            .databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_2_3)
            .build()
    }
}
