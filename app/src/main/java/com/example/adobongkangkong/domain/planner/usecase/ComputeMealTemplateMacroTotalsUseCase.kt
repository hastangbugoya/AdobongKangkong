package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.domain.model.MacroTotals
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import com.example.adobongkangkong.domain.repository.MealTemplateItemRepository
import com.example.adobongkangkong.domain.repository.RecipeBatchLookupRepository
import com.example.adobongkangkong.domain.repository.RecipeRepository
import javax.inject.Inject

/**
 * Computes macro totals (kcal/protein/carbs/fat) for meal templates for display in the template picker.
 *
 * Notes:
 * - Best-effort: missing snapshots or missing bridges count as 0 for that item.
 * - Items may be FOOD, RECIPE, or RECIPE_BATCH.
 * - Amount basis:
 *   - if grams != null -> use nutrientsPerGram * grams
 *   - else if servings != null -> prefer gramsPerServingUnit bridge, else mlPerServingUnit bridge
 */
class ComputeMealTemplateMacroTotalsUseCase @Inject constructor(
    private val templateItems: MealTemplateItemRepository,
    private val foodSnapshots: FoodNutritionSnapshotRepository,
    private val recipeBatches: RecipeBatchLookupRepository,
    private val recipes: RecipeRepository
) {
    suspend operator fun invoke(templateIds: List<Long>): Map<Long, MacroTotals> {
        if (templateIds.isEmpty()) return emptyMap()

        val items = templateItems.getItemsForTemplates(templateIds)

        // Collect batch ids and recipe ids for id→foodId mapping.
        val batchIds = linkedSetOf<Long>()
        val recipeIds = linkedSetOf<Long>()
        val directFoodIds = linkedSetOf<Long>()

        for (item in items) {
            when (item.type) {
                PlannedItemSource.FOOD -> directFoodIds.add(item.refId)
                PlannedItemSource.RECIPE -> recipeIds.add(item.refId)
                PlannedItemSource.RECIPE_BATCH -> batchIds.add(item.refId)
            }
        }

        val batchFoodIdsByBatchId = recipeBatches.getBatchFoodIds(batchIds)

        val recipeFoodIdsByRecipeId: Map<Long, Long> = buildMap {
            for (recipeId in recipeIds) {
                val header = recipes.getHeaderByRecipeId(recipeId) ?: continue
                put(recipeId, header.foodId)
            }
        }

        val allFoodIds = linkedSetOf<Long>()
        allFoodIds.addAll(directFoodIds)
        allFoodIds.addAll(batchFoodIdsByBatchId.values)
        allFoodIds.addAll(recipeFoodIdsByRecipeId.values)

        val snapshotsByFoodId = foodSnapshots.getSnapshots(allFoodIds)

        // Keys: align with existing macro mapping.
        val K_CAL = NutrientKey("CALORIES_KCAL")
        val K_PRO = NutrientKey("PROTEIN_G")
        val K_CAR = NutrientKey("CARBS_G")
        val K_FAT = NutrientKey("FAT_G")

        val totalsByTemplateId = LinkedHashMap<Long, MacroTotals>()

        fun add(templateId: Long, add: MacroTotals) {
            val cur = totalsByTemplateId[templateId] ?: MacroTotals()
            totalsByTemplateId[templateId] = MacroTotals(
                caloriesKcal = cur.caloriesKcal + add.caloriesKcal,
                proteinG = cur.proteinG + add.proteinG,
                carbsG = cur.carbsG + add.carbsG,
                fatG = cur.fatG + add.fatG
            )
        }

        for (item in items) {
            val snapshotFoodId: Long? = when (item.type) {
                PlannedItemSource.FOOD -> item.refId
                PlannedItemSource.RECIPE -> recipeFoodIdsByRecipeId[item.refId]
                PlannedItemSource.RECIPE_BATCH -> batchFoodIdsByBatchId[item.refId]
            }
            if (snapshotFoodId == null) continue

            val snap = snapshotsByFoodId[snapshotFoodId] ?: continue

            val grams = item.grams
            val servings = item.servings

            val macros = when {
                grams != null && grams > 0.0 -> {
                    val map = snap.nutrientsPerGram
                    if (map == null) MacroTotals() else MacroTotals(
                        caloriesKcal = map[K_CAL] * grams,
                        proteinG = map[K_PRO] * grams,
                        carbsG = map[K_CAR] * grams,
                        fatG = map[K_FAT] * grams
                    )
                }

                servings != null && servings > 0.0 -> {
                    val gps = snap.gramsPerServingUnit
                    val npm = snap.nutrientsPerGram
                    if (gps != null && npm != null) {
                        val g = servings * gps
                        MacroTotals(
                            caloriesKcal = npm[K_CAL] * g,
                            proteinG = npm[K_PRO] * g,
                            carbsG = npm[K_CAR] * g,
                            fatG = npm[K_FAT] * g
                        )
                    } else {
                        val mls = snap.mlPerServingUnit
                        val nml = snap.nutrientsPerMilliliter
                        if (mls != null && nml != null) {
                            val ml = servings * mls
                            MacroTotals(
                                caloriesKcal = nml[K_CAL] * ml,
                                proteinG = nml[K_PRO] * ml,
                                carbsG = nml[K_CAR] * ml,
                                fatG = nml[K_FAT] * ml
                            )
                        } else {
                            MacroTotals()
                        }
                    }
                }

                else -> MacroTotals()
            }

            add(item.templateId, macros)
        }

        // Ensure all requested templateIds exist in output.
        for (id in templateIds) {
            totalsByTemplateId.putIfAbsent(id, MacroTotals())
        }

        return totalsByTemplateId
    }
}
