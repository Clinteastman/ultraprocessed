package com.ultraprocessed.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A non-bowel IBS symptom: bloating, heartburn, gas, cramping etc.
 * One row per discrete onset; lingering symptoms can use [durationMinutes].
 */
@Entity(
    tableName = "symptom_event",
    indices = [
        Index(value = ["occurred_at"]),
        Index(value = ["kind"])
    ]
)
data class SymptomEvent(
    @PrimaryKey
    @ColumnInfo(name = "client_uuid")
    val clientUuid: String,

    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,

    val kind: SymptomKind,
    val severity: Int,                // 0..3

    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int? = null,

    val location: String? = null,     // free-text, e.g. "upper abdomen"
    val notes: String? = null,

    @ColumnInfo(name = "sync_state")
    val syncState: SyncState = SyncState.PENDING,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
