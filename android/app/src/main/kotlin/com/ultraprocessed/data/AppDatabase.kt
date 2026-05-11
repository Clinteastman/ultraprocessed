package com.ultraprocessed.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ultraprocessed.data.dao.BowelEventDao
import com.ultraprocessed.data.dao.ConsumptionLogDao
import com.ultraprocessed.data.dao.DailyCheckinDao
import com.ultraprocessed.data.dao.FastingProfileDao
import com.ultraprocessed.data.dao.FoodEntryDao
import com.ultraprocessed.data.dao.SymptomEventDao
import com.ultraprocessed.data.entities.BowelEvent
import com.ultraprocessed.data.entities.ConsumptionLog
import com.ultraprocessed.data.entities.DailyCheckin
import com.ultraprocessed.data.entities.FastingProfile
import com.ultraprocessed.data.entities.FoodEntry
import com.ultraprocessed.data.entities.SymptomEvent

@Database(
    entities = [
        FoodEntry::class,
        ConsumptionLog::class,
        FastingProfile::class,
        BowelEvent::class,
        SymptomEvent::class,
        DailyCheckin::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun consumptionLogDao(): ConsumptionLogDao
    abstract fun fastingProfileDao(): FastingProfileDao
    abstract fun bowelEventDao(): BowelEventDao
    abstract fun symptomEventDao(): SymptomEventDao
    abstract fun dailyCheckinDao(): DailyCheckinDao

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

        /**
         * v5 -> v6: IBS tracking. Adds the three event tables (bowel,
         * symptom, daily check-in) plus FODMAP columns on food_entry.
         * Existing food rows default to UNKNOWN so the analyzer can
         * back-fill them on the next scan or via a manual edit.
         */
        private val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE food_entry ADD COLUMN fodmap_level TEXT NOT NULL DEFAULT 'UNKNOWN'"
                )
                db.execSQL(
                    "ALTER TABLE food_entry ADD COLUMN fodmap_tags_json TEXT NOT NULL DEFAULT '[]'"
                )
                db.execSQL("ALTER TABLE food_entry ADD COLUMN fodmap_notes TEXT")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS bowel_event (
                        client_uuid TEXT NOT NULL PRIMARY KEY,
                        occurred_at INTEGER NOT NULL,
                        bristol INTEGER NOT NULL,
                        urgency INTEGER NOT NULL DEFAULT 0,
                        completeness INTEGER NOT NULL DEFAULT 2,
                        pain INTEGER NOT NULL DEFAULT 0,
                        blood INTEGER NOT NULL DEFAULT 0,
                        mucus INTEGER NOT NULL DEFAULT 0,
                        notes TEXT,
                        sync_state TEXT NOT NULL DEFAULT 'PENDING',
                        created_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bowel_event_occurred_at ON bowel_event(occurred_at)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS symptom_event (
                        client_uuid TEXT NOT NULL PRIMARY KEY,
                        occurred_at INTEGER NOT NULL,
                        kind TEXT NOT NULL,
                        severity INTEGER NOT NULL,
                        duration_minutes INTEGER,
                        location TEXT,
                        notes TEXT,
                        sync_state TEXT NOT NULL DEFAULT 'PENDING',
                        created_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_symptom_event_occurred_at ON symptom_event(occurred_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_symptom_event_kind ON symptom_event(kind)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_checkin (
                        client_uuid TEXT NOT NULL PRIMARY KEY,
                        day TEXT NOT NULL,
                        bloating_overall INTEGER NOT NULL DEFAULT 0,
                        heartburn_overall INTEGER NOT NULL DEFAULT 0,
                        gut_score INTEGER,
                        mood INTEGER,
                        stress INTEGER,
                        period INTEGER NOT NULL DEFAULT 0,
                        notes TEXT,
                        sync_state TEXT NOT NULL DEFAULT 'PENDING',
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_daily_checkin_day ON daily_checkin(day)")
            }
        }

        fun build(context: Context): AppDatabase = Room
            .databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()
    }
}
