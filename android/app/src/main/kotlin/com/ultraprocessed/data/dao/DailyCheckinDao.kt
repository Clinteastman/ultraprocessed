package com.ultraprocessed.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ultraprocessed.data.entities.DailyCheckin
import com.ultraprocessed.data.entities.SyncState
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyCheckinDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(checkin: DailyCheckin)

    @Query("SELECT * FROM daily_checkin WHERE day = :day LIMIT 1")
    suspend fun getByDay(day: String): DailyCheckin?

    @Query("SELECT * FROM daily_checkin WHERE day = :day LIMIT 1")
    fun observeByDay(day: String): Flow<DailyCheckin?>

    @Query("SELECT * FROM daily_checkin ORDER BY day DESC LIMIT :limit")
    fun observeRecent(limit: Int = 60): Flow<List<DailyCheckin>>

    @Query("SELECT * FROM daily_checkin WHERE sync_state = :state")
    suspend fun pending(state: SyncState = SyncState.PENDING): List<DailyCheckin>

    @Query("DELETE FROM daily_checkin WHERE client_uuid = :uuid")
    suspend fun deleteByClientUuid(uuid: String)
}
