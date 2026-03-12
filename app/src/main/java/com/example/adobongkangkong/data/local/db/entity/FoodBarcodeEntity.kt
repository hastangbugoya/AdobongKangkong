package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.adobongkangkong.domain.model.ServingUnit

/**
 * Barcode → Food mapping table.
 *
 * Purpose
 * -------
 * Allows multiple barcodes (different packaging) to map to the same Food entity.
 *
 * Example:
 * - Coke 12 oz can
 * - Coke 20 oz bottle
 * - Coke 2L bottle
 *
 * All share the same nutrition identity but may have different packaging metadata.
 *
 * Invariants
 * ----------
 * - barcode is unique across DB (primary key).
 * - A food may have multiple barcodes.
 * - USDA mappings can override user mappings at the same barcode.
 *
 * Design rule
 * -----------
 * Food = nutrition identity.
 * Barcode row = package variant.
 *
 * Barcode rows may override packaging fields such as:
 * - servings per package
 * - household serving text
 * - serving size/unit for this package
 *
 * Nutrition values must NEVER be overridden here.
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

    /** Present when source = USDA. */
    val usdaFdcId: Long? = null,

    /** USDA publishedDate (yyyy-MM-dd) associated with this barcode (when known). */
    val usdaPublishedDateIso: String? = null,

    /** USDA modifiedDate (yyyy-MM-dd) associated with this barcode (when known). */
    val usdaModifiedDateIso: String? = null,

    /**
     * Package-specific override fields
     *
     * These override Food defaults when this barcode is used.
     *
     * Example:
     * - Yogurt single cup (1 serving)
     * - Yogurt 6-pack (6 servings)
     * - Yogurt 12-pack (12 servings)
     */

    /** Overrides Food.servingsPerPackage for this barcode/package. */
    val overrideServingsPerPackage: Double? = null,

    /** USDA household serving text such as "1 can (355 mL)" or "1 bottle". */
    val overrideHouseholdServingText: String? = null,

    /** Optional serving size override specific to this package. */
    val overrideServingSize: Double? = null,

    /** Optional serving unit override specific to this package. */
    val overrideServingUnit: ServingUnit? = null,

    /** When this mapping was created (USDA first seen OR user assigned). */
    val assignedAtEpochMs: Long,

    /** Last time this barcode was scanned/resolved. */
    val lastSeenAtEpochMs: Long
)

enum class BarcodeMappingSource {
    USDA,
    USER_ASSIGNED
}