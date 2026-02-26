package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.domain.planner.model.PlannedItemSource
import com.example.adobongkangkong.domain.repository.FoodLookupRepository
import com.example.adobongkangkong.domain.repository.PlannedItemsRangeRepository
import com.example.adobongkangkong.domain.repository.RecipeHeaderLookup
import com.example.adobongkangkong.domain.repository.RecipeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import java.time.LocalDate
import javax.inject.Inject

/**
 * Observes “shopping needs” (food quantities) derived from planned meals/items over a forward-looking window.
 *
 * ## Purpose
 * Provide a single domain entry point for the Shopping List feature to answer:
 * “What foods do I need on each date in the next N days, and how much?”
 *
 * The output is designed for UI rendering and grouping, not for persistence.
 *
 * ## Rationale (why this use case exists)
 * Planned meals store *intent* (items scheduled on dates), but shopping needs require **expansion**
 * and **normalization**:
 *
 * - FOOD planned items can be read directly.
 * - RECIPE planned items must be expanded into ingredient foods, scaled to the planned servings.
 * - Names must be resolved for display.
 *
 * Keeping this in the domain layer:
 * - centralizes expansion rules (so planner/logging/shopping don’t diverge),
 * - prevents UI from duplicating recipe math logic,
 * - maintains a stable contract even if data layer joins or schema evolve.
 *
 * ## Behavior
 * For an inclusive ISO window `[startDate, startDate + days - 1]`:
 *
 * 1) Observe planned items joined to their planned dates via [PlannedItemsRangeRepository].
 * 2) For each planned row:
 *    - If `type == FOOD`:
 *        emit one “atom” as-is (foodId + grams/servings).
 *    - If `type == RECIPE`:
 *        expand to ingredient atoms using [RecipeRepository]:
 *        - Load recipe header by recipeFoodId (edit-mode key).
 *        - Compute `scale = plannedServings / servingsYield`.
 *        - For each ingredient line, scale ingredient servings/grams by `scale`.
 *    - If `type == RECIPE_BATCH`:
 *        intentionally ignored in this version (see “Limitations”).
 * 3) Aggregate atoms by `(date, foodId)`:
 *    - Sum grams independently (null treated as 0.0, output null if total == 0.0).
 *    - Sum servings independently (same rule).
 * 4) Resolve food names in bulk via [FoodLookupRepository].
 * 5) Emit a sorted list:
 *    - date ASC
 *    - foodName ASC (case-insensitive)
 *
 * ## Parameters
 * @param startDate First day (inclusive) of the window.
 * @param days Number of days to include (must be > 0). Window is inclusive.
 *
 * ## Return
 * @return A stream of [PlannedFoodNeed] rows suitable for UI grouping and display.
 * Emits whenever the underlying planned items/meals in the range change.
 *
 * ## Edge cases handled
 * - `days <= 0` → throws (caller bug).
 * - No planned items in range → emits empty list.
 * - RECIPE row missing planned servings / servingsYield / header / ingredients → contributes nothing (silent skip).
 * - Ingredient line with neither grams nor servings → skipped.
 * - Food name missing in lookup → uses `"Food #<id>"` fallback.
 *
 * ## Pitfalls / gotchas
 * - **Recipe expansion requires planned servings.** If a planned recipe item is stored without servings,
 *   it will not contribute to needs (by design: scaling would be undefined).
 * - **Ingredient quantities are dual-path (grams vs servings).** This use case sums grams and servings
 *   independently; it does not convert between them (no density guessing).
 * - **Sorting is UI-facing.** If the UI later needs “group by food then earliest date”, do that at the UI/VM
 *   layer or introduce a second domain projection—do not change ordering silently here.
 * - **Date membership is ISO-string based upstream.** This use case trusts `dateIso` from the join
 *   as the authoritative planned day.
 *
 * ## Architectural rules
 * - Do not join to Foods/Recipes tables directly here except via the injected lookup repositories/DAOs.
 * - Do not guess density or convert ml ↔ grams; keep grams and servings as separate quantities.
 * - Output is derived only; this use case must not write to the database.
 */
class ObservePlannedFoodNeedsUseCase @Inject constructor(
    private val plannedItemsRangeRepository: PlannedItemsRangeRepository,
    private val foodLookupRepository: FoodLookupRepository,
    private val recipeRepository: RecipeRepository,
    private val recipeHeaderLookup: RecipeHeaderLookup,
) {

    operator fun invoke(
        startDate: LocalDate,
        days: Int
    ): Flow<List<PlannedFoodNeed>> {
        require(days > 0) { "days must be > 0" }

        val startIso = startDate.toString()
        val endIso = startDate.plusDays(days.toLong() - 1).toString()

        return plannedItemsRangeRepository
            .observePlannedItemsInRange(startIso, endIso)
            .mapLatest { rows ->
                val atoms = buildList {
                    for (row in rows) {
                        val date = LocalDate.parse(row.dateIso)

                        when (row.type) {
                            PlannedItemSource.FOOD -> {
                                add(
                                    NeedAtom(
                                        date = date,
                                        foodId = row.refId,
                                        grams = row.grams,
                                        servings = row.servings
                                    )
                                )
                            }

                            PlannedItemSource.RECIPE -> {
                                addAll(
                                    expandRecipe(
                                        date = date,
                                        recipeFoodId = row.refId,
                                        plannedServings = row.servings
                                    )
                                )
                            }

                            PlannedItemSource.RECIPE_BATCH -> {
                                // Intentionally omitted for now (your direction: recipe is the source).
                            }
                        }
                    }
                }

                if (atoms.isEmpty()) return@mapLatest emptyList()

                // Aggregate per (date, foodId)
                val aggregated: List<NeedAtom> =
                    atoms.groupBy { it.date to it.foodId }
                        .map { (key, list) ->
                            val (date, foodId) = key
                            val gramsSum = list.sumOf { it.grams ?: 0.0 }.takeIf { it != 0.0 }
                            val servingsSum = list.sumOf { it.servings ?: 0.0 }.takeIf { it != 0.0 }
                            NeedAtom(date = date, foodId = foodId, grams = gramsSum, servings = servingsSum)
                        }

                // Resolve names in one call
                val ids = aggregated.map { it.foodId }.distinct()
                val namesById = foodLookupRepository.getFoodNamesByIds(ids)

                aggregated
                    .map { a ->
                        PlannedFoodNeed(
                            date = a.date,
                            foodId = a.foodId,
                            foodName = namesById[a.foodId] ?: "Food #${a.foodId}",
                            grams = a.grams,
                            servings = a.servings
                        )
                    }
                    .sortedWith(
                        compareBy<PlannedFoodNeed> { it.date }
                            .thenBy { it.foodName.lowercase() }
                    )
            }
    }

    private suspend fun expandRecipe(
        date: LocalDate,
        recipeFoodId: Long,
        plannedServings: Double?
    ): List<NeedAtom> {
        val pServ = plannedServings?.takeIf { it > 0.0 } ?: return emptyList()

        // Your RecipeRepository is keyed by foodId for edit mode.
        val header = recipeRepository.getRecipeByFoodId(recipeFoodId) ?: return emptyList()

        val yield = header.servingsYield.takeIf { it > 0.0 } ?: return emptyList()
        val scale = pServ / yield

        val ingredients = recipeRepository.getIngredients(header.recipeId)
        if (ingredients.isEmpty()) return emptyList()

        // Ingredient lines are servings-based in your domain contract.
        return ingredients.mapNotNull { ing ->
            val scaledServings =
                ing.ingredientServings?.takeIf { it > 0.0 }?.let { it * scale }

            val scaledGrams =
                ing.ingredientGrams?.takeIf { it > 0.0 }?.let { it * scale }

            if (scaledServings == null && scaledGrams == null) return@mapNotNull null

            NeedAtom(
                date = date,
                foodId = ing.ingredientFoodId,
                grams = scaledGrams,
                servings = scaledServings
            )
        }
    }

    private data class NeedAtom(
        val date: LocalDate,
        val foodId: Long,
        val grams: Double?,
        val servings: Double?
    )
}

/**
 * Output row: “food needed on date”.
 *
 * Semantics:
 * - [grams] and [servings] are independent quantities and may both be present.
 * - Recipe expansions typically produce servings-based ingredient needs (grams often null),
 *   because your recipe ingredient contract is primarily servings-based.
 */
data class PlannedFoodNeed(
    val date: LocalDate,
    val foodId: Long,
    val foodName: String,
    val grams: Double?,
    val servings: Double?
)

/**
 * Minimal projection for planned items joined with their planned date.
 * Backed by planned_items + planned_meals join in the data layer.
 */
data class PlannedItemWithDateRow(
    val dateIso: String,
    val type: PlannedItemSource,
    val refId: Long,
    val grams: Double?,
    val servings: Double?
)

/**
 * =============================================================================
 * FOR FUTURE AI ASSISTANT — invariants, boundaries, and “do not refactor” notes
 * =============================================================================
 *
 * Invariants (must not change)
 * - Window is inclusive: endIso = startDate + (days - 1).
 * - RECIPE expansion scale is: plannedServings / recipe.servingsYield.
 * - Aggregate key is exactly (date, foodId).
 * - Grams and servings are summed independently; never auto-convert between them.
 * - No density guessing, no ml↔g conversion, no “helpful” unit inference.
 * - Ordering is date ASC then foodName ASC (case-insensitive).
 *
 * Do not refactor notes
 * - Do not move recipe expansion into UI. Keep this logic centralized so shopping/planner stay consistent.
 * - Do not change skip-vs-error behavior casually:
 *   - Missing recipe/header/yield/ingredients currently results in “no contribution” (silent skip).
 *   - If you introduce diagnostics, do it as an *additional* debug surface, not by changing output semantics.
 *
 * Architectural boundaries
 * - This use case is read-only. It must not write planned meals/items or mutate recipes.
 * - It relies on repositories as boundaries. Avoid direct DB/DAO access here.
 *
 * Known limitations (intentional)
 * - PlannedItemSource.RECIPE_BATCH is ignored per original direction (“recipe is the source”).
 *   If adding:
 *   - Decide whether to treat batch as recipe reference or as cooked-grams-based needs.
 *   - Keep the “no density guessing” rule.
 *
 * Performance considerations
 * - Name resolution is intentionally batched via getFoodNamesByIds(ids).
 * - Recipe expansion does per-recipe reads (header + ingredients). If this becomes hot:
 *   - consider caching recipe header/ingredients within a single mapLatest emission,
 *   - but do not introduce global caches that can go stale across DB updates without invalidation.
 *
 * Migration notes
 * - Date strings are ISO yyyy-MM-dd. If migrating to KMP time, keep the ISO-string boundary
 *   between data storage and domain logic stable.
 */