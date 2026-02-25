package com.example.adobongkangkong.domain.usecase

import javax.inject.Inject

/**
 * NormalizeNutritionToPer100gUseCase
 *
 * ## Purpose
 * Normalizes a set of nutrient amounts to a **PER_100G** basis.
 *
 * ## Rationale
 * Nutrition data can enter the app in different bases (commonly per 100g or per serving). For
 * consistent storage, scaling, comparisons, and downstream math (recipes, logging, totals), we
 * prefer a single canonical basis.
 *
 * This use case supports the agreed strategy:
 * - **Normalize on write** (new/updated data should be stored in a consistent basis),
 * - **Tolerate legacy on read** (older rows may still exist in non-canonical bases and must not
 *   crash the app).
 *
 * ## Supported scenarios
 * - **USDA / label data already per 100g**:
 *   - Pass basis = PER_100G → returns unchanged nutrient map.
 * - **User-entered nutrients per serving**:
 *   - Pass basis = PER_SERVING and provide gramsForBasis (grams per serving) → scales nutrients to
 *     per 100g.
 * - **Incomplete serving metadata** (unknown grams per serving):
 *   - PER_SERVING without valid gramsForBasis → returns [Result.CannotNormalize] with a reason.
 *
 * ## Mathematical rule
 * When converting PER_SERVING → PER_100G:
 * - factor = 100 / gramsForBasis
 * - nutrientsPer100g[n] = nutrientsPerServing[n] * factor (for non-null values)
 *
 * Null nutrient values are preserved as null (unknown remains unknown).
 *
 * ## Inputs
 * @param input Input bundle containing:
 * - [Input.basis] Current basis of the provided nutrient values
 * - [Input.gramsForBasis] Grams corresponding to the current basis (required for PER_SERVING)
 * - [Input.nutrients] Map of nutrientCode → amount (nullable amounts allowed)
 *
 * ## Outputs
 * - On success: [Result.Ok] with [Output.basis] = PER_100G and [Output.nutrientsPer100g]
 * - On failure: [Result.CannotNormalize] with a human-readable reason
 *
 * ## Tips / warnings
 * - **Do not guess gramsForBasis**. If grams-for-serving is unknown, return CannotNormalize and let
 *   UI request the missing input. Guessing produces silently wrong numbers.
 * - Keep in mind this use case only handles PER_100G and PER_SERVING. Volume bases (PER_100ML) are
 *   not supported here and should be handled in separate logic (typically requiring density).
 * - This is a pure function: it does not read or write DB, and it should remain deterministic.
 */
class NormalizeNutritionToPer100gUseCase @Inject constructor() {

    /**
     * Input payload for normalization.
     *
     * @property basis Declares the basis of [nutrients] (PER_100G or PER_SERVING).
     * @property gramsForBasis Grams corresponding to the basis:
     * - Required when basis == PER_SERVING (i.e., grams per serving).
     * - Ignored when basis == PER_100G.
     * @property nutrients Map of nutrientCode → amount. Amounts are nullable to support partially
     * known nutrition data.
     */
    data class Input(
        val basis: Basis,
        /** Grams for the basis. Required when basis == PER_SERVING. */
        val gramsForBasis: Double?,
        val nutrients: Map<String, Double?> // nutrientCode -> amount
    )

    /**
     * Output payload on successful normalization.
     *
     * @property basis Always PER_100G for outputs from this use case.
     * @property nutrientsPer100g Normalized nutrient map (nullable amounts preserved).
     */
    data class Output(
        val basis: Basis = Basis.PER_100G,
        val nutrientsPer100g: Map<String, Double?>
    )

    /**
     * Supported nutrition bases for this normalization step.
     */
    enum class Basis { PER_100G, PER_SERVING }

    /**
     * Result of attempting to normalize nutrition to PER_100G.
     */
    sealed class Result {
        /** Normalization succeeded. */
        data class Ok(val output: Output) : Result()

        /** Normalization could not be performed due to missing/invalid inputs. */
        data class CannotNormalize(val reason: String) : Result()
    }

    /**
     * Normalizes nutrient values to PER_100G.
     *
     * - If [Input.basis] == PER_100G → returns values unchanged.
     * - If [Input.basis] == PER_SERVING → requires [Input.gramsForBasis] > 0 and scales values
     *   using factor = 100 / gramsForBasis.
     *
     * @param input Input nutrients and basis metadata.
     * @return [Result.Ok] with normalized values, or [Result.CannotNormalize] if required metadata
     * is missing/invalid.
     */
    fun invoke(input: Input): Result {
        return when (input.basis) {
            Basis.PER_100G -> {
                Result.Ok(
                    Output(
                        nutrientsPer100g = input.nutrients
                    )
                )
            }

            Basis.PER_SERVING -> {
                val grams = input.gramsForBasis
                if (grams == null || grams <= 0.0) {
                    return Result.CannotNormalize("Missing/invalid gramsForBasis for PER_SERVING")
                }
                val factor = 100.0 / grams
                val out = input.nutrients.mapValues { (_, v) ->
                    v?.let { it * factor }
                }
                Result.Ok(Output(nutrientsPer100g = out))
            }
        }
    }
}

/**
 * FUTURE AI ASSISTANT NOTES
 *
 * - This file follows the “two KDocs” standard:
 *   - Top KDoc: dev-facing purpose/rationale/scenarios/tips/warnings.
 *   - Bottom KDoc: constraints/invariants for automated edits.
 *
 * - Keep this use case PURE:
 *   - No database access
 *   - No network access
 *   - No logging side effects
 *   - No dependency on Android/Room
 *
 * - Do NOT add heuristics that guess gramsForBasis.
 *   If gramsForBasis is missing/invalid, returning CannotNormalize is the correct behavior.
 *
 * - This use case intentionally supports ONLY:
 *   - PER_100G
 *   - PER_SERVING
 *   Volume (PER_100ML) or other density-based conversions belong elsewhere.
 *
 * - Nullable nutrients must remain nullable.
 *   Do not convert nulls to 0.0, because null represents “unknown”, not “zero”.
 *
 * - If rounding is needed for display, do it at the UI layer; keep computation full precision here.
 */