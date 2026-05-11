package com.ultraprocessed.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single bathroom visit. Severity scales are deliberately small (0-3)
 * so logging is one-tap; Bristol uses the canonical 1-7 stool-form scale.
 *
 * Mirrors backend/app/models.py:BowelEvent so sync is mechanical.
 */
@Entity(
    tableName = "bowel_event",
    indices = [
        Index(value = ["occurred_at"])
    ]
)
data class BowelEvent(
    @PrimaryKey
    @ColumnInfo(name = "client_uuid")
    val clientUuid: String,

    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,

    val bristol: Int,                 // 1..7
    val urgency: Int = 0,             // 0..3
    val completeness: Int = 2,        // 0=incomplete, 1=partial, 2=complete
    val pain: Int = 0,                // 0..3
    val blood: Boolean = false,
    val mucus: Boolean = false,
    val notes: String? = null,

    @ColumnInfo(name = "sync_state")
    val syncState: SyncState = SyncState.PENDING,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
