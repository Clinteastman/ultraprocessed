package com.ultraprocessed.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ultraprocessed.data.entities.FastingProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface FastingProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: FastingProfile): Long

    @Update
    suspend fun update(profile: FastingProfile)

    @Query("SELECT * FROM fasting_profile WHERE active = 1 LIMIT 1")
    suspend fun getActive(): FastingProfile?

    @Query("SELECT * FROM fasting_profile WHERE active = 1 LIMIT 1")
    fun observeActive(): Flow<FastingProfile?>

    @Query("SELECT * FROM fasting_profile ORDER BY id ASC")
    fun observeAll(): Flow<List<FastingProfile>>

    @Query("UPDATE fasting_profile SET active = 0")
    suspend fun deactivateAll()

    @Query("UPDATE fasting_profile SET active = 1 WHERE id = :id")
    suspend fun activate(id: Long)
}
