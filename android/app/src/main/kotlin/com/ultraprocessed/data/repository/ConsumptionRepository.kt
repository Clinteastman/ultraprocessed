package com.ultraprocessed.data.repository

import com.ultraprocessed.data.dao.ConsumptionLogDao
import com.ultraprocessed.data.dao.ConsumptionWithFood
import com.ultraprocessed.data.entities.ConsumptionLog
import kotlinx.coroutines.flow.Flow

class ConsumptionRepository(private val dao: ConsumptionLogDao) {

    suspend fun log(entry: ConsumptionLog) = dao.upsert(entry)

    fun observeRange(fromMs: Long, toMs: Long): Flow<List<ConsumptionWithFood>> =
        dao.observeRange(fromMs, toMs)

    fun observeRecent(limit: Int = 200): Flow<List<ConsumptionWithFood>> =
        dao.observeRecent(limit)

    fun observeKcalSumInRange(fromMs: Long, toMs: Long): Flow<Double> =
        dao.observeKcalSumInRange(fromMs, toMs)

    suspend fun lastEatenAt(): Long? = dao.lastEatenAt()

    suspend fun pending(): List<ConsumptionLog> = dao.pending()

    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    suspend fun delete(uuid: String) = dao.deleteByClientUuid(uuid)
}
