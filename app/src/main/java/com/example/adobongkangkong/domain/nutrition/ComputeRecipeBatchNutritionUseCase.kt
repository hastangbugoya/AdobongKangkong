package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.domain.recipes.FoodNutritionSnapshot
import com.example.adobongkangkong.domain.recipes.Recipe
import com.example.adobongkangkong.domain.recipes.RecipeNutritionResult
import com.example.adobongkangkong.domain.recipes.RecipeNutritionWarning
import com.example.adobongkangkong.domain.recipes.nutrientsForGrams
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import com.example.adobongkangkong.domain.repository.RecipeIngredientLine
import com.example.adobongkangkong.domain.repository.RecipeRepository
import javax.inject.Inject

/**
 * Canonical recipe-batch nutrition computation for servings-based ingredients using immutable snapshots.
 *
 * ## Purpose
 * Compute nutrition totals for an entire recipe batch and optionally derive:
 * - per-serving nutrients,
 * - per-cooked-gram nutrients,
 * - cooked grams per serving,
 * while emitting warnings when recipe metadata or ingredient data is incomplete.
 *
 * ## Rationale (why this use case exists)
 * Recipes are editable and ingredient foods can change over time. For correctness and historical
 * stability, recipe nutrition computation must use *snapshot* nutrition inputs for ingredient foods
 * (not live joins back to mutable food entities).
 *
 * This use case centralizes:
 * - the canonical math for summing ingredient nutrients,
 * - the rules for deriving per-serving and per-gram breakdowns,
 * - the warning policy for missing/invalid fields,
 * so all call sites (recipe screen, logging, planner) behave consistently.
 *
 * ## Behavior
 * Data inputs:
 * - Loads recipe header + ingredient lines from [RecipeRepository] when called by recipeFoodId.
 * - Loads ingredient nutrition snapshots via [FoodNutritionSnapshotRepository] for all ingredient foodIds.
 *
 * Batch totals:
 * - For each ingredient line:
 *   - Uses `ingredientServings` (nullable) and defaults null to `1.0` (see pitfalls).
 *   - Requires `gramsPerServingUnit` on snapshot (> 0).
 *   - Requires `nutrientsPerGram` on snapshot.
 *   - Computes grams = servings * gramsPerServingUnit.
 *   - Adds snapshot nutrients for grams.
 *
 * Derived outputs:
 * - perServing:
 *   - If servingsYield missing/invalid/non-positive → warning + null.
 *   - Else totals / servingsYield.
 * - perCookedGram:
 *   - If totalYieldGrams missing/invalid/non-positive → warning + null.
 *   - Else totals / totalYieldGrams.
 * - gramsPerServingCooked:
 *   - Only when servingsYield valid (>0) and totalYieldGrams valid (>0) and not missing-yield warning.
 *   - totalYieldGrams / servingsYield.
 *
 * ## Parameters
 * Entry points:
 * - `execute(recipeFoodId: Long)`: Recipe is identified by its recipe “foodId” (editable recipe definition).
 * - `execute(recipe: Recipe)`: Computes from an in-memory recipe (used after applying overrides).
 * - `invoke(recipe: Recipe)`: Back-compat operator wrapper around execute(recipe).
 *
 * ## Return
 * A [RecipeNutritionResult] containing:
 * - `totals`: NutrientMap for entire batch (may be EMPTY if no usable ingredients).
 * - `perServing`: NutrientMap? (null if servings yield missing/invalid).
 * - `perCookedGram`: NutrientMap? (null if total yield grams missing/invalid).
 * - `gramsPerServingCooked`: Double? (null if either prerequisite missing/invalid).
 * - `warnings`: List of [RecipeNutritionWarning] explaining missing/invalid inputs.
 *
 * ## Edge cases
 * - Missing recipe header (recipeFoodId not found) → returns EMPTY totals + no warnings.
 * - Missing ingredient snapshot → warning per missing food; ingredient contributes nothing.
 * - Non-positive servings on a line → warning; ingredient skipped.
 * - Missing grams-per-serving or nutrients-per-gram → warning; ingredient skipped.
 *
 * ## Pitfalls / gotchas
 * - **Defaulting null ingredient servings to 1.0 is intentional** to preserve “recipe math integrity”
 *   and keep unexpected nulls visible in UI while still producing a usable estimate.
 * - This use case currently supports **mass-only scaling** (grams). It must not guess density or
 *   convert between grams and mL without an explicit volume-capable snapshot model.
 * - Warnings are part of the contract; do not silently “fix” invalid data without recording warnings.
 *
 * ## Architectural rules
 * - Uses snapshot-based ingredient nutrition via [FoodNutritionSnapshotRepository]; no joins to live foods.
 * - Does not write to repositories; pure read + compute + warnings.
 * - No UI state, no navigation, no database APIs.
 */
class ComputeRecipeBatchNutritionUseCase @Inject constructor(
    private val recipeRepo: RecipeRepository,
    private val snapshotRepo: FoodNutritionSnapshotRepository
) {

    /**
     * Computes nutrition for a recipe identified by its recipe "foodId" (editable recipe definition).
     *
     * @param recipeFoodId The foodId representing the editable recipe definition.
     * @return [RecipeNutritionResult] containing totals, optional derived maps, and warnings.
     */
    suspend fun execute(recipeFoodId: Long): RecipeNutritionResult {
        val header = recipeRepo.getRecipeByFoodId(recipeFoodId)
            ?: return RecipeNutritionResult(
                totals = NutrientMap.EMPTY,
                perServing = null,
                perCookedGram = null,
                gramsPerServingCooked = null,
                warnings = emptyList()
            )

        val ingredientLines: List<RecipeIngredientLine> = recipeRepo.getIngredients(header.recipeId)

        val ingredientFoodIds: Set<Long> = ingredientLines
            .map { it.ingredientFoodId }
            .toSet()

        val foodsById: Map<Long, FoodNutritionSnapshot> = snapshotRepo.getSnapshots(ingredientFoodIds)

        return computeFromServingsLines(
            servingsYield = header.servingsYield,
            totalYieldGrams = header.totalYieldGrams,
            lines = ingredientLines,
            foodsById = foodsById
        )
    }

    /**
     * Computes nutrition from an in-memory Recipe (used by CreateLogEntryUseCase after applying overrides).
     *
     * @param recipe In-memory recipe that may have overrides applied.
     * @return [RecipeNutritionResult] containing totals, optional derived maps, and warnings.
     */
    suspend fun execute(recipe: Recipe): RecipeNutritionResult {
        val ingredientFoodIds: Set<Long> = recipe.ingredients
            .map { it.foodId }
            .toSet()

        val foodsById: Map<Long, FoodNutritionSnapshot> = snapshotRepo.getSnapshots(ingredientFoodIds)

        // Adapt RecipeIngredient -> RecipeIngredientLine shape (servings-only model)
        val lines: List<RecipeIngredientLine> = recipe.ingredients.map {
            RecipeIngredientLine(
                ingredientFoodId = it.foodId,
                ingredientServings = it.servings
            )
        }

        // NOTE: your Recipe model has nullable servingsYield/totalYieldGrams; compute expects Double/Double?
        // We preserve your warning behavior by supplying a sentinel for servingsYield if missing.
        val servingsYield: Double = recipe.servingsYield ?: run {
            // will be warned as MissingServingsYield below, but compute() needs a Double
            0.0
        }

        return computeFromServingsLines(
            servingsYield = servingsYield,
            totalYieldGrams = recipe.totalYieldGrams,
            lines = lines,
            foodsById = foodsById,
            includeMissingServingsYieldWarning = recipe.servingsYield == null
        )
    }

    /**
     * Optional: keeps old call sites working (computeUseCase(recipe)).
     */
    suspend operator fun invoke(recipe: Recipe): RecipeNutritionResult = execute(recipe)

    private fun computeFromServingsLines(
        servingsYield: Double,
        totalYieldGrams: Double?,
        lines: List<RecipeIngredientLine>,
        foodsById: Map<Long, FoodNutritionSnapshot>,
        includeMissingServingsYieldWarning: Boolean = false
    ): RecipeNutritionResult {

        val warnings = mutableListOf<RecipeNutritionWarning>()

        // totals for entire recipe batch
        val totals = lines.fold(NutrientMap.EMPTY) { acc, line ->
            val foodId = line.ingredientFoodId
            // ⚠️ Default null servings to 1.0.
            // UI layer allows nullable servings during editing, but the domain draft
            // requires a non-null Double for nutrition scaling and planner expansion.
            // We intentionally default to 1.0 (NOT 0.0) to preserve recipe math integrity
            // and make unexpected null states visible in the UI.
            val servings = line.ingredientServings ?: 1.0

            if (servings <= 0.0) {
                warnings += RecipeNutritionWarning.IngredientServingsNonPositive(foodId, servings)
                return@fold acc
            }

            val snapshot = foodsById[foodId]
            if (snapshot == null) {
                warnings += RecipeNutritionWarning.MissingFood(foodId)
                return@fold acc
            }

            val gpsu = snapshot.gramsPerServingUnit
            if (gpsu == null || gpsu <= 0.0) {
                warnings += RecipeNutritionWarning.MissingGramsPerServing(foodId)
                return@fold acc
            }

            if (snapshot.nutrientsPerGram == null) {
                warnings += RecipeNutritionWarning.MissingNutrientsPerGram(foodId)
                return@fold acc
            }

            val grams = servings * gpsu
            acc + snapshot.nutrientsForGrams(grams)
        }

        // per-serving
        val perServing: NutrientMap? = when {
            includeMissingServingsYieldWarning -> {
                warnings += RecipeNutritionWarning.MissingServingsYield
                null
            }
            servingsYield.isNaN() -> {
                warnings += RecipeNutritionWarning.InvalidServingsYield(servingsYield)
                null
            }
            servingsYield <= 0.0 -> {
                warnings += RecipeNutritionWarning.InvalidServingsYield(servingsYield)
                null
            }
            else -> totals.scaledBy(1.0 / servingsYield)
        }

        // per cooked gram
        val perCookedGram: NutrientMap? = when {
            totalYieldGrams == null -> {
                warnings += RecipeNutritionWarning.MissingTotalYieldGrams
                null
            }
            totalYieldGrams <= 0.0 -> {
                warnings += RecipeNutritionWarning.InvalidTotalYieldGrams(totalYieldGrams)
                null
            }
            else -> totals.scaledBy(1.0 / totalYieldGrams)
        }

        val gramsPerServingCooked: Double? =
            if (!includeMissingServingsYieldWarning && servingsYield > 0.0 && totalYieldGrams != null && totalYieldGrams > 0.0) {
                totalYieldGrams / servingsYield
            } else {
                null
            }

        return RecipeNutritionResult(
            totals = totals,
            perServing = perServing,
            perCookedGram = perCookedGram,
            gramsPerServingCooked = gramsPerServingCooked,
            warnings = warnings
        )
    }
}

/**
 * FUTURE-AI / MAINTENANCE KDoc (Do not remove)
 *
 * ## Invariants (must not change)
 * - Snapshot-based computation:
 *   - Ingredient nutrition must come from [FoodNutritionSnapshotRepository] snapshots (immutable inputs).
 *   - Do not join back to live foods/recipes for nutrient computation.
 * - Mass-only scaling:
 *   - Computation currently depends on `gramsPerServingUnit` and `nutrientsPerGram`.
 *   - Do not add grams↔mL conversion or density guessing here.
 * - Warning-driven degradation:
 *   - Missing/invalid ingredient data must add warnings and skip that ingredient.
 *   - Missing/invalid servingsYield or totalYieldGrams must add warnings and set derived outputs to null.
 * - Null ingredient servings default remains `1.0` (intentional, do not change without UI+domain audit).
 *
 * ## Do not refactor notes
 * - Do not “simplify” by removing the warnings list; it is part of the contract used by UI.
 * - Do not change repository API assumptions:
 *   - RecipeRepository: getRecipeByFoodId(recipeFoodId), getIngredients(recipeId).
 * - Keep execute(...) overloads:
 *   - One loads from repository by recipeFoodId.
 *   - One computes from in-memory Recipe (used after overrides).
 * - Avoid “optimizing” to parallel snapshot loads without ensuring repository supports it safely.
 *
 * ## Architectural boundaries
 * - Domain-layer compute only: reads recipe + snapshots, returns result + warnings.
 * - No persistence writes, no navigation, no UI concerns.
 *
 * ## Migration notes (KMP / time APIs)
 * - No time APIs involved.
 * - KMP-safe as long as snapshot/repository abstractions remain platform-agnostic.
 *
 * ## Performance considerations
 * - Complexity is O(N) over ingredient lines.
 * - Snapshot loading cost depends on repository implementation; consider batch fetching (already used via getSnapshots(Set<Long>)).
 *
 * ## Future extension (volume / liquids)
 * - Before supporting liquid ingredients:
 *   1) Extend FoodNutritionSnapshot to include `mlPerServingUnit` and `nutrientsPerMilliliter`.
 *   2) Extend snapshot mapper to populate those fields for volume-grounded foods.
 *   3) Update computeFromServingsLines to branch grams vs mL without density guessing.
 */