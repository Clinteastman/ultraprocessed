package com.ultraprocessed.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ultraprocessed.data.entities.FoodEntry
import com.ultraprocessed.data.entities.SyncState
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: FoodEntry)

    @Update
    suspend fun update(entry: FoodEntry)

    @Query("SELECT * FROM food_entry WHERE client_uuid = :uuid LIMIT 1")
    suspend fun getByClientUuid(uuid: String): FoodEntry?

    @Query("SELECT * FROM food_entry WHERE barcode = :barcode ORDER BY updated_at DESC LIMIT 1")
    suspend fun getByBarcode(barcode: String): FoodEntry?

    @Query("SELECT * FROM food_entry WHERE client_uuid = :uuid LIMIT 1")
    fun observeByClientUuid(uuid: String): Flow<FoodEntry?>

    @Query("SELECT * FROM food_entry WHERE sync_state = :state")
    suspend fun pending(state: SyncState = SyncState.PENDING): List<FoodEntry>

    @Query("SELECT COUNT(*) FROM food_entry WHERE sync_state = :state")
    fun observePendingCount(state: SyncState = SyncState.PENDING): Flow<Int>

    @Query(
        "SELECT * FROM food_entry " +
        "WHERE image_synced = 0 " +
        "  AND image_path IS NOT NULL " +
        "  AND sync_state = :state"
    )
    suspend fun pendingImages(state: SyncState = SyncState.SYNCED): List<FoodEntry>

    @Query("SELECT * FROM food_entry ORDER BY updated_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<FoodEntry>>

    @Query(
        "SELECT * FROM food_entry " +
        "WHERE LOWER(name) LIKE '%' || LOWER(:q) || '%' " +
        "   OR LOWER(IFNULL(brand, '')) LIKE '%' || LOWER(:q) || '%' " +
        "ORDER BY updated_at DESC LIMIT :limit"
    )
    fun observeSearch(q: String, limit: Int = 30): Flow<List<FoodEntry>>

    @Query("DELETE FROM food_entry WHERE client_uuid = :uuid")
    suspend fun deleteByClientUuid(uuid: String)
}
