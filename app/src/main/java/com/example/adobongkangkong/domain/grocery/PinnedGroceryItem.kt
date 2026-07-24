package com.example.adobongkangkong.domain.grocery

/**
 * Small always-on grocery item for AK's shopping-list add-on.
 *
 * These are manually managed staples. They are intentionally not generated
 * from the meal planner, not tied to store prices, and not meant to become a
 * full grocery-app inventory model.
 */
data class PinnedGroceryItem(
    val id: Long = 0,
    val name: String,
    val quantityText: String? = null,
    val isChecked: Boolean = false,
    val sortOrder: Int = 0,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
