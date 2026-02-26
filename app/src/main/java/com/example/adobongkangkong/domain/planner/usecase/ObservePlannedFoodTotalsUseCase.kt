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
 * Builds **total** needed amounts per food across the next N planned days.
 *
 * ## What this use case does
 * This consumes the *per-date* planned needs stream from [ObservePlannedFoodNeedsUseCase] and produces
 * one row per `foodId` that represents:
 *
 * - earliest planned date that needs this food (for sorting / shopping priority)
 * - total grams (if any were deterministically computable)
 * - total milliliters (if any were deterministically computable)
 * - total "unconverted servings" (when we cannot safely canonicalize servings into grams/mL)
 *
 * This is designed for a “shopping list / totals” UI where users want a concise list of foods
 * and total quantities across a time horizon.
 *
 * ## Why this exists (rationale)
 * The upstream needs list can include mixed quantity representations:
 * - direct grams (always safe)
 * - servings (sometimes convertible to grams or mL, sometimes not)
 *
 * This use case centralizes the conversion and “don’t silently drop data” behavior so:
 * - the UI doesn’t implement nutrition math rules
 * - conversion rules are consistent across screens
 * - you preserve correctness (no density guessing, no implicit g<->mL bridging)
 *
 * ## Canonicalization rules (locked in)
 * - **grams**:
 *   - Always include `row.grams` directly.
 *   - Convert servings → grams if deterministic (mass units) OR explicit grams-per-serving bridge exists.
 * - **milliliters**:
 *   - Convert servings → mL if deterministic (volume units) OR explicit ml-per-serving bridge exists.
 * - **NO density guessing**:
 *   - Never convert grams ↔ mL. If you want that later, add an explicit density field and a separate rule.
 * - **Never silently drop servings**:
 *   - If servings cannot be converted to grams/mL, accumulate them into [PlannedFoodTotalNeed.unconvertedServingsTotal].
 *
 * ## Sorting (output)
 * Default sort:
 * 1) earliestNextPlannedDate ASC (nulls last)
 * 2) foodName ASC (case-insensitive)
 * 3) foodId ASC (stable tiebreaker)
 *
 * ## Inputs / outputs
 * Input:
 * - [startDate] + [days] define the horizon passed to [ObservePlannedFoodNeedsUseCase].
 *
 * Output:
 * - A stream of [PlannedFoodTotalNeed] rows (one per foodId).
 *
 * ## Gotchas / edge cases handled
 * - Missing Food rows in DB: uses name from upstream rows, otherwise "Food #id".
 * - Zero values:
 *   - grams/ml/servings == 0.0 are ignored so totals don't show meaningless 0.
 * - Mixed units for the same food across days:
 *   - grams can accumulate alongside unconverted servings (and/or mL) in the same output row.
 * - Count-ish/container-ish serving units:
 *   - Attempts explicit bridges first (gramsPerServingUnit or mlPerServingUnit), else remains unconverted.
 *
 * ## Limitations (intentional)
 * - This does not expand recipes; upstream needs use case is responsible for expansion.
 * - This does not interpret RECIPE_BATCH here; upstream currently omits it by design.
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
     * Conversion is based on the food's serving definition:
     * - If servingUnit is a mass unit, convert amountInUnit -> grams.
     * - If servingUnit is a volume unit, convert amountInUnit -> mL.
     * - Otherwise, attempt explicit bridges:
     *   - gramsPerServingUnit (grams per 1 serving)
     *   - mlPerServingUnit (mL per 1 serving)
     *
     * IMPORTANT:
     * - This intentionally does NOT do any g<->mL conversion (no density guessing).
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
 * Interpretation:
 * - [gramsTotal] is present if we accumulated any grams (direct grams or converted servings->grams).
 * - [mlTotal] is present if we accumulated any mL (converted servings->mL).
 * - [unconvertedServingsTotal] is present if any servings could not be converted.
 *
 * The purpose of [unconvertedServingsTotal] is to preserve visibility:
 * we never silently drop planned quantities just because they’re not canonicalizable yet.
 */
data class PlannedFoodTotalNeed(
    val foodId: Long,
    val foodName: String,
    val earliestNextPlannedDate: LocalDate?,
    val gramsTotal: Double?,
    val mlTotal: Double?,
    val unconvertedServingsTotal: Double?
)

/**
 * FOR FUTURE AI ASSISTANT — READ BEFORE “IMPROVING” THIS FILE
 *
 * Why this exists:
 * - Upstream produces mixed units (grams and servings) and we must produce a single “totals” list for shopping.
 *
 * Non-negotiable rules:
 * - Never guess density. Never convert grams <-> mL.
 * - Never drop data: if servings cannot be converted, keep them in unconvertedServingsTotal.
 *
 * Common future requests:
 * - “Support density”: add an explicit density field (or explicit gramsPerMl) and create a separate conversion path.
 * - “Support recipe batches”: add expansion upstream (ObservePlannedFoodNeedsUseCase), not here.
 *
 * If you change conversion behavior:
 * - Add regression tests for the exact scenario that motivated the change.
 * - Prefer widening output visibility (more explicit fields) over silent conversion assumptions.
 */