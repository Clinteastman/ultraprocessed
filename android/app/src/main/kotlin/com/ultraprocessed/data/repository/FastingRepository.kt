package com.ultraprocessed.data.repository

import com.ultraprocessed.data.dao.FastingProfileDao
import com.ultraprocessed.data.entities.FastingProfile
import kotlinx.coroutines.flow.Flow

class FastingRepository(private val dao: FastingProfileDao) {

    suspend fun save(profile: FastingProfile): Long = dao.upsert(profile)

    suspend fun getActive(): FastingProfile? = dao.getActive()

    fun observeActive(): Flow<FastingProfile?> = dao.observeActive()

    fun observeAll(): Flow<List<FastingProfile>> = dao.observeAll()

    suspend fun setActive(id: Long) {
        dao.deactivateAll()
        dao.activate(id)
    }
}
