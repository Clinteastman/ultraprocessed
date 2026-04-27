package com.ultraprocessed.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single eating event. References a FoodEntry by client UUID so logs
 * remain useful even before sync gives the food a server id.
 */
@Entity(
    tableName = "consumption_log",
    foreignKeys = [
        ForeignKey(
            entity = FoodEntry::class,
            parentColumns = ["client_uuid"],
            childColumns = ["food_client_uuid"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["food_client_uuid"]),
        Index(value = ["eaten_at"]),
        Index(value = ["server_id"], unique = true)
    ]
)
data class ConsumptionLog(
    @PrimaryKey
    @ColumnInfo(name = "client_uuid")
    val clientUuid: String,

    @ColumnInfo(name = "server_id")
    val serverId: Long? = null,

    @ColumnInfo(name = "food_client_uuid")
    val foodClientUuid: String,

    /** 0..100 inclusive. */
    @ColumnInfo(name = "percentage_eaten")
    val percentageEaten: Int,

    @ColumnInfo(name = "eaten_at")
    val eatenAt: Long,

    val lat: Double? = null,
    val lng: Double? = null,

    @ColumnInfo(name = "location_label")
    val locationLabel: String? = null,

    /** Calories at the moment we logged it; immune to later FoodEntry edits. */
    @ColumnInfo(name = "kcal_consumed_snapshot")
    val kcalConsumedSnapshot: Double? = null,

    @ColumnInfo(name = "sync_state")
    val syncState: SyncState = SyncState.PENDING,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
