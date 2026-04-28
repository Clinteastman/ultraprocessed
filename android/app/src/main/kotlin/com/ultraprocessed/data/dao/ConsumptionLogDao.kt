package com.ultraprocessed.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.ultraprocessed.data.entities.ConsumptionLog
import com.ultraprocessed.data.entities.FoodEntry
import com.ultraprocessed.data.entities.SyncState
import kotlinx.coroutines.flow.Flow

/**
 * A consumption log paired with the food entry it refers to. Used by the
 * history and dashboard surfaces so we never have to do an n+1 query.
 */
data class ConsumptionWithFood(
    @Embedded val log: ConsumptionLog,
    @Relation(
        parentColumn = "food_client_uuid",
        entityColumn = "client_uuid"
    )
    val food: FoodEntry
)

@Dao
interface ConsumptionLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: ConsumptionLog)

    @Update
    suspend fun update(log: ConsumptionLog)

    @Query("SELECT * FROM consumption_log WHERE client_uuid = :uuid LIMIT 1")
    suspend fun getByClientUuid(uuid: String): ConsumptionLog?

    @Transaction
    @Query(
        """
        SELECT * FROM consumption_log
        WHERE eaten_at BETWEEN :fromMs AND :toMs
        ORDER BY eaten_at DESC
        """
    )
    fun observeRange(fromMs: Long, toMs: Long): Flow<List<ConsumptionWithFood>>

    @Transaction
    @Query(
        """
        SELECT * FROM consumption_log
        ORDER BY eaten_at DESC
        LIMIT :limit
        """
    )
    fun observeRecent(limit: Int = 200): Flow<List<ConsumptionWithFood>>

    @Transaction
    @Query(
        """
        SELECT * FROM consumption_log
        WHERE lat IS NOT NULL AND lng IS NOT NULL
        ORDER BY eaten_at DESC
        LIMIT :limit
        """
    )
    fun observeLocated(limit: Int = 1000): Flow<List<ConsumptionWithFood>>

    @Query("SELECT * FROM consumption_log WHERE sync_state = :state")
    suspend fun pending(state: SyncState = SyncState.PENDING): List<ConsumptionLog>

    @Query("SELECT COUNT(*) FROM consumption_log WHERE sync_state = :state")
    fun observePendingCount(state: SyncState = SyncState.PENDING): Flow<Int>

    @Query("SELECT MAX(eaten_at) FROM consumption_log")
    suspend fun lastEatenAt(): Long?

    @Query(
        """
        SELECT COALESCE(SUM(kcal_consumed_snapshot), 0.0) FROM consumption_log
        WHERE eaten_at BETWEEN :fromMs AND :toMs
        """
    )
    fun observeKcalSumInRange(fromMs: Long, toMs: Long): Flow<Double>

    @Query("DELETE FROM consumption_log WHERE client_uuid = :uuid")
    suspend fun deleteByClientUuid(uuid: String)

    /**
     * Tags every log that doesn't yet have a location with the given coords
     * + label, and re-marks them PENDING so the next sync re-uploads them.
     * Returns the number of rows affected.
     */
    @Query(
        """
        UPDATE consumption_log
        SET lat = :lat, lng = :lng, location_label = :label, sync_state = 'PENDING'
        WHERE lat IS NULL AND lng IS NULL
        """
    )
    suspend fun backfillMissingLocation(lat: Double, lng: Double, label: String): Int

    /**
     * Re-applies the given coords to every log already tagged with [label]
     * (case-sensitive). Used when the saved Home coords were wrong and need
     * to be propagated to past entries that already have the Home label.
     * Re-marks rows PENDING so the next sync pushes the corrected coords.
     */
    @Query(
        """
        UPDATE consumption_log
        SET lat = :lat, lng = :lng, sync_state = 'PENDING'
        WHERE location_label = :label
        """
    )
    suspend fun retagLocation(lat: Double, lng: Double, label: String): Int
}
