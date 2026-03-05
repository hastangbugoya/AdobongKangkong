package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.domain.model.MacroTotals
import com.example.adobongkangkong.domain.nutrition.MacroKeys
import com.example.adobongkangkong.domain.planner.model.PlannedMeal
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import com.example.adobongkangkong.domain.repository.RecipeBatchLookupRepository
import com.example.adobongkangkong.domain.repository.RecipeRepository
import javax.inject.Inject

/**
 * Computes macro totals (kcal / protein / carbs / fat) for a set of planned meals.
 *
 * Used by PlannerDay to display:
 * - per-meal macro totals (mealId -> totals)
 * - daily macro totals (sum across all meals)
 *
 * Notes:
 * - Best-effort: missing snapshots or missing serving bridges contribute 0 for that item.
 * - Planned item source mapping:
 *   - FOOD        -> sourceId is foods.id
 *   - RECIPE      -> sourceId is recipes.id (resolved to recipe.foodId via [RecipeRepository])
 *   - RECIPE_BATCH-> sourceId is recipe_batches.id (resolved to batchFoodId via [RecipeBatchLookupRepository])
 *
 * Amount basis:
 * - if qtyGrams != null -> use nutrientsPerGram * grams
 * - else if qtyServings != null -> attempt conversion via:
 *     - gramsPerServingUnit + nutrientsPerGram, OR
 *     - mlPerServingUnit + nutrientsPerMilliliter
 */
class ComputePlannedDayMacroTotalsUseCase @Inject constructor(
    private val foodSnapshots: FoodNutritionSnapshotRepository,
    private val recipes: RecipeRepository,
    private val recipeBatches: RecipeBatchLookupRepository
) {

    data class Output(
        val mealTotals: Map<Long, MacroTotals>,
        val dayTotals: MacroTotals
    )

    suspend operator fun invoke(meals: List<PlannedMeal>): Output {
        if (meals.isEmpty()) return Output(mealTotals = emptyMap(), dayTotals = MacroTotals())

        val allItems = meals.flatMap { it.items }

        val foodIds = mutableSetOf<Long>()
        val recipeIds = mutableSetOf<Long>()
        val batchIds = mutableSetOf<Long>()

        for (it in allItems) {
            when (it.sourceType) {
                PlannedItemSource.FOOD -> foodIds.add(it.sourceId)
                PlannedItemSource.RECIPE -> recipeIds.add(it.sourceId)
                PlannedItemSource.RECIPE_BATCH -> batchIds.add(it.sourceId)
            }
        }

        val recipeFoodIdsByRecipeId = recipes.getFoodIdsByRecipeIds(recipeIds)
        foodIds.addAll(recipeFoodIdsByRecipeId.values)

        val batchFoodIdsByBatchId = recipeBatches.getBatchFoodIds(batchIds)
        foodIds.addAll(batchFoodIdsByBatchId.values)

        val snapshotsByFoodId = foodSnapshots.getSnapshots(foodIds)

        val K_CAL = MacroKeys.CALORIES
        val K_PRO = MacroKeys.PROTEIN
        val K_CAR = MacroKeys.CARBS
        val K_FAT = MacroKeys.FAT

        val mealTotals = LinkedHashMap<Long, MacroTotals>()
        var day = MacroTotals()

        fun addToMeal(mealId: Long, add: MacroTotals) {
            val cur = mealTotals[mealId] ?: MacroTotals()
            mealTotals[mealId] = MacroTotals(
                caloriesKcal = cur.caloriesKcal + add.caloriesKcal,
                proteinG = cur.proteinG + add.proteinG,
                carbsG = cur.carbsG + add.carbsG,
                fatG = cur.fatG + add.fatG
            )
            day = MacroTotals(
                caloriesKcal = day.caloriesKcal + add.caloriesKcal,
                proteinG = day.proteinG + add.proteinG,
                carbsG = day.carbsG + add.carbsG,
                fatG = day.fatG + add.fatG
            )
        }

        // Compute totals per item, then accumulate into its parent meal.
        for (meal in meals) {
            for (item in meal.items) {
                val snapshotFoodId = when (item.sourceType) {
                    PlannedItemSource.FOOD -> item.sourceId
                    PlannedItemSource.RECIPE -> recipeFoodIdsByRecipeId[item.sourceId]
                    PlannedItemSource.RECIPE_BATCH -> batchFoodIdsByBatchId[item.sourceId]
                } ?: continue

                val snap = snapshotsByFoodId[snapshotFoodId] ?: continue

                val grams = item.qtyGrams
                val servings = item.qtyServings

                val macros = when {
                    grams != null && grams > 0.0 -> {
                        val map = snap.nutrientsPerGram ?: continue
                        MacroTotals(
                            caloriesKcal = (map[K_CAL] ?: 0.0) * grams,
                            proteinG = (map[K_PRO] ?: 0.0) * grams,
                            carbsG = (map[K_CAR] ?: 0.0) * grams,
                            fatG = (map[K_FAT] ?: 0.0) * grams
                        )
                    }
                    servings != null && servings > 0.0 -> {
                        // Prefer mass bridge
                        val gps = snap.gramsPerServingUnit
                        val npm = snap.nutrientsPerGram
                        if (gps != null && npm != null) {
                            val g = servings * gps
                            MacroTotals(
                                caloriesKcal = (npm[K_CAL] ?: 0.0) * g,
                                proteinG = (npm[K_PRO] ?: 0.0) * g,
                                carbsG = (npm[K_CAR] ?: 0.0) * g,
                                fatG = (npm[K_FAT] ?: 0.0) * g
                            )
                        } else {
                            // Fallback: volume bridge if present
                            val mls = snap.mlPerServingUnit
                            val nml = snap.nutrientsPerMilliliter
                            if (mls != null && nml != null) {
                                val ml = servings * mls
                                MacroTotals(
                                    caloriesKcal = (nml[K_CAL] ?: 0.0) * ml,
                                    proteinG = (nml[K_PRO] ?: 0.0) * ml,
                                    carbsG = (nml[K_CAR] ?: 0.0) * ml,
                                    fatG = (nml[K_FAT] ?: 0.0) * ml
                                )
                            } else {
                                MacroTotals()
                            }
                        }
                    }
                    else -> MacroTotals()
                }

                addToMeal(meal.id, macros)
            }
        }

        // Ensure all meals have an entry (even if 0).
        for (m in meals) {
            mealTotals.putIfAbsent(m.id, MacroTotals())
        }

        return Output(mealTotals = mealTotals, dayTotals = day)
    }
}
