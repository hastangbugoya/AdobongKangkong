package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import javax.inject.Inject

/**
 * Updates quantity on an existing planned item.
 *
 * Does not change ordering or move items; purely quantity.
 */
class UpdatePlannedItemQuantityUseCase @Inject constructor(
    private val items: PlannedItemRepository
) {
    suspend operator fun invoke(
        itemId: Long,
        grams: Double? = null,
        servings: Double? = null
    ) {
        require(itemId > 0) { "itemId must be > 0" }
        requireValidQuantity(grams, servings)

        val existing = items.getById(itemId)
            ?: throw IllegalArgumentException("Planned item not found: id=$itemId")

        items.update(
            existing.copy(
                grams = grams,
                servings = servings
            )
        )
    }

    private fun requireValidQuantity(grams: Double?, servings: Double?) {
        if (grams != null) require(grams > 0.0) { "grams must be > 0 when provided" }
        if (servings != null) require(servings > 0.0) { "servings must be > 0 when provided" }
        require(!(grams == null && servings == null)) { "Either grams or servings must be provided" }
    }
}
