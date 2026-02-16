package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.isMassUnit
import com.example.adobongkangkong.domain.model.isVolumeUnit
import com.example.adobongkangkong.domain.model.toGrams
import com.example.adobongkangkong.domain.model.toMilliliters
import com.example.adobongkangkong.domain.repository.FoodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import java.time.LocalDate
import javax.inject.Inject

/**
 * Builds TOTAL needed amounts per food across the next N days.
 *
 * Input is the per-date expanded list produced by [ObservePlannedFoodNeedsUseCase]
 * (so RECIPE items are already expanded into ingredient foods).
 *
 * Canonicalization goal:
 * - Try to convert everything into grams and/or mL (mass/volume).
 * - If we cannot convert a servings-only quantity for a food, we keep it as
 *   [unconvertedServingsTotal] so it doesn't silently disappear.
 *
 * Sorting:
 * - Default: earliestNextPlannedDate ASC, then foodName ASC (case-insensitive), then foodId ASC.
 *
 * NOTE:
 * - RECIPE_BATCH is ignored upstream by ObservePlannedFoodNeedsUseCase (per your rule).
 */
class ObservePlannedFoodTotalsUseCase @Inject constructor(
    private val observeNeeds: ObservePlannedFoodNeedsUseCase,
    private val foodRepository: FoodRepository
) {

    operator fun invoke(
        startDate: LocalDate,
        days: Int
    ): Flow<List<PlannedFoodTotalNeed>> {
        return observeNeeds(startDate = startDate, days = days)
            .mapLatest { perDateNeeds ->
                if (perDateNeeds.isEmpty()) return@mapLatest emptyList()

                val byFoodId = perDateNeeds.groupBy { it.foodId }

                val out = ArrayList<PlannedFoodTotalNeed>(byFoodId.size)

                for ((foodId, rows) in byFoodId) {
                    val food = foodRepository.getById(foodId)
                    val foodName = rows.firstOrNull()?.foodName
                        ?: food?.name
                        ?: "Food #$foodId"

                    val earliest = rows.minOfOrNull { it.date }

                    var gramsTotal = 0.0
                    var mlTotal = 0.0
                    var anyGrams = false
                    var anyMl = false
                    var unconvertedServingsTotal = 0.0
                    var anyUnconvertedServings = false

                    for (r in rows) {
                        // Direct grams always count.
                        r.grams?.let {
                            if (it != 0.0) {
                                gramsTotal += it
                                anyGrams = true
                            }
                        }

                        val s = r.servings
                        if (s == null || s == 0.0) continue

                        // Try to convert servings -> grams/mL based on the Food's serving definition.
                        val converted = convertServingsToCanonical(food, servings = s)
                        when {
                            converted?.grams != null -> {
                                gramsTotal += converted.grams
                                anyGrams = true
                            }
                            converted?.ml != null -> {
                                mlTotal += converted.ml
                                anyMl = true
                            }
                            else -> {
                                // Keep as “servings” if we cannot canonicalize.
                                unconvertedServingsTotal += s
                                anyUnconvertedServings = true
                            }
                        }
                    }

                    out += PlannedFoodTotalNeed(
                        foodId = foodId,
                        foodName = foodName,
                        earliestNextPlannedDate = earliest,
                        gramsTotal = gramsTotal.takeIf { anyGrams },
                        mlTotal = mlTotal.takeIf { anyMl },
                        unconvertedServingsTotal = unconvertedServingsTotal.takeIf { anyUnconvertedServings }
                    )
                }

                out.sortedWith(
                    compareBy<PlannedFoodTotalNeed> { it.earliestNextPlannedDate ?: LocalDate.MAX }
                        .thenBy { it.foodName.lowercase() }
                        .thenBy { it.foodId }
                )
            }
    }

    private data class CanonicalAmount(
        val grams: Double? = null,
        val ml: Double? = null
    )

    /**
     * Attempts to convert "servings" into canonical grams or mL.
     *
     * This does NOT do density-based g<->mL bridging (that can be added later when you decide
     * to treat foods with both gramsPerServingUnit and mlPerServingUnit as having a density).
     */
    private fun convertServingsToCanonical(
        food: com.example.adobongkangkong.domain.model.Food?,
        servings: Double
    ): CanonicalAmount? {
        if (food == null) return null

        val unit: ServingUnit = food.servingUnit
        val servingSize: Double = food.servingSize // “how many units per 1 serving”
        if (servings <= 0.0 || servingSize <= 0.0) return null

        // “amount in servingUnit”
        val amountInUnit = servings * servingSize

        return when {
            unit.isMassUnit() -> {
                val g = unit.toGrams(amountInUnit) ?: return null
                CanonicalAmount(grams = g)
            }

            unit.isVolumeUnit() -> {
                val ml = unit.toMilliliters(amountInUnit) ?: return null
                CanonicalAmount(ml = ml)
            }

            else -> {
                // Container-ish / count-ish units (CAN, BOTTLE, SERVING, SCOOP, etc.)
                // Prefer explicit bridges if present.
                val gPerServing = food.gramsPerServingUnit
                if (gPerServing != null && gPerServing > 0.0) {
                    return CanonicalAmount(grams = servings * gPerServing)
                }

                val mlPerServing = food.mlPerServingUnit
                if (mlPerServing != null && mlPerServing > 0.0) {
                    return CanonicalAmount(ml = servings * mlPerServing)
                }

                null
            }
        }
    }
}

/**
 * Output row: “total food needed across a range”.
 *
 * If we could canonicalize:
 * - gramsTotal != null and/or mlTotal != null
 *
 * If we could NOT canonicalize some servings:
 * - unconvertedServingsTotal != null (so nothing is silently dropped)
 */
data class PlannedFoodTotalNeed(
    val foodId: Long,
    val foodName: String,
    val earliestNextPlannedDate: LocalDate?,
    val gramsTotal: Double?,
    val mlTotal: Double?,
    val unconvertedServingsTotal: Double?
)