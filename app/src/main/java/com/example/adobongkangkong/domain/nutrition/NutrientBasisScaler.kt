package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.isMassUnit
import com.example.adobongkangkong.domain.model.toGrams
import kotlin.math.abs

/**
 * Canonical nutrient scaling utilities between storage basis and UI “per-serving” display.
 *
 * ## Purpose
 * Provide the single authoritative conversion layer for translating nutrient amounts between:
 * - **Canonical storage** (typically PER_100G / PER_100ML), and
 * - **UI display** (per serving as defined by `servingSize * (g/ml)PerServingUnit`).
 *
 * This object exists so the rest of the app never “re-implements” scaling math ad hoc.
 *
 * ## Rationale (why this exists)
 * The app deliberately separates concerns:
 * - **Storage basis** is optimized for consistent scaling and aggregation (per 100g / per 100ml).
 * - **UI** is user-facing and expects nutrient amounts “per serving” (the unit/size the user recognizes).
 *
 * Without a centralized scaler:
 * - different screens will perform slightly different conversions,
 * - rounding and basis mistakes will creep in,
 * - regressions will silently distort macros and totals.
 *
 * This file is therefore a core correctness boundary:
 * - If canonical storage changes, UI conversions must be updated here.
 * - If UI serving semantics change, conversions must be updated here.
 *
 * ## Canonical model assumptions
 * - **Mass basis**:
 *   - Canonical storage is typically PER_100G whenever the food can be grounded in grams
 *     (i.e., gramsPerServingUnit is known and valid).
 *   - UI display is per serving where:
 *       gramsPerServing = servingSize * gramsPerServingUnit
 *
 * - **Volume basis**:
 *   - Canonical storage is PER_100ML when the food can be grounded in milliliters
 *     (mlPerServingUnit known and valid).
 *   - UI display is per serving where:
 *       mlPerServing = servingSize * mlPerServingUnit
 *
 * - **No density guessing**:
 *   - This scaler must never convert grams ↔ milliliters without an explicit density field.
 *
 * ## Behavior
 * The scaler provides two symmetric conversions for each basis family:
 *
 * Mass:
 * - canonicalToDisplayPerServing(): PER_100G → per-serving UI amount
 * - displayPerServingToCanonical(): per-serving UI amount → PER_100G storage
 *
 * Volume:
 * - canonicalToDisplayPerServingVolume(): PER_100ML → per-serving UI amount
 * - displayPerServingToCanonicalVolume(): per-serving UI amount → PER_100ML storage
 *
 * It also provides almostEqual() for deterministic unit test comparisons.
 *
 * ## Parameters
 * Mass conversions require:
 * - `storedAmount` or `uiPerServingAmount`
 * - basis ([BasisType])
 * - `servingSize`
 * - `gramsPerServingUnit` (required to scale when basis is PER_100G)
 *
 * Volume conversions require:
 * - `storedAmount` or `uiPerServingAmount`
 * - basis ([BasisType])
 * - `servingSize`
 * - `mlPerServingUnit` (required to scale when basis is PER_100ML)
 *
 * ## Return
 * Each conversion returns [Result]:
 * - `amount`: the converted amount (or original amount if scaling is not possible / not applicable)
 * - `didScale`: true if scaling math was actually applied
 *
 * ## Process walkthrough (math)
 * PER_100G:
 * - Storage is “X per 100g”.
 * - UI wants “X per (serving grams)”.
 * - So:
 *     display = stored * (gramsPerServing / 100)
 *     stored  = display * (100 / gramsPerServing)
 *
 * PER_100ML:
 * - Storage is “X per 100ml”.
 * - UI wants “X per (serving ml)”.
 * - So:
 *     display = stored * (mlPerServing / 100)
 *     stored  = display * (100 / mlPerServing)
 *
 * USDA_REPORTED_SERVING:
 * - Already per serving snapshot; do not rescale.
 *
 * Regression example (must remain correct):
 * - Bok Choy = 59 kcal per 1 lb (453.59237g).
 * - Stored PER_100G = 59 * (100 / 453.59237) = 13.0072734689...
 * - Display per 1 lb must scale back:
 *     13.00727... * (453.59237 / 100) = 59.
 *
 * ## Edge cases
 * - Missing or invalid `servingSize` (<= 0) → scaling cannot be performed.
 * - Missing or invalid grams/ml bridge (null or <= 0) → scaling cannot be performed.
 * - Basis mismatch (e.g., ask for PER_100G scaling without grams bridge) → returns original amount with didScale=false.
 *
 * ## Pitfalls / gotchas
 * - **This is the only approved scaling implementation.** Do not reproduce scaling elsewhere.
 * - **Do not add grams ↔ ml conversions here.** Density is unknown and must not be guessed.
 * - `didScale=false` is not an error; it means the caller asked for scaling but the required grounding data was missing.
 * - Be careful with “USDA_REPORTED_SERVING”: it is treated as already-per-serving and never rescaled.
 *
 * ## Architectural rules
 * - Pure deterministic functions.
 * - No repository access.
 * - No Android/UI dependencies.
 * - Must remain safe under historical snapshot model (no joins, no mutation).
 *
 * ## Testing requirement
 * This file has a unit test. Any change here MUST keep all tests passing.
 * Treat test failure as a correctness regression, not a “minor rounding difference”.
 */
object NutrientBasisScaler {

    data class Result(
        val amount: Double,
        val didScale: Boolean
    )

    /**
     * Converts a stored nutrient amount (in [storedBasis]) into a per-serving display amount.
     *
     * If scaling cannot be performed (missing grams info), returns the original amount with didScale=false.
     */
    fun canonicalToDisplayPerServing(
        storedAmount: Double,
        storedBasis: BasisType,
        servingSize: Double,
        gramsPerServingUnit: Double?
    ): Result {
        return when (storedBasis) {
            BasisType.PER_100G -> {
                val grams = gramsPerServing(servingSize, gramsPerServingUnit)
                    ?: return Result(storedAmount, false)
                Result(storedAmount * grams / 100.0, true)
            }

            // We do not attempt ml<->g conversions here (density unknown).
            BasisType.PER_100ML -> Result(storedAmount, false)

            // Already a per-serving snapshot, nothing to do.
            BasisType.USDA_REPORTED_SERVING -> Result(storedAmount, false)
        }
    }

    /**
     * Converts a per-serving UI amount into canonical storage amount for the given [canonicalBasis].
     *
     * If scaling cannot be performed (missing grams info), returns the original amount with didScale=false.
     */
    fun displayPerServingToCanonical(
        uiPerServingAmount: Double,
        canonicalBasis: BasisType,
        servingSize: Double,
        gramsPerServingUnit: Double?
    ): Result {
        return when (canonicalBasis) {
            BasisType.PER_100G -> {
                val grams = gramsPerServing(servingSize, gramsPerServingUnit)
                    ?: return Result(uiPerServingAmount, false)
                Result(uiPerServingAmount * 100.0 / grams, true)
            }

            BasisType.PER_100ML -> Result(uiPerServingAmount, false)
            BasisType.USDA_REPORTED_SERVING -> Result(uiPerServingAmount, false)
        }
    }

    /**
     * Convenience: stable "almost equals" helper for tests or sanity checks.
     */
    fun almostEqual(a: Double, b: Double, eps: Double = 1e-9): Boolean = abs(a - b) <= eps

    private fun gramsPerServing(
        servingSize: Double,
        gramsPerServingUnit: Double?,
        servingUnit: ServingUnit? = null
    ): Double? {
        if (servingSize <= 0.0) return null

        val gPerUnit: Double? =
            when {
                // 🔥 NEW: handle deterministic mass units
                servingUnit?.isMassUnit() == true -> servingUnit.toGrams(1.0)

                else -> gramsPerServingUnit
            }

        val g = gPerUnit ?: return null
        if (g <= 0.0) return null

        return servingSize * g
    }

    // DO NOT TOUCH THIS (future-you note):
    // Volume scaling is independent from mass scaling.
    // PER_100ML assumes pure volume math (ml only).
    fun canonicalToDisplayPerServingVolume(
        storedAmount: Double,
        storedBasis: BasisType,
        servingSize: Double,
        mlPerServingUnit: Double?
    ): Result {
        return when (storedBasis) {
            BasisType.PER_100ML -> {
                val ml = mlPerServing(servingSize, mlPerServingUnit)
                    ?: return Result(storedAmount, false)

                Result(storedAmount * ml / 100.0, true)
            }
            else -> Result(storedAmount, false)
        }
    }

    fun displayPerServingToCanonicalVolume(
        uiPerServingAmount: Double,
        canonicalBasis: BasisType,
        servingSize: Double,
        mlPerServingUnit: Double?
    ): Result {
        return when (canonicalBasis) {
            BasisType.PER_100ML -> {
                val ml = mlPerServing(servingSize, mlPerServingUnit)
                    ?: return Result(uiPerServingAmount, false)
                Result(uiPerServingAmount * 100.0 / ml, true)
            }
            else -> Result(uiPerServingAmount, false)
        }
    }

    private fun mlPerServing(servingSize: Double, mlPerServingUnit: Double?): Double? {
        if (servingSize <= 0.0) return null
        val ml = mlPerServingUnit ?: return null
        if (ml <= 0.0) return null
        return servingSize * ml
    }
}

/**
 * FUTURE-AI / MAINTENANCE KDoc (Do not remove)
 *
 * ## Invariants (must not change)
 * - This is the single source of truth for nutrient scaling between:
 *   - PER_100G ⇄ UI per-serving (grams-backed)
 *   - PER_100ML ⇄ UI per-serving (ml-backed)
 * - Must never perform grams↔mL conversion (no density guessing).
 * - USDA_REPORTED_SERVING must remain “already per serving” (no scaling).
 * - When required grounding is missing, return original amount with didScale=false (do not crash).
 * - Unit tests for this file must ALWAYS pass; treat failures as correctness regressions.
 *
 * ## Do not refactor notes
 * - Do not duplicate scaling logic in UI, repositories, or other use cases.
 * - Do not “simplify” by removing didScale; callers rely on the signal for UX/edit validation.
 * - Avoid changing epsilon defaults without updating tests and validating real-world regressions.
 *
 * ## Architectural boundaries
 * - Pure domain utility (no Android, no DB, no I/O).
 * - Used by:
 *   - Food editor display mapping,
 *   - Save-time canonicalization,
 *   - Snapshot creation,
 *   - Recipe nutrition computations.
 *
 * ## Performance considerations
 * - O(1) computations; safe in hot paths (lists, recompositions).
 *
 * ## Future improvements (only if needed; preserve invariants)
 * - Consider exposing explicit helper APIs:
 *   - gramsPerServing(servingSize, gpsu) and mlPerServing(servingSize, mpsu) publicly,
 *     if multiple callers need the validated computed serving mass/volume.
 * - Consider standardized rounding policy (UI only) that stays outside this scaler to keep math exact.
 * - If a real density model is introduced:
 *   - Add a separate, explicitly named conversion path (e.g., `mlToGramsWithDensity`)
 *   - Keep current methods unchanged unless a migration plan exists and tests are updated accordingly.
 */