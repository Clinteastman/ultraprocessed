package com.ultraprocessed.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * End-of-day rollup. One row per (user, day) — stored as a 'YYYY-MM-DD'
 * string in the user's local timezone so DST changes don't merge rows.
 */
@Entity(
    tableName = "daily_checkin",
    indices = [
        Index(value = ["day"], unique = true)
    ]
)
data class DailyCheckin(
    @PrimaryKey
    @ColumnInfo(name = "client_uuid")
    val clientUuid: String,

    val day: String,                  // 'YYYY-MM-DD' local

    @ColumnInfo(name = "bloating_overall")
    val bloatingOverall: Int = 0,     // 0..3

    @ColumnInfo(name = "heartburn_overall")
    val heartburnOverall: Int = 0,    // 0..3

    @ColumnInfo(name = "gut_score")
    val gutScore: Int? = null,        // 1..10

    val mood: Int? = null,            // 0..3
    val stress: Int? = null,          // 0..3
    val period: Boolean = false,
    val notes: String? = null,

    @ColumnInfo(name = "sync_state")
    val syncState: SyncState = SyncState.PENDING,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
