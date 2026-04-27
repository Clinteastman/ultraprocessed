package com.ultraprocessed.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A food the user has scanned or otherwise identified. Multiple
 * `ConsumptionLog` rows can reference one entry (e.g., scanning the same
 * cereal box across multiple meals).
 */
@Entity(
    tableName = "food_entry",
    indices = [
        Index(value = ["barcode"]),
        Index(value = ["server_id"], unique = true)
    ]
)
data class FoodEntry(
    @PrimaryKey
    @ColumnInfo(name = "client_uuid")
    val clientUuid: String,

    @ColumnInfo(name = "server_id")
    val serverId: Long? = null,

    val name: String,
    val brand: String? = null,
    val barcode: String? = null,

    @ColumnInfo(name = "nova_class")
    val novaClass: Int,

    @ColumnInfo(name = "nova_rationale")
    val novaRationale: String,

    @ColumnInfo(name = "kcal_per_100g")
    val kcalPer100g: Double? = null,

    @ColumnInfo(name = "kcal_per_unit")
    val kcalPerUnit: Double? = null,

    @ColumnInfo(name = "serving_description")
    val servingDescription: String? = null,

    @ColumnInfo(name = "image_path")
    val imagePath: String? = null,

    @ColumnInfo(name = "image_url")
    val imageUrl: String? = null,

    @ColumnInfo(name = "ingredients_json")
    val ingredientsJson: String = "[]",

    @ColumnInfo(name = "nutrients_json")
    val nutrientsJson: String? = null,

    val source: FoodSource,
    val confidence: Double = 1.0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "sync_state")
    val syncState: SyncState = SyncState.PENDING
)
