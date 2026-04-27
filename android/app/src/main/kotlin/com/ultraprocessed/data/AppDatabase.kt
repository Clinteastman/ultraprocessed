package com.ultraprocessed.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun consumptionLogDao(): ConsumptionLogDao
    abstract fun fastingProfileDao(): FastingProfileDao

    companion object {
        private const val DB_NAME = "ultraprocessed.db"

        fun build(context: Context): AppDatabase = Room
            .databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
            .fallbackToDestructiveMigration()
            .build()
    }
}
