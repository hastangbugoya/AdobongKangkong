package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent manual grocery item for AK's small "Always on list" add-on.
 *
 * This table is intentionally simple:
 * - Rows are always-on by definition.
 * - Generated grocery items still come from planner/recipe data.
 * - Checked state only means "handled for now"; it does not remove the item.
 * - Deleting the row is the only way to remove it from the always-on list.
 *
 * Do not grow this into a full grocery app model with stores, prices, aisles,
 * coupons, inventory, or route planning. Those belong outside this tiny AK add-on.
 */
@Entity(
    tableName = "pinned_grocery_items",
    indices = [
        Index(value = ["sortOrder"]),
        Index(value = ["name"])
    ]
)
data class PinnedGroceryItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val quantityText: String? = null,
    val isChecked: Boolean = false,
    val sortOrder: Int = 0,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
