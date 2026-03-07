package com.example.adobongkangkong.domain.mealprep.usecase

import com.example.adobongkangkong.domain.model.MacroTotals
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import javax.inject.Inject

/**
 * Computes compact macro totals for the in-memory meal template editor draft.
 *
 * ## For developers
 * Purpose:
 * - provide Phase 3A live macro totals for the template editor before the template is saved
 * - reuse the same nutrition snapshot math conventions already used elsewhere in planner/template
 *   macro aggregation
 *
 * Behavior:
 * - best-effort only; missing snapshots or missing bridges count as zero for that draft line
 * - `grams` takes precedence over `servings`, matching existing template/planner macro behavior
 * - totals are advisory only and must never block template saving
 *
 * Scope:
 * - this use case intentionally works on editor draft rows, not persisted template ids
 * - it currently supports the item shape produced by the template editor: `foodId + servings + grams`
 */
class ComputeMealTemplateDraftMacroTotalsUseCase @Inject constructor(
    private val snapshots: FoodNutritionSnapshotRepository
) {

    data class DraftItem(
        val foodId: Long,
        val servings: Double?,
        val grams: Double?
    )

    suspend operator fun invoke(items: List<DraftItem>): MacroTotals {
        if (items.isEmpty()) return MacroTotals()

        val snapshotMap = snapshots.getSnapshots(items.map { it.foodId }.toSet())

        val kCal = NutrientKey("CALORIES_KCAL")
        val kProtein = NutrientKey("PROTEIN_G")
        val kCarbs = NutrientKey("CARBS_G")
        val kFat = NutrientKey("FAT_G")

        var totals = MacroTotals()

        for (item in items) {
            val snap = snapshotMap[item.foodId] ?: continue

            val add = when {
                item.grams != null && item.grams > 0.0 -> {
                    val perGram = snap.nutrientsPerGram
                    if (perGram == null) {
                        MacroTotals()
                    } else {
                        MacroTotals(
                            caloriesKcal = perGram[kCal] * item.grams,
                            proteinG = perGram[kProtein] * item.grams,
                            carbsG = perGram[kCarbs] * item.grams,
                            fatG = perGram[kFat] * item.grams
                        )
                    }
                }

                item.servings != null && item.servings > 0.0 -> {
                    val gramsPerServing = snap.gramsPerServingUnit
                    val perGram = snap.nutrientsPerGram
                    if (gramsPerServing != null && perGram != null) {
                        val grams = item.servings * gramsPerServing
                        MacroTotals(
                            caloriesKcal = perGram[kCal] * grams,
                            proteinG = perGram[kProtein] * grams,
                            carbsG = perGram[kCarbs] * grams,
                            fatG = perGram[kFat] * grams
                        )
                    } else {
                        val mlPerServing = snap.mlPerServingUnit
                        val perMl = snap.nutrientsPerMilliliter
                        if (mlPerServing != null && perMl != null) {
                            val ml = item.servings * mlPerServing
                            MacroTotals(
                                caloriesKcal = perMl[kCal] * ml,
                                proteinG = perMl[kProtein] * ml,
                                carbsG = perMl[kCarbs] * ml,
                                fatG = perMl[kFat] * ml
                            )
                        } else {
                            MacroTotals()
                        }
                    }
                }

                else -> MacroTotals()
            }

            totals = MacroTotals(
                caloriesKcal = totals.caloriesKcal + add.caloriesKcal,
                proteinG = totals.proteinG + add.proteinG,
                carbsG = totals.carbsG + add.carbsG,
                fatG = totals.fatG + add.fatG
            )
        }

        return totals
    }
}

/**
 * Bottom KDoc for future AI assistant.
 *
 * Phase 3A needed live template-editor macros without depending on persisted template ids, so this
 * use case accepts draft rows directly. Keep it isolated from UI concerns:
 * - it returns raw [MacroTotals]
 * - compact summary text is still produced by the shared template formatter
 * - goal comparisons, if added later, should layer on top of this instead of replacing it
 */
