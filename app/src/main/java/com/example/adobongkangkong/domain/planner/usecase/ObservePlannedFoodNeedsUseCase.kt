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
 * Observe all FOOD needs for planned items in the next N days.
 *
 * Rules (locked in):
 * - FOOD planned items: include as-is (grams/servings).
 * - RECIPE planned items: expand to ingredient foods, scaled by plannedServings / recipe.servingsYield.
 *   - Uses RecipeRepository.getRecipeByFoodId(...) or header lookup (see [RecipeHeaderLookup]).
 *   - Uses RecipeRepository.getIngredients(recipeId) which provides ingredient servings.
 *   - Output for expanded recipe ingredients is in "servings" (grams = null) because your
 *     RecipeIngredientLine is servings-based in the domain.
 * - Ordering: planned date ASC, then food name ASC (case-insensitive).
 * - Aggregation: per (date, foodId), sum grams and sum servings independently.
 *
 * NOTE:
 * - RECIPE_BATCH is intentionally ignored in this version per your request (“recipe is the source”).
 *   You can add it later using the same expansion strategy as recipe batches already do.
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
 * If the need comes from a recipe expansion, grams will typically be null and servings non-null
 * (because your RecipeIngredientLine is servings-based).
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