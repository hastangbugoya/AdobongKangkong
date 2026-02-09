package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Barcode → Food mapping table.
 *
 * Invariants:
 * - barcode is unique across DB (primary key).
 * - A food may have multiple barcodes.
 * - USDA mappings can override user mappings at the same barcode.
 */
@Entity(
    tableName = "food_barcodes",
    indices = [
        Index(value = ["foodId"]),
        Index(value = ["usdaFdcId"]),
        Index(value = ["source"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = FoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["foodId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FoodBarcodeEntity(
    /** Normalized barcode digits (primary key). */
    @PrimaryKey
    val barcode: String,

    /** FK -> foods.id */
    val foodId: Long,

    /** Where this mapping came from. */
    val source: BarcodeMappingSource,

    /** Present when source=USDA. */
    val usdaFdcId: Long? = null,

    /** USDA publishedDate (yyyy-MM-dd) associated with this barcode (when known). */
    val usdaPublishedDateIso: String? = null,

    /** When this mapping was created (USDA first seen OR user assigned). */
    val assignedAtEpochMs: Long,

    /** Last time this barcode was scanned/resolved. */
    val lastSeenAtEpochMs: Long
)

enum class BarcodeMappingSource {
    USDA,
    USER_ASSIGNED
}
