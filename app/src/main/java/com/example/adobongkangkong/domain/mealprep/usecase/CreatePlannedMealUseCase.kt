package com.example.adobongkangkong.domain.mealprep.usecase

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.data.local.db.entity.PlannedMealEntity
import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import javax.inject.Inject

/**
 * Creates a planned meal for a specific ISO date (yyyy-MM-dd).
 *
 * - PlannedDay is derived; we persist meals and items only.
 * - sortOrder is assigned automatically unless explicitly provided.
 *
 * Note: Slot is stored as a string enum token:
 * BREAKFAST | LUNCH | DINNER | SNACK | CUSTOM
 */
class CreatePlannedMealUseCase @Inject constructor(
    private val meals: PlannedMealRepository
) {
    suspend operator fun invoke(
        dateIso: String,
        slot: MealSlot,
        customLabel: String? = null,
        nameOverride: String? = null,
        sortOrder: Int? = null
    ): Long {
        require(dateIso.isNotBlank()) { "dateIso must not be blank" }
//        require(slot.isNotBlank()) { "slot must not be blank" }

        val normalizedCustomLabel = when (slot) {
            MealSlot.CUSTOM -> {
                val label = customLabel?.trim().orEmpty()
                require(label.isNotBlank()) { "customLabel is required when slot == CUSTOM" }
                label
            }
            else -> slot.display
        }

        val finalSortOrder = sortOrder ?: run {
            val max = meals.getMaxSortOrderForDate(dateIso)
            max + 1
        }

        val entity = PlannedMealEntity(
            date = dateIso,
            slot = slot,
            customLabel = normalizedCustomLabel,
            nameOverride = nameOverride?.trim()?.takeIf { it.isNotBlank() },
            sortOrder = finalSortOrder
        )

        return meals.insert(entity)
    }
}
