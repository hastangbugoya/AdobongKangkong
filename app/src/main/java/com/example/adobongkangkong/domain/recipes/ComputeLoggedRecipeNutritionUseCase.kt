package com.example.adobongkangkong.domain.recipes

import com.example.adobongkangkong.domain.nutrition.NutrientMap
import javax.inject.Inject

/**
 * Computes nutrition totals for a recipe log event based on the user’s chosen log mode (cooked grams or servings).
 *
 * Purpose
 * - Convert a precomputed [RecipeNutritionResult] (per-unit nutrition) into concrete totals suitable for logging,
 *   while enforcing point-of-use rules for which logging modes are currently allowed.
 *
 * Rationale (why this use case exists)
 * - Recipe nutrition computation can be performed independently from logging decisions (import and computation can be lax),
 *   but logging must be strict: some log modes require yield metadata to be present.
 * - Centralizing “can this be logged?” gating here prevents UI and callers from duplicating rules and warning assembly.
 *
 * Behavior
 * - Starts with upstream warnings from [recipeNutrition.warnings].
 * - Applies additional gating + validation based on [input]:
 *   - [RecipeLogInput.ByCookedGrams]
 *     - Rejects non-positive grams (<= 0) and adds [RecipeNutritionWarning.InvalidTotalYieldGrams].
 *     - Requires [RecipeNutritionResult.perCookedGram] to exist; if missing, adds [RecipeNutritionWarning.MissingTotalYieldGrams].
 *     - If allowed, returns totals = perCookedGram scaled by grams.
 *   - [RecipeLogInput.ByServings]
 *     - Rejects non-positive servings (<= 0) and adds [RecipeNutritionWarning.InvalidServingsYield].
 *     - Requires [RecipeNutritionResult.perServing] to exist; if missing, adds [RecipeNutritionWarning.MissingServingsYield].
 *     - If allowed, returns totals = perServing scaled by servings.
 * - When blocked, totals are always [NutrientMap.EMPTY] and `isAllowed = false`.
 *
 * Parameters
 * - recipeNutrition: Precomputed recipe nutrition, including per-unit values (per cooked gram and/or per serving) and warnings.
 * - input: The desired logging mode and amount (cooked grams or servings).
 *
 * Return
 * - [LoggedRecipeNutritionResult] containing:
 *   - totals: Scaled totals if allowed, otherwise [NutrientMap.EMPTY]
 *   - warnings: Upstream warnings plus any gating/validation warnings added here
 *   - isAllowed: True only when the selected log mode is supported and the amount is valid (> 0)
 *
 * Edge cases
 * - If upstream computation produced warnings (missing nutrients, partial data), they are always preserved and returned.
 * - If both per-unit values are absent upstream, the result will be blocked for whichever input mode is requested.
 * - Non-positive input amounts are treated as invalid at point-of-use and return blocked results.
 *
 * Pitfalls / gotchas
 * - Warning types intentionally double as “why blocked” indicators; do not replace them with generic messages.
 * - The naming of warnings (e.g., InvalidTotalYieldGrams) is historical: in this use case the invalid value is the
 *   requested log amount. Do not “correct” naming here without auditing all consumers.
 * - This use case does not perform any food re-joins or snapshot hydration; it operates strictly on provided inputs.
 *
 * Architectural rules (if applicable)
 * - Domain-only decision layer: no DB, no UI, no navigation, no date/time logic.
 * - Logging model note: ISO-date-based logging uses `logDateIso` as authoritative, but this use case does not read/write logs.
 * - Snapshot logs are immutable and must not rejoin foods; keep this use case pure and deterministic.
 */
class ComputeLoggedRecipeNutritionUseCase @Inject constructor() {

    operator fun invoke(
        recipeNutrition: RecipeNutritionResult,
        input: RecipeLogInput
    ): LoggedRecipeNutritionResult {
        val warnings = recipeNutrition.warnings.toMutableList()

        return when (input) {
            is RecipeLogInput.ByCookedGrams -> {
                val grams = input.grams
                if (grams <= 0.0) {
                    warnings += RecipeNutritionWarning.InvalidTotalYieldGrams(grams)
                    return LoggedRecipeNutritionResult(
                        totals = NutrientMap.EMPTY,
                        warnings = warnings,
                        isAllowed = false
                    )
                }

                val perCookedGram = recipeNutrition.perCookedGram
                if (perCookedGram == null) {
                    // Gate: can't log by grams without cooked yield
                    warnings += RecipeNutritionWarning.MissingTotalYieldGrams
                    return LoggedRecipeNutritionResult(
                        totals = NutrientMap.EMPTY,
                        warnings = warnings,
                        isAllowed = false
                    )
                }

                LoggedRecipeNutritionResult(
                    totals = perCookedGram.scaledBy(grams),
                    warnings = warnings,
                    isAllowed = true
                )
            }

            is RecipeLogInput.ByServings -> {
                val servings = input.servings
                if (servings <= 0.0) {
                    warnings += RecipeNutritionWarning.InvalidServingsYield(servings)
                    return LoggedRecipeNutritionResult(
                        totals = NutrientMap.EMPTY,
                        warnings = warnings,
                        isAllowed = false
                    )
                }

                val perServing = recipeNutrition.perServing
                if (perServing == null) {
                    // Gate: can't log by servings without servingsYield
                    warnings += RecipeNutritionWarning.MissingServingsYield
                    return LoggedRecipeNutritionResult(
                        totals = NutrientMap.EMPTY,
                        warnings = warnings,
                        isAllowed = false
                    )
                }

                LoggedRecipeNutritionResult(
                    totals = perServing.scaledBy(servings),
                    warnings = warnings,
                    isAllowed = true
                )
            }
        }
    }
}

/**
 * ===== Bottom KDoc (for future AI assistant) =====
 *
 * Invariants (what must not change)
 * - Must preserve upstream warnings from recipeNutrition.warnings and append gating/validation warnings (never discard).
 * - Blocked results must always return:
 *   - totals = NutrientMap.EMPTY
 *   - isAllowed = false
 *   - warnings include a specific reason (Invalid* or Missing*)
 * - Allowed results must always return:
 *   - totals = scaled per-unit map
 *   - isAllowed = true
 *   - warnings still include upstream warnings (even when allowed)
 * - This use case must remain pure/deterministic: no IO, no DB, no time/date, no food re-joins.
 * - Snapshot logs are immutable and must not rejoin foods; do not introduce hydration or “latest food” lookups here.
 *
 * Do not refactor notes
 * - Do not change warning types or their attachment points without auditing all downstream rendering/analytics.
 * - Do not “simplify” by removing early returns if it changes the exact warnings list or order.
 *
 * Architectural boundaries
 * - Domain only; callable from UI and other domain orchestration.
 * - No dependence on Android framework types.
 * - No creation or mutation of log rows; output is purely derived totals + warnings.
 *
 * Migration notes (KMP / time APIs)
 * - None; intentionally time-agnostic.
 *
 * Performance considerations
 * - O(1) branching and a single scaling operation per allowed call.
 * - Warnings list copying is minimal (toMutableList). Keep it that way unless warnings grow extremely large.
 *
 * Recommendations (maintenance / streamlining / performance)
 * - Consider renaming warning types in the future (outside this pass) to align semantics:
 *   - InvalidTotalYieldGrams(currently used for “invalid logged grams input”) could be confusing.
 *   - If renamed, provide a compatibility layer or migration for any UI that pattern-matches warning classes.
 * - Consider extracting the repeated “blocked return” construction into a tiny private helper in a future refactor:
 *   - It would reduce duplication, but only do this if you can guarantee warnings content/order is unchanged.
 * - If warning order becomes user-visible and meaningful, consider explicitly documenting whether upstream warnings
 *   should appear before gating warnings (current behavior: upstream first, gating appended).
 */