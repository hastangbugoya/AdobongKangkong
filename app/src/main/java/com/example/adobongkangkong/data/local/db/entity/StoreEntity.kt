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
 * First-pass scope:
 * - Keep this intentionally minimal.
 * - Store identity is just a user-facing name for now.
 * - Avoid adding address, chain metadata, or default-store logic until needed.
 *
 * Notes:
 * - Name is unique to prevent duplicate stores like "Costco" appearing multiple times.
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
    val name: String
)

/**
 * =============================================================================
 * FUTURE-YOU / FUTURE AI NOTES — DO NOT DELETE
 * =============================================================================
 *
 * Why name is unique
 * - This first pass assumes one logical store row per distinct store name.
 * - That keeps inserts/upserts simple while the feature is still small.
 *
 * Future-safe expansion paths
 * - Add store brand/chain fields later if needed.
 * - Add address/location fields later if the app starts caring about specific branches.
 * - Add sortOrder / isFavorite / notes later without changing the core relationship model.
 *
 * Important guardrail
 * - Do NOT move price fields onto StoreEntity or FoodEntity.
 * - Price belongs on the relationship row between a specific food and a specific store.
 *
 * Shopping/list direction
 * - This table is intentionally small so future shopping workflows can reference stores
 *   without needing to redesign the pricing model.
 */