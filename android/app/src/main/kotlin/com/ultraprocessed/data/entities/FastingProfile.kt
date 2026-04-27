package com.ultraprocessed.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A fasting schedule. Eating window is defined as minutes-after-midnight
 * in the user's local time, which avoids any timezone math at the storage
 * layer. Domain code computes the next-eat-at instant from this.
 *
 * For 16:8 with eating window 12:00-20:00, start=720, end=1200.
 * For OMAD with a one-hour eating window from 17:00-18:00, start=1020, end=1080.
 */
@Entity(tableName = "fasting_profile")
data class FastingProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    @ColumnInfo(name = "schedule_type")
    val scheduleType: ScheduleType,

    @ColumnInfo(name = "eating_window_start_minutes")
    val eatingWindowStartMinutes: Int,

    @ColumnInfo(name = "eating_window_end_minutes")
    val eatingWindowEndMinutes: Int,

    val active: Boolean = false
)
