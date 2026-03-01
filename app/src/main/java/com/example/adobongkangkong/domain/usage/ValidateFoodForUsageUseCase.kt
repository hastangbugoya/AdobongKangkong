package com.example.adobongkangkong.domain.usage

import com.example.adobongkangkong.domain.logging.model.AmountInput
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.isMassUnit
import com.example.adobongkangkong.domain.model.isVolumeUnit
import com.example.adobongkangkong.domain.model.requiresGramsPerServing
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import com.example.adobongkangkong.domain.recipes.FoodNutritionSnapshot
import javax.inject.Inject

/**
 * Unified food validation / completeness checks for domain usage decisions.
 *
 * ## What this use case solves
 * We need ONE consistent, domain-owned decision-maker for:
 *
 * - "Can this food be logged with this amount input?"
 * - "Can this food be used in recipes (and if blocked, why)?"
 * - "What user-facing message should we show when something is missing?"
 *
 * This prevents drift between:
 * - Foods list "fix" banners
 * - Food editor "needs fix" banners
 * - CreateLogEntry gating (logging flow)
 *
 * ## Core invariants (locked-in)
 * - No silent grams↔mL conversion (no density guessing).
 * - Volume-grounded foods do NOT require grams-per-serving to be loggable by servings.
 *   "Volume-grounded" means:
 *     - servingUnit is a deterministic volume unit (asMl), OR
 *     - mlPerServingUnit bridge is present (> 0).
 * - A food with no usable nutrient basis is blocked for LOGGING and RECIPE contexts.
 *
 * ## Why both "persisted" + "draft" entry points
 * - Persisted validation uses a [FoodNutritionSnapshot] (from the snapshot repository).
 * - Draft validation is for FoodEditor, which has unsaved nutrient rows instead of a snapshot.
 *
 * Both paths share the same bridge/grounding rules and the same messages/reasons.
 */
class ValidateFoodForUsageUseCase @Inject constructor() {

    // -------------------------------------------------------------------------
    // Inputs
    // -------------------------------------------------------------------------

    /**
     * Persisted validation input (used by FoodsList + CreateLogEntry).
     *
     * Snapshot can be null when:
     * - the food has never had nutrients saved
     * - the snapshot pipeline cannot produce a canonical basis
     */
    data class PersistedInput(
        val servingUnit: ServingUnit,
        val gramsPerServingUnit: Double?,
        val mlPerServingUnit: Double?,
        val amountInput: AmountInput,
        val context: UsageContext,
        val snapshot: FoodNutritionSnapshot?
    )

    /**
     * Draft nutrient summary for FoodEditor validation.
     *
     * Draft has no snapshot; we only know:
     * - whether the editor currently has nutrient rows
     * - whether any row contains a numeric value
     *
     * "All nutrients zero" is not enforced here by default (draft is free-form UI data),
     * but you can add it later if you want to treat explicit zeros as incomplete.
     */
    data class DraftNutrients(
        val hasAnyRows: Boolean,
        val hasAnyNumeric: Boolean
    )

    /**
     * Draft validation input (used by FoodEditor).
     *
     * Note: FoodEditor "fix banner" is about "loggable / recipe-usable by servings"
     * (not necessarily a specific amount entry mode). We therefore validate it as
     * servings-based completeness.
     */
    data class DraftInput(
        val servingUnit: ServingUnit,
        val gramsPerServingUnit: Double?,
        val mlPerServingUnit: Double?,
        val context: UsageContext,
        val draft: DraftNutrients
    )

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Persisted (snapshot-backed) validation.
     *
     * Use this everywhere outside FoodEditor:
     * - FoodsList
     * - CreateLogEntry (logging gate)
     * - future: recipe ingredient pickers
     */
    fun execute(input: PersistedInput): FoodValidationResult {
        val (unit, gpsu, mlpsu, amount, context, snapshot) = input

        // ---------- Snapshot gate ----------
        val snap = snapshot ?: return blocked(
            reason = FoodValidationResult.Reason.MissingSnapshot,
            message = msgMissingSnapshot(context)
        )

        // Determine canonical nutrient basis present
        val massMap = snap.nutrientsPerGram
        val volMap = snap.nutrientsPerMilliliter

        val hasMass = massMap != null && !massMap.isEmpty()
        val hasVol = volMap != null && !volMap.isEmpty()

        if (!hasMass && !hasVol) {
            return blocked(
                reason = FoodValidationResult.Reason.MissingNutrients,
                message = msgMissingNutrients()
            )
        }

        // If chosen basis exists but all values are zero, treat as incomplete.
        if ((hasMass && allZero(massMap)) || (hasVol && allZero(volMap))) {
            return blocked(
                reason = FoodValidationResult.Reason.AllNutrientsZero,
                message = msgAllZeroNutrients()
            )
        }

        // ---------- Serving grounding gate (shared) ----------
        val grounding = validateServingGrounding(
            servingUnit = unit,
            gramsPerServingUnit = gpsu,
            mlPerServingUnit = mlpsu,
            amountInput = amount,
            context = context
        )
        if (grounding is FoodValidationResult.Blocked) return grounding

        // ---------- Basis/amount compatibility gate ----------
        return validateBasisCompatibility(
            hasMass = hasMass,
            hasVol = hasVol,
            servingUnit = unit,
            gramsPerServingUnit = gpsu,
            mlPerServingUnit = mlpsu,
            amountInput = amount
        )
    }

    /**
     * Draft (FoodEditor) validation.
     *
     * This intentionally validates editor completeness as "servings-based usage",
     * because that's what matters for "loggable / recipe usable" banners:
     * - serving backing bridges
     * - "has nutrients"
     * - "nutrient amounts not blank"
     */
    fun executeDraft(input: DraftInput): FoodValidationResult {
        val (unit, gpsu, mlpsu, context, draft) = input

        if (!draft.hasAnyRows) {
            return blocked(
                reason = FoodValidationResult.Reason.MissingNutrients,
                message = msgDraftNoRows()
            )
        }

        if (!draft.hasAnyNumeric) {
            return blocked(
                reason = FoodValidationResult.Reason.BlankNutrients,
                message = msgDraftBlankAmounts()
            )
        }

        // Draft banners are about "using servings". Enforce grounding accordingly.
        val amount = AmountInput.ByServings(1.0)

        val grounding = validateServingGrounding(
            servingUnit = unit,
            gramsPerServingUnit = gpsu,
            mlPerServingUnit = mlpsu,
            amountInput = amount,
            context = context
        )
        if (grounding is FoodValidationResult.Blocked) return grounding

        // Draft does NOT know snapshot basis (per-g vs per-mL). We stop here.
        return FoodValidationResult.Ok
    }

    // -------------------------------------------------------------------------
    // Shared validators (private)
    // -------------------------------------------------------------------------

    private fun validateServingGrounding(
        servingUnit: ServingUnit,
        gramsPerServingUnit: Double?,
        mlPerServingUnit: Double?,
        amountInput: AmountInput,
        context: UsageContext
    ): FoodValidationResult {
        val needsBacking = servingUnit.requiresGramsPerServing()
        val isServingBased = amountInput is AmountInput.ByServings

        // LOCKED-IN: Volume-grounded means grams are not required for servings-based entry.
        val isVolumeGrounded =
            servingUnit.isVolumeUnit() || (mlPerServingUnit?.takeIf { it > 0.0 } != null)

        if (needsBacking && isServingBased && (gramsPerServingUnit == null || gramsPerServingUnit <= 0.0) && !isVolumeGrounded) {
            val noun = when (context) {
                UsageContext.LOGGING -> "log this food by serving"
                UsageContext.RECIPE -> "use this food in a recipe by servings"
            }
            return blocked(
                reason = FoodValidationResult.Reason.MissingGramsPerServing,
                message = "Set grams-per-serving to $noun (no density guessing)."
            )
        }

        return FoodValidationResult.Ok
    }
    /**
     * Validates ONLY the "servings grounding" rule, without requiring a snapshot.
     *
     * Use cases:
     * - CSV import warnings ("this food will be blocked when logging by servings")
     * - Any UX that wants to warn about missing grams-per-serving / ml-per-serving bridges
     *   without loading snapshots.
     *
     * Important:
     * - This does NOT check nutrients or snapshot presence.
     * - This intentionally validates using servings-based input (ByServings),
     *   since grounding is only relevant when the user uses servings.
     */
    fun validateServingsGroundingOnly(
        servingUnit: ServingUnit,
        gramsPerServingUnit: Double?,
        mlPerServingUnit: Double?,
        context: UsageContext
    ): FoodValidationResult = validateServingGrounding(
        servingUnit = servingUnit,
        gramsPerServingUnit = gramsPerServingUnit,
        mlPerServingUnit = mlPerServingUnit,
        amountInput = AmountInput.ByServings(1.0),
        context = context
    )

    private fun validateBasisCompatibility(
        hasMass: Boolean,
        hasVol: Boolean,
        servingUnit: ServingUnit,
        gramsPerServingUnit: Double?,
        mlPerServingUnit: Double?,
        amountInput: AmountInput
    ): FoodValidationResult {
        // If volume-only nutrients, grams input cannot be supported (no density).
        if (!hasMass && hasVol) {
            return when (amountInput) {
                is AmountInput.ByGrams -> blocked(
                    reason = FoodValidationResult.Reason.BasisMismatchVolumeNeedsServings,
                    message = "This food is volume-based; log it by servings (or add mass-based nutrients)."
                )

                is AmountInput.ByServings -> {
                    // Need to be able to compute mL from servings:
                    // deterministic volume unit OR ml-per-serving bridge
                    val canComputeMl =
                        servingUnit.isVolumeUnit() || (mlPerServingUnit?.takeIf { it > 0.0 } != null)

                    if (!canComputeMl) blocked(
                        reason = FoodValidationResult.Reason.MissingMlPerServing,
                        message = "Set mL-per-serving (or use a deterministic volume unit) to log this volume-based food by servings."
                    ) else FoodValidationResult.Ok
                }
            }
        }

        // If mass-only nutrients, servings require grams.
        if (hasMass && !hasVol) {
            return when (amountInput) {
                is AmountInput.ByGrams -> FoodValidationResult.Ok

                is AmountInput.ByServings -> {
                    val canComputeGrams =
                        (gramsPerServingUnit?.takeIf { it > 0.0 } != null) ||
                                servingUnit == ServingUnit.G ||
                                servingUnit.isMassUnit()

                    if (!canComputeGrams) blocked(
                        reason = FoodValidationResult.Reason.BasisMismatchMassNeedsGrams,
                        message = "Set grams-per-serving to log this food by servings."
                    ) else FoodValidationResult.Ok
                }
            }
        }

        // If both exist, allow (call site will choose the right basis to scale by).
        return FoodValidationResult.Ok
    }

    private fun allZero(m: NutrientMap?): Boolean {
        if (m == null) return false
        val e = m.entries()
        if (e.isEmpty()) return false
        return e.all { (_, v) -> v == 0.0 }
    }

    private fun blocked(reason: FoodValidationResult.Reason, message: String): FoodValidationResult =
        FoodValidationResult.Blocked(reason = reason, message = message)

    // -------------------------------------------------------------------------
    // Message helpers (single wording source)
    // -------------------------------------------------------------------------

    private fun msgMissingSnapshot(context: UsageContext): String =
        when (context) {
            UsageContext.LOGGING -> "Food has no nutrients snapshot and cannot be logged."
            UsageContext.RECIPE -> "Food has no nutrients snapshot and cannot be used in recipes."
        }

    private fun msgMissingNutrients(): String =
        "Food has no nutrient data; enter nutrients to log or use in recipes."

    private fun msgAllZeroNutrients(): String =
        "Food nutrients are all zero; enter nutrient amounts to log or use in recipes."

    private fun msgDraftNoRows(): String =
        "Food has no nutrients and cannot be logged or used in recipes."

    private fun msgDraftBlankAmounts(): String =
        "Food nutrient amounts are blank; enter nutrient amounts to log or use in recipes."
}

/**
 * =============================================================================
 * FUTURE-YOU NOTE (2026-02-27)
 * =============================================================================
 *
 * Rationale / why this file exists
 *
 * This use case is intentionally the *single source of truth* for food completeness:
 *
 * - FoodsList must not invent its own "fix message" rules.
 * - FoodEditor must not duplicate or partially disagree with logging rules.
 * - CreateLogEntry must not silently guess density or bypass snapshot gates.
 *
 * If you need a new rule, add it HERE and wire all call sites to consume the same result.
 *
 * Locked-in safety rule
 *
 * - Do not add grams↔mL conversion here.
 * - Only allow grams/mL bridging via explicit fields:
 *     - gramsPerServingUnit
 *     - mlPerServingUnit
 *   OR deterministic volume units (ServingUnit.asMl).
 */