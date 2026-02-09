package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.domain.repository.PlannedMealRepository
import javax.inject.Inject

/**
 * Deletes a planned meal. Planned items are deleted via FK CASCADE.
 *
 * We will expose this ONLY for empty meals in UI (functional cleanup).
 */
class RemovePlannedMealUseCase @Inject constructor(
    private val meals: PlannedMealRepository
) {
    suspend operator fun invoke(mealId: Long) {
        require(mealId > 0L) { "mealId must be > 0" }
        meals.deleteById(mealId)
    }
}
