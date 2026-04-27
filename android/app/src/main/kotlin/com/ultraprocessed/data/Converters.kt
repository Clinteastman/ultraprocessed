package com.ultraprocessed.data

import androidx.room.TypeConverter
import com.ultraprocessed.data.entities.FoodSource
import com.ultraprocessed.data.entities.ScheduleType
import com.ultraprocessed.data.entities.SyncState

/**
 * Room type converters for the enums used across entities. Stored as
 * stable string values rather than ordinals so reordering enum members
 * never silently corrupts data.
 */
class Converters {

    @TypeConverter
    fun fromSyncState(value: SyncState): String = value.name

    @TypeConverter
    fun toSyncState(value: String): SyncState = SyncState.valueOf(value)

    @TypeConverter
    fun fromFoodSource(value: FoodSource): String = value.name

    @TypeConverter
    fun toFoodSource(value: String): FoodSource = FoodSource.valueOf(value)

    @TypeConverter
    fun fromScheduleType(value: ScheduleType): String = value.name

    @TypeConverter
    fun toScheduleType(value: String): ScheduleType = ScheduleType.valueOf(value)
}
