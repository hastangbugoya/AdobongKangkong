package com.example.adobongkangkong.domain.mealprep.usecase

import com.example.adobongkangkong.data.local.db.entity.PlannedItemEntity
import com.example.adobongkangkong.domain.repository.PlannedItemRepository
import javax.inject.Inject

/**
 * Adds a FOOD planned item under an existing planned meal.
 *
 * Quantity semantics mirror existing logging:
 * - grams and servings are nullable
 * - higher layers can enforce rules; this layer stays minimal
 *
 * sortOrder is assigned automatically unless explicitly provided.
 */
class AddPlannedFoodItemUseCase @Inject constructor(
    private val items: PlannedItemRepository
) {
    suspend operator fun invoke(
        mealId: Long,
        foodId: Long,
        grams: Double? = null,
        servings: Double? = null,
        sortOrder: Int? = null
    ): Long {
        require(mealId > 0) { "mealId must be > 0" }
        require(foodId > 0) { "foodId must be > 0" }
        requireValidQuantity(grams, servings)

        // Append by default without needing a DB read for "max sort order".
        // Reads should order by (sortOrder ASC, id ASC).
        val finalSortOrder = sortOrder ?: Int.MAX_VALUE

        val entity = PlannedItemEntity(
            mealId = mealId,
            type = "FOOD",
            refId = foodId,
            grams = grams,
            servings = servings,
            sortOrder = finalSortOrder
        )

        return items.insert(entity)
    }

    private fun requireValidQuantity(grams: Double?, servings: Double?) {
        if (grams != null) require(grams > 0.0) { "grams must be > 0 when provided" }
        if (servings != null) require(servings > 0.0) { "servings must be > 0 when provided" }
        require(!(grams == null && servings == null)) { "Either grams or servings must be provided" }
    }
}
