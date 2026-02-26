package com.example.adobongkangkong.domain.recipes

import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.repository.RecipeRepository
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Computes total calories (kcal) and calories-per-serving for a recipe identified by its recipe foodId.
 *
 * Purpose
 * - Provide a lightweight, calories-only computation for a recipe so UI/callers can display kcal totals quickly
 *   without requiring batch context or cooked-yield math.
 *
 * Rationale (why this use case exists)
 * - Many surfaces only need calories (kcal), not a full nutrient breakdown.
 * - The canonical recipe nutrition computation operates over a [Recipe] snapshot; this use case adapts repository
 *   storage (header + ingredient lines) into that snapshot and extracts kcal.
 * - Centralizes “is this foodId a recipe?” gating and “servings must be valid” enforcement so callers don’t duplicate it.
 *
 * Behavior
 * - Loads recipe header by foodId via [RecipeRepository.getRecipeByFoodId].
 *   - If not found, returns [Result.NotRecipeFood].
 * - Enforces `servingsYield > 0` for per-serving computation.
 *   - If invalid, returns [Result.InvalidServings].
 * - Loads ingredient lines via [RecipeRepository.getIngredients] for the recipeId.
 * - Builds an in-memory [Recipe] snapshot:
 *   - Name is not required for math and is left blank.
 *   - Each [RecipeIngredient] is populated with `foodId` + `servings` from the ingredient line.
 * - Computes recipe nutrition totals via [ComputeRecipeNutritionForSnapshotUseCase].
 * - Extracts total kcal from `nutrition.totals[NutrientKey.CALORIES_KCAL]`.
 *   - If missing, returns [Result.MissingCalories].
 * - Computes per-serving kcal as `totalKcal / servingsYield`, rounding to int.
 * - Returns [Result.Success] with integer-rounded totals.
 *
 * Parameters
 * - foodId: The food id that must correspond to a recipe-backed food (i.e., have a recipe header in the repository).
 *
 * Return
 * - [Result.Success] with:
 *   - totalKcal: Total recipe kcal for the entire recipe (batch total), rounded to Int.
 *   - perServingKcal: Kcal per serving based on servingsYield, rounded to Int.
 * - [Result.NotRecipeFood] if the foodId is not associated with a recipe header.
 * - [Result.InvalidServings] if servingsYield is <= 0, preventing per-serving math.
 * - [Result.MissingCalories] if the upstream nutrition totals do not contain [NutrientKey.CALORIES_KCAL].
 *
 * Edge cases
 * - Ingredient lines may contain null servings (per repository model); this use case passes them through into the
 *   snapshot. Any validation/interpretation happens inside [ComputeRecipeNutritionForSnapshotUseCase].
 * - `totalYieldGrams` is carried through but not required for this kcal computation path (no cooked-grams logging here).
 * - Rounding uses [roundToInt]; small floating errors can shift values near the .5 boundary.
 *
 * Pitfalls / gotchas
 * - This is not a “logged recipe” calculator: it computes recipe totals from stored recipe ingredients, not a log entry.
 * - This use case does not enforce that the recipe has at least one ingredient or non-null servings per ingredient;
 *   missing/invalid ingredient amounts are expected to surface upstream in nutrition computation logic.
 * - Do not replace rounding behavior without auditing UI expectations (users often notice kcal off-by-1 changes).
 *
 * Architectural rules (if applicable)
 * - Domain-only orchestration: no UI, no navigation, no DB/Room APIs directly.
 * - Uses repository abstractions and a snapshot nutrition use case; must not rejoin foods or mutate snapshot logs.
 * - Logging model note: ISO-date-based logging uses `logDateIso` as authoritative; this use case does not write logs.
 * - Snapshot logs are immutable and must not rejoin foods; keep this logic pure and deterministic.
 */
class ComputeRecipeKcalForFoodIdUseCase @Inject constructor(
    private val recipes: RecipeRepository,
    private val computeNutrition: ComputeRecipeNutritionForSnapshotUseCase
) {

    suspend operator fun invoke(foodId: Long): Result {
        val header = recipes.getRecipeByFoodId(foodId)
            ?: return Result.NotRecipeFood(foodId)

        if (header.servingsYield <= 0.0) {
            return Result.InvalidServings(foodId)
        }

        val ingredientLines = recipes.getIngredients(header.recipeId)

        val recipe = Recipe(
            id = header.recipeId,
            name = "", // not required for nutrition math
            ingredients = ingredientLines.map { line ->
                RecipeIngredient(
                    foodId = line.ingredientFoodId,
                    servings = line.ingredientServings
                )
            },
            servingsYield = header.servingsYield,
            totalYieldGrams = header.totalYieldGrams
        )

        val nutrition = computeNutrition(recipe)

        val totalKcal = nutrition.totals[NutrientKey.CALORIES_KCAL]
            ?: return Result.MissingCalories(foodId)

        val perServingKcal =
            (totalKcal / header.servingsYield).roundToInt()

        return Result.Success(
            totalKcal = totalKcal.roundToInt(),
            perServingKcal = perServingKcal
        )
    }

    sealed class Result {
        data class Success(
            val totalKcal: Int,
            val perServingKcal: Int
        ) : Result()

        data class NotRecipeFood(val foodId: Long) : Result()
        data class InvalidServings(val foodId: Long) : Result()
        data class MissingCalories(val foodId: Long) : Result()
    }
}

/**
 * ===== Bottom KDoc (for future AI assistant) =====
 *
 * Invariants (what must not change)
 * - Must gate on recipe existence:
 *   - If getRecipeByFoodId(foodId) returns null, return NotRecipeFood(foodId).
 * - Must require servingsYield > 0 for per-serving computation:
 *   - If <= 0, return InvalidServings(foodId) before any division.
 * - Must compute nutrition via ComputeRecipeNutritionForSnapshotUseCase using a Recipe snapshot constructed from:
 *   - recipeId from header
 *   - ingredients from repository ingredient lines (ingredientFoodId + ingredientServings)
 * - Must extract kcal using NutrientKey.CALORIES_KCAL and return MissingCalories(foodId) if absent.
 * - Must remain pure and deterministic: no DB types, no UI, no date/time usage, no food re-joins.
 * - Snapshot logs are immutable and must not rejoin foods; do not add log hydration here.
 *
 * Do not refactor notes
 * - Do not change rounding semantics (roundToInt) without auditing UI baselines and tests.
 * - Do not “optimize away” the Recipe construction by passing repository lines directly unless
 *   ComputeRecipeNutritionForSnapshotUseCase’s contract is updated intentionally.
 *
 * Architectural boundaries
 * - This use case depends only on:
 *   - RecipeRepository (read-only access)
 *   - ComputeRecipeNutritionForSnapshotUseCase (pure computation)
 * - No mutation of recipe entities here; only reads + computation.
 *
 * Migration notes (KMP / time APIs if relevant)
 * - None; this use case is time-agnostic.
 *
 * Performance considerations
 * - One header lookup + one ingredient list read + one nutrition compute.
 * - If performance becomes a concern:
 *   - Consider a repository method that returns header + ingredients in a single query/transaction (data-layer change),
 *     but only if it doesn’t alter behavior.
 *   - Consider a “kcal-only” nutrition compute path upstream to avoid building full nutrient maps, but that is a
 *     deliberate future optimization (outside this pass).
 */