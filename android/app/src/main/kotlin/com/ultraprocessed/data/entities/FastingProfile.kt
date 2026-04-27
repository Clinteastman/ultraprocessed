package com.ultraprocessed.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A fasting schedule. Two patterns are encoded:
 *
 *  - Time-restricted (TRE): SIXTEEN_EIGHT, EIGHTEEN_SIX, TWENTY_FOUR, OMAD,
 *    CUSTOM. Daily eating window in minutes-after-midnight in the user's
 *    local time. restricted_days_mask is 0; restricted_kcal_target is null.
 *
 *  - Multi-day calorie-restriction: FIVE_TWO, FOUR_THREE, ADF. Eating window
 *    fields ignored; restricted_days_mask is a 7-bit Mon..Sun bitmask of
 *    which weekdays are restricted-calorie days; restricted_kcal_target is
 *    the kcal cap on a restricted day (typically 500-600).
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

    /** 7-bit Mon..Sun bitmask. 0 = unused (TRE schedules). */
    @ColumnInfo(name = "restricted_days_mask")
    val restrictedDaysMask: Int = 0,

    /** kcal cap on a restricted day (5:2/4:3/ADF). Null if unused. */
    @ColumnInfo(name = "restricted_kcal_target")
    val restrictedKcalTarget: Int? = null,

    val active: Boolean = false
)
