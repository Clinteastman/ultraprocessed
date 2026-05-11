package com.ultraprocessed.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ultraprocessed.data.entities.BowelEvent
import com.ultraprocessed.data.entities.SyncState
import kotlinx.coroutines.flow.Flow

@Dao
interface BowelEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: BowelEvent)

    @Query("SELECT * FROM bowel_event WHERE client_uuid = :uuid LIMIT 1")
    suspend fun getByClientUuid(uuid: String): BowelEvent?

    @Query(
        """
        SELECT * FROM bowel_event
        WHERE occurred_at BETWEEN :fromMs AND :toMs
        ORDER BY occurred_at DESC
        """
    )
    fun observeRange(fromMs: Long, toMs: Long): Flow<List<BowelEvent>>

    @Query(
        """
        SELECT * FROM bowel_event
        WHERE occurred_at BETWEEN :fromMs AND :toMs
        ORDER BY occurred_at ASC
        """
    )
    suspend fun inRange(fromMs: Long, toMs: Long): List<BowelEvent>

    @Query(
        """
        SELECT * FROM bowel_event
        ORDER BY occurred_at DESC
        LIMIT :limit
        """
    )
    fun observeRecent(limit: Int = 200): Flow<List<BowelEvent>>

    @Query("SELECT * FROM bowel_event WHERE sync_state = :state")
    suspend fun pending(state: SyncState = SyncState.PENDING): List<BowelEvent>

    @Query("SELECT COUNT(*) FROM bowel_event WHERE sync_state = :state")
    fun observePendingCount(state: SyncState = SyncState.PENDING): Flow<Int>

    @Query("DELETE FROM bowel_event WHERE client_uuid = :uuid")
    suspend fun deleteByClientUuid(uuid: String)
}
