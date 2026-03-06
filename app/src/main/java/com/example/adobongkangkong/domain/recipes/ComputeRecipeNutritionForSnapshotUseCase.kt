package com.example.adobongkangkong.domain.recipes

import android.util.Log
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import com.example.adobongkangkong.domain.nutrition.dividedBy
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import javax.inject.Inject

/**
 * Computes full recipe nutrition totals and per-unit breakdowns using immutable per-gram nutrition snapshots.
 *
 * Purpose
 * - Produce a complete [RecipeNutritionResult] for a given [Recipe], including:
 *   - total nutrients for the entire recipe,
 *   - nutrients per serving (when servingsYield is valid),
 *   - nutrients per cooked gram (when totalYieldGrams is valid),
 *   - and diagnostic warnings describing any missing/invalid inputs encountered during computation.
 *
 * Rationale (why this use case exists)
 * - Recipe nutrition math must be deterministic and reproducible: using per-gram normalized snapshots prevents
 *   rejoining live/mutable food state during computations and aligns with the “snapshot-first” architecture.
 * - Import and editing flows are intentionally lax (they may produce incomplete recipes); instead of failing hard,
 *   this use case surfaces issues via [RecipeNutritionWarning] so the UI can guide the user.
 * - Correctness is enforced at point-of-use (logging/planning) by checking the result + warnings, rather than
 *   blocking computation entirely.
 *
 * Behavior
 * - Loads required food nutrition snapshots in a batch via [FoodNutritionSnapshotRepository.getSnapshots].
 * - Computes recipe totals by iterating ingredients and accumulating scaled nutrient maps:
 *   - Ingredient servings:
 *     - If null, defaults to 1.0 (intentional; see pitfalls).
 *     - If <= 0, adds [RecipeNutritionWarning.IngredientServingsNonPositive] and skips the ingredient.
 *   - Snapshot availability:
 *     - If missing snapshot → [RecipeNutritionWarning.MissingFood] and skip.
 *     - If gramsPerServingUnit missing/invalid → [RecipeNutritionWarning.MissingGramsPerServing] and skip.
 *     - If nutrientsPerGram missing → [RecipeNutritionWarning.MissingNutrientsPerGram] and skip.
 *   - Otherwise computes ingredient grams = servings * gramsPerServingUnit and adds nutrientsPerGram scaled by grams.
 * - Computes derived views:
 *   - perServing:
 *     - Requires recipe.servingsYield != null and > 0; otherwise adds Missing/Invalid warnings and returns null.
 *   - perCookedGram:
 *     - Requires recipe.totalYieldGrams != null and > 0; otherwise adds Missing/Invalid warnings and returns null.
 *   - gramsPerServingCooked:
 *     - Only set when both servingsYield and totalYieldGrams are present and > 0.
 * - Returns [RecipeNutritionResult] with warnings de-duplicated (distinct()).
 *
 * Parameters
 * - recipe: Recipe snapshot containing ingredient lines and optional yield metadata.
 *
 * Return
 * - [RecipeNutritionResult] containing:
 *   - totals: Total nutrients for the entire recipe (may be partial if some ingredients were skipped).
 *   - perServing: Nutrients per serving, or null if servingsYield missing/invalid.
 *   - perCookedGram: Nutrients per cooked gram, or null if totalYieldGrams missing/invalid.
 *   - gramsPerServingCooked: Cooked grams per serving, or null if yields are missing/invalid.
 *   - warnings: Non-fatal issues encountered during computation (missing snapshot data, invalid yields, etc.).
 *
 * Edge cases
 * - If all ingredients are skipped due to warnings, totals will be [NutrientMap.EMPTY].
 * - Null servings are defaulted to 1.0 (NOT 0.0) to keep math stable during editing and to surface unexpected nulls.
 * - Warnings may be produced both from missing yield metadata and from ingredient-level issues simultaneously.
 *
 * Pitfalls / gotchas
 * - Defaulting null servings to 1.0 is intentional and must remain stable: it prevents “silent zeroing” and helps keep
 *   recipe math meaningful during in-progress edits; callers should treat warnings as actionable signals.
 * - This use case is “lax by design”: it returns results even when partial. Callers that require strict correctness
 *   (e.g., logging modes) must gate using the presence of perServing/perCookedGram and/or warnings.
 * - Android logging ([Log]) in domain is a pragmatic debug aid; keep it low-noise and do not leak PII.
 *
 * Architectural rules (if applicable)
 * - Domain computation must operate on immutable nutrition snapshots (per-gram) and must not rejoin live food state.
 * - No DB/Room types here; persistence access is via [FoodNutritionSnapshotRepository].
 * - Logging model note: ISO-date-based logging uses `logDateIso` as authoritative; this use case does not read/write logs.
 * - Snapshot logs are immutable and must not rejoin foods; keep this computation pure and deterministic.
 */
class ComputeRecipeNutritionForSnapshotUseCase @Inject constructor(
    private val snapshotRepo: FoodNutritionSnapshotRepository
) {

    /**
     * Loads food snapshots in batch and computes full recipe nutrition.
     *
     * Purpose
     * - Provide the standard “one call” entry point that:
     *   - determines required food ids,
     *   - batch-loads snapshots,
     *   - and delegates to the pure computation step.
     *
     * Rationale
     * - Snapshot IO is an implementation detail; callers should not orchestrate snapshot loading.
     *
     * Behavior
     * - Builds a unique food id set from [recipe.ingredients].
     * - Calls [FoodNutritionSnapshotRepository.getSnapshots] once with the full set.
     * - Delegates to [computeWithSnapshots] for deterministic math.
     *
     * Parameters
     * - recipe: Recipe snapshot to compute nutrition for.
     *
     * Return
     * - [RecipeNutritionResult] produced by [computeWithSnapshots].
     *
     * Edge cases
     * - Duplicate ingredient foodIds are intentionally de-duped for snapshot loading efficiency.
     *
     * Pitfalls / gotchas
     * - This method is suspend due to snapshot IO; do not convert to a blocking call.
     *
     * Architectural rules
     * - Repository access only; no DB/Room API usage directly.
     */
    suspend operator fun invoke(recipe: Recipe): RecipeNutritionResult {
        val foodIds = recipe.ingredients.map { it.foodId }.toSet()
        val foodsById = snapshotRepo.getSnapshots(foodIds)

        return computeWithSnapshots(
            recipe = recipe,
            foodsById = foodsById
        )
    }

    /**
     * Pure computation step.
     *
     * Purpose
     * - Compute totals and derived per-unit nutrient maps deterministically using only in-memory inputs.
     *
     * Rationale
     * - Enables unit tests, previews/simulations, and reuse by flows that already have snapshots loaded.
     *
     * Behavior
     * - Accumulates ingredient contributions into totals, skipping invalid/missing inputs while emitting warnings.
     * - Derives per-serving and per-cooked-gram maps when yield metadata is valid; otherwise emits warnings.
     * - Returns a [RecipeNutritionResult] with de-duplicated warnings.
     *
     * Parameters
     * - recipe: Recipe snapshot containing ingredients and yield metadata.
     * - foodsById: Map of foodId -> [FoodNutritionSnapshot] used for computation.
     *
     * Return
     * - [RecipeNutritionResult] containing totals, derived maps (nullable), gramsPerServingCooked (nullable), and warnings.
     *
     * Edge cases
     * - Ingredients may reference foodIds missing from foodsById; those are skipped with warnings.
     *
     * Pitfalls / gotchas
     * - Do not “fix up” missing snapshot fields here; surface them as warnings and skip the ingredient.
     *
     * Architectural rules
     * - Must remain pure (no IO). Keep it internal to discourage accidental external dependency contracts.
     */
    internal fun computeWithSnapshots(
        recipe: Recipe,
        foodsById: Map<Long, FoodNutritionSnapshot>
    ): RecipeNutritionResult {

        val warnings = mutableListOf<RecipeNutritionWarning>()

        val totals = recipe.ingredients.fold(NutrientMap.Companion.EMPTY) { acc, ingredient ->
            val foodId = ingredient.foodId
            val servings = ingredient.servings ?: 1.0

            if (servings <= 0.0) {
                warnings += RecipeNutritionWarning.IngredientServingsNonPositive(foodId, servings)
                return@fold acc
            }

            val snapshot = foodsById[foodId]
            if (snapshot == null) {
                warnings += RecipeNutritionWarning.MissingFood(foodId)
                return@fold acc
            }

            val gramsPerServingUnit = snapshot.gramsPerServingUnit
            val mlPerServingUnit = snapshot.mlPerServingUnit
            val nutrientsPerGram = snapshot.nutrientsPerGram
            val nutrientsPerMilliliter = snapshot.nutrientsPerMilliliter

            when {
                gramsPerServingUnit != null && gramsPerServingUnit > 0.0 -> {
                    if (nutrientsPerGram == null) {
                        warnings += RecipeNutritionWarning.MissingNutrientsPerGram(foodId)
                        return@fold acc
                    }
                    val grams = servings * gramsPerServingUnit
                    acc + snapshot.nutrientsForGrams(grams)
                }

                mlPerServingUnit != null && mlPerServingUnit > 0.0 -> {
                    if (nutrientsPerMilliliter == null) {
                        warnings += RecipeNutritionWarning.MissingNutrientsPerMilliliter(foodId)
                        return@fold acc
                    }
                    val milliliters = servings * mlPerServingUnit
                    acc + snapshot.nutrientsForMilliliters(milliliters)
                }

                else -> {
                    warnings += RecipeNutritionWarning.MissingGramsPerServing(foodId)
                    warnings += RecipeNutritionWarning.MissingMlPerServing(foodId)
                    acc
                }
            }
        }

        val servingsYield = recipe.servingsYield
        val totalYieldGrams = recipe.totalYieldGrams

        val perServing: NutrientMap? = when {
            servingsYield == null -> {
                warnings += RecipeNutritionWarning.MissingServingsYield
                null
            }
            servingsYield <= 0 -> {
                warnings += RecipeNutritionWarning.InvalidServingsYield(servingsYield)
                null
            }
            else -> totals.dividedBy(servingsYield.toDouble())
        }

        val perCookedGram: NutrientMap? = when {
            totalYieldGrams == null -> {
                warnings += RecipeNutritionWarning.MissingTotalYieldGrams
                null
            }
            totalYieldGrams <= 0 -> {
                warnings += RecipeNutritionWarning.InvalidTotalYieldGrams(totalYieldGrams)
                null
            }
            else -> totals.dividedBy(totalYieldGrams.toDouble())
        }

        val gramsPerServingCooked: Double? = when {
            servingsYield != null && servingsYield > 0 &&
                    totalYieldGrams != null && totalYieldGrams > 0 ->
                totalYieldGrams.toDouble() / servingsYield.toDouble()
            else -> null
        }
        Log.d(
            "Meow",
            "Snapshot> recipeId=${recipe.id} ingredients=${recipe.ingredients.size} " +
                    "snapshotsLoaded=${foodsById.size} totalsSize=${totals.entries().size} warnings=${warnings.size}"
        )
        return RecipeNutritionResult(
            totals = totals,
            perServing = perServing,
            perCookedGram = perCookedGram,
            gramsPerServingCooked = gramsPerServingCooked,
            warnings = warnings.distinct()
        )
    }
}

/**
 * ===== Bottom KDoc (for future AI assistant) =====
 *
 * Invariants (what must not change)
 * - Import is lax: computation should return a result with warnings rather than throwing for missing data.
 * - Ingredient-level rules:
 *   - ingredient.servings null must default to 1.0 (NOT 0.0) exactly as implemented.
 *   - servings <= 0 must produce IngredientServingsNonPositive and skip that ingredient.
 *   - Missing snapshot must produce MissingFood and skip.
 *   - Missing/invalid gramsPerServingUnit must produce MissingGramsPerServing and skip.
 *   - Missing nutrientsPerGram must produce MissingNutrientsPerGram and skip.
 * - Derived maps:
 *   - perServing is null unless servingsYield != null and > 0 (emit Missing/Invalid warnings otherwise).
 *   - perCookedGram is null unless totalYieldGrams != null and > 0 (emit Missing/Invalid warnings otherwise).
 *   - gramsPerServingCooked is non-null only when both yields are present and > 0.
 * - Warnings must be de-duplicated via distinct() on return.
 * - Must not rejoin foods or use mutable food state; only snapshotRepo snapshots.
 * - Logging model: ISO-date-based logging uses logDateIso as authoritative; this use case must remain time-agnostic.
 * - Snapshot logs are immutable and must not rejoin foods; no hydration or “latest food” lookups here.
 *
 * Do not refactor notes
 * - Do not change default servings behavior (null -> 1.0) or warning types/messages without auditing UI flows.
 * - Do not rewrite fold/early-return structure if it changes which ingredients contribute to totals in edge cases.
 * - Keep computeWithSnapshots internal and pure; do not introduce IO into it.
 *
 * Architectural boundaries
 * - Domain-only computation:
 *   - All IO is through FoodNutritionSnapshotRepository.getSnapshots (in invoke()).
 *   - computeWithSnapshots must remain IO-free.
 * - No Room/DAO types, no UI, no navigation.
 *
 * Migration notes (KMP / time APIs if relevant)
 * - Current file imports android.util.Log; if/when migrating domain to KMP, replace with a platform-agnostic logger
 *   abstraction (e.g., an injected Logger interface) and keep the message content equivalent.
 *
 * Performance considerations
 * - Snapshot loading is batched by unique foodIds (set) to minimize IO.
 * - Computation is linear in ingredient count; each ingredient does constant work plus a NutrientMap scaling/add.
 * - If NutrientMap operations become hot:
 *   - Consider optimizing NutrientMap.scaledBy and plus operations (data-structure-level change, outside this pass).
 * - Logging can be noisy on frequent recomputation; if it impacts performance, consider guarding with a debug flag
 *   (future refactor only—do not change behavior in this pass).
 *
 * Recommendations (maintenance / streamlining / performance)
 * - The Log tag "Meow" is non-descriptive; consider standardizing tags or using a shared logger (future refactor).
 * - Consider documenting warning ordering expectations (currently: ingredient warnings accumulated during fold,
 *   then yield warnings appended). If UI depends on order, make it explicit.
 * - If callers frequently already have snapshots, consider exposing a public test-only entry or keeping internal
 *   and providing a dedicated facade use case; but avoid widening the API surface unintentionally.
 */