package com.ultraprocessed.data.repository

import com.ultraprocessed.data.dao.FoodEntryDao
import com.ultraprocessed.data.entities.FoodEntry
import kotlinx.coroutines.flow.Flow

class FoodRepository(private val dao: FoodEntryDao) {

    suspend fun upsert(entry: FoodEntry) = dao.upsert(entry)

    suspend fun get(uuid: String): FoodEntry? = dao.getByClientUuid(uuid)

    suspend fun getByBarcode(barcode: String): FoodEntry? = dao.getByBarcode(barcode)

    fun observeRecent(limit: Int = 50): Flow<List<FoodEntry>> = dao.observeRecent(limit)

    fun observeSearch(q: String, limit: Int = 30): Flow<List<FoodEntry>> =
        dao.observeSearch(q, limit)

    fun observe(uuid: String): Flow<FoodEntry?> = dao.observeByClientUuid(uuid)

    suspend fun pending(): List<FoodEntry> = dao.pending()

    suspend fun pendingImages(): List<FoodEntry> = dao.pendingImages()

    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    suspend fun delete(uuid: String) = dao.deleteByClientUuid(uuid)
}
