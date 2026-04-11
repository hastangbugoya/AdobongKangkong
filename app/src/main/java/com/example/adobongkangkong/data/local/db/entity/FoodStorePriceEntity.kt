package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents the relationship between a Food and a Store with normalized pricing.
 *
 * Key design decisions:
 * - Price belongs to the relationship (food + store), not FoodEntity.
 * - Surrogate PK used for Room simplicity and future extensibility.
 * - Unique constraint on (foodId, storeId) ensures only one active pricing snapshot per pair.
 *
 * Current scope:
 * - Persist normalized comparable estimates only.
 * - Keep basis separate:
 *   - pricePer100g for mass-based comparison
 *   - pricePer100ml for volume-based comparison
 * - No package history yet.
 * - No grams↔mL guessing.
 *
 * Practical product rule:
 * - User enters the package quantity and total price that is most useful to them.
 * - App normalizes immediately and stores the normalized estimate for future use.
 *
 * Future-safe:
 * - Can later evolve into price history by adding snapshot rows or splitting table.
 */
@Entity(
    tableName = "food_store_prices",
    foreignKeys = [
        ForeignKey(
            entity = FoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["foodId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["storeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["foodId"]),
        Index(value = ["storeId"]),
        Index(value = ["foodId", "storeId"], unique = true)
    ]
)
data class FoodStorePriceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * FK → FoodEntity.id
     */
    val foodId: Long,

    /**
     * FK → StoreEntity.id
     */
    val storeId: Long,

    /**
     * Normalized estimated price per 100 grams.
     *
     * Rules:
     * - Nullable because a row may only have a volume-based estimate.
     * - Must be positive when present.
     * - App must not infer this from volume.
     */
    val pricePer100g: Double?,

    /**
     * Normalized estimated price per 100 milliliters.
     *
     * Rules:
     * - Nullable because a row may only have a mass-based estimate.
     * - Must be positive when present.
     * - App must not infer this from mass.
     */
    val pricePer100ml: Double?,

    /**
     * Timestamp of when this normalized pricing snapshot was last updated.
     */
    val updatedAtEpochMs: Long
)

/**
 * =============================================================================
 * FUTURE-YOU / FUTURE AI NOTES — DO NOT DELETE
 * =============================================================================
 *
 * Why normalized-only persistence
 * - The app needs practical comparable estimates, not package-level retail history.
 * - Package size used for entry is an input mechanism, not the persisted model.
 *
 * Why keep g and mL separate
 * - Mass and volume are not interchangeable without explicit density.
 * - Mixing them would create misleading averages and invalid comparisons.
 *
 * Why unique (foodId, storeId)
 * - Prevents duplicate rows like:
 *     (chicken breast, Costco) x 3 entries
 * - Forces updates instead of parallel snapshots in this first pass.
 *
 * Future evolution paths
 * - Add package metadata fields for UI audit/debug only
 * - Add effectiveDate / updatedAt detail expansion
 * - OR introduce FoodStorePriceHistoryEntity
 * - Keep this table as latest snapshot for fast reads
 *
 * DAO implications
 * - Averages must remain basis-specific:
 *   - AVG(pricePer100g)
 *   - AVG(pricePer100ml)
 * - Do NOT collapse these into one blended "price".
 *
 * Important guardrails
 * - Do NOT move pricing fields to FoodEntity.
 * - Do NOT duplicate store info here.
 * - Do NOT guess grams from mL or mL from grams.
 * - At least one normalized path should be present before saving a row.
 */