package com.ultraprocessed.data.repository

import com.ultraprocessed.data.dao.BowelEventDao
import com.ultraprocessed.data.dao.DailyCheckinDao
import com.ultraprocessed.data.dao.SymptomEventDao
import com.ultraprocessed.data.entities.BowelEvent
import com.ultraprocessed.data.entities.DailyCheckin
import com.ultraprocessed.data.entities.SymptomEvent
import com.ultraprocessed.data.entities.SymptomKind
import kotlinx.coroutines.flow.Flow

/**
 * Single repository covering all three IBS event tables. They're
 * unified here because the UI almost always presents them together
 * (the gut tracker page shows bowel + symptoms + check-ins on a
 * combined timeline).
 */
class IbsRepository(
    private val bowelDao: BowelEventDao,
    private val symptomDao: SymptomEventDao,
    private val checkinDao: DailyCheckinDao
) {
    // ---- Bowel events ----
    suspend fun logBowel(event: BowelEvent) = bowelDao.upsert(event)
    fun observeBowelRange(fromMs: Long, toMs: Long): Flow<List<BowelEvent>> =
        bowelDao.observeRange(fromMs, toMs)
    fun observeBowelRecent(limit: Int = 200): Flow<List<BowelEvent>> =
        bowelDao.observeRecent(limit)
    suspend fun pendingBowel(): List<BowelEvent> = bowelDao.pending()
    suspend fun getBowel(uuid: String): BowelEvent? = bowelDao.getByClientUuid(uuid)
    suspend fun deleteBowel(uuid: String) = bowelDao.deleteByClientUuid(uuid)
    suspend fun bowelInRange(fromMs: Long, toMs: Long): List<BowelEvent> =
        bowelDao.inRange(fromMs, toMs)

    // ---- Symptoms ----
    suspend fun logSymptom(event: SymptomEvent) = symptomDao.upsert(event)
    fun observeSymptomRange(fromMs: Long, toMs: Long): Flow<List<SymptomEvent>> =
        symptomDao.observeRange(fromMs, toMs)
    fun observeSymptomKindInRange(kind: SymptomKind, fromMs: Long, toMs: Long): Flow<List<SymptomEvent>> =
        symptomDao.observeByKindInRange(kind, fromMs, toMs)
    fun observeSymptomRecent(limit: Int = 200): Flow<List<SymptomEvent>> =
        symptomDao.observeRecent(limit)
    suspend fun pendingSymptoms(): List<SymptomEvent> = symptomDao.pending()
    suspend fun getSymptom(uuid: String): SymptomEvent? = symptomDao.getByClientUuid(uuid)
    suspend fun deleteSymptom(uuid: String) = symptomDao.deleteByClientUuid(uuid)
    suspend fun symptomsInRange(fromMs: Long, toMs: Long): List<SymptomEvent> =
        symptomDao.inRange(fromMs, toMs)

    // ---- Daily check-ins ----
    suspend fun saveCheckin(checkin: DailyCheckin) = checkinDao.upsert(checkin)
    suspend fun getCheckin(day: String): DailyCheckin? = checkinDao.getByDay(day)
    fun observeCheckin(day: String): Flow<DailyCheckin?> = checkinDao.observeByDay(day)
    fun observeCheckinRecent(limit: Int = 60): Flow<List<DailyCheckin>> =
        checkinDao.observeRecent(limit)
    suspend fun pendingCheckins(): List<DailyCheckin> = checkinDao.pending()
}
