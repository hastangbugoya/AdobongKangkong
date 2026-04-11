package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Simple store master table for food pricing relationships.
 *
 * Why this exists:
 * - Price belongs to the food-store relationship, not the food itself.
 * - A dedicated store table keeps the schema normalized and gives us a clean path
 *   for future shopping/list features without polluting FoodEntity.
 *
 * Current scope:
 * - Keep this intentionally minimal.
 * - Store identity is a user-facing name plus optional address.
 * - Avoid adding contact, chain metadata, or default-store logic until needed.
 *
 * Notes:
 * - Name is unique to prevent duplicate stores like "Costco" appearing multiple times.
 * - Address is optional because many users may only care about the store name.
 * - Case/format normalization is still an app-layer concern for now.
 */
@Entity(
    tableName = "stores",
    indices = [
        Index(value = ["name"], unique = true)
    ]
)
data class StoreEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * User-facing store name.
     *
     * Examples:
     * - Costco
     * - Walmart
     * - Trader Joe's
     */
    val name: String,

    /**
     * Optional user-facing address or branch note.
     *
     * Examples:
     * - 123 Main St
     * - Downtown branch
     * - Vernon location
     *
     * Kept nullable because precise store location is helpful sometimes,
     * but not required for the pricing feature to work.
     */
    val address: String? = null
)

/**
 * =============================================================================
 * FUTURE-YOU / FUTURE AI NOTES — DO NOT DELETE
 * =============================================================================
 *
 * Why name is unique
 * - This pass still assumes one logical store row per distinct store name.
 * - That keeps inserts/upserts simple while the feature remains small.
 *
 * Why address is nullable
 * - Address helps distinguish branches when useful, but it is not required for
 *   normalized food pricing to function.
 * - The app should remain practical and low-friction.
 *
 * Future-safe expansion paths
 * - Add store brand/chain fields later if needed.
 * - Add sortOrder / isFavorite / notes later without changing the core relationship model.
 * - Add branch-specific identity rules later only if the product truly needs them.
 *
 * Important guardrail
 * - Do NOT move price fields onto StoreEntity or FoodEntity.
 * - Price belongs on the relationship row between a specific food and a specific store.
 *
 * Shopping/list direction
 * - This table is intentionally small so future shopping workflows can reference stores
 *   without needing to redesign the pricing model.
 */