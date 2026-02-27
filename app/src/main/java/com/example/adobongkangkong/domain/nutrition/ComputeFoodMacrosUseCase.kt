package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import javax.inject.Inject

data class FoodMacros(
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double
)

/**
 * Computes food macros using normalized per-gram nutrition.
 *
 * Domain rules:
 * - Always operate in grams.
 * - Never trust DB basis.
 * - Missing nutrients default to 0.
 */
class ComputeFoodMacrosUseCase @Inject constructor(
    private val snapshotRepo: FoodNutritionSnapshotRepository
) {
    suspend operator fun invoke(
        foodId: Long,
        grams: Double
    ): FoodMacros? {
        val snapshot = snapshotRepo.getSnapshot(foodId) ?: return null
        val nutrients = snapshot.nutrientsPerGram ?: return null

        return FoodMacros(
            calories = nutrients[NutrientKey("CALORIES")] * grams,
            protein = nutrients[NutrientKey("PROTEIN_G")] * grams,
            carbs    = nutrients[NutrientKey("CARBS_G")] * grams,
            fat      = nutrients[NutrientKey("FAT_G")] * grams
        )
    }
}
