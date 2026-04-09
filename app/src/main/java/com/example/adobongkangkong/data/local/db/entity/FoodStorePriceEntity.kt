package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents the relationship between a Food and a Store with an associated price.
 *
 * Key design decisions:
 * - Price belongs to the relationship (food + store), not FoodEntity.
 * - Surrogate PK used for Room simplicity and future extensibility.
 * - Unique constraint on (foodId, storeId) ensures only one active price per pair.
 *
 * First-pass scope:
 * - Single "estimatedPrice" only.
 * - No price history yet.
 * - DAO will aggregate (AVG) if needed later.
 *
 * Future-safe:
 * - Can later evolve into price history by adding timestamps or splitting table.
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
     * Estimated price for this food at this store.
     *
     * Rules:
     * - Non-null: presence of row = price exists.
     * - Use consistent currency at app level (not enforced here yet).
     */
    val estimatedPrice: Double
)

/**
 * =============================================================================
 * FUTURE-YOU / FUTURE AI NOTES — DO NOT DELETE
 * =============================================================================
 *
 * Why no price history (yet)
 * - This table represents the "current best known price".
 * - Simpler queries and avoids premature complexity.
 *
 * Future evolution paths
 * - Add effectiveDate / updatedAt column
 * - OR introduce FoodStorePriceHistoryEntity
 * - Keep this table as "latest snapshot" for fast reads
 *
 * Why unique (foodId, storeId)
 * - Prevents duplicate rows like:
 *     (chicken breast, Costco) x 3 entries
 * - Forces updates instead of inserts (upsert behavior)
 *
 * DAO implications
 * - Simple AVG queries can still work across rows (if later expanded)
 * - For now, each pair has only one row → AVG is trivial but future-proof
 *
 * Important guardrail
 * - Do NOT move estimatedPrice to FoodEntity.
 * - Do NOT duplicate store info here.
 * - This table must remain the single source of truth for pricing relationships.
 */