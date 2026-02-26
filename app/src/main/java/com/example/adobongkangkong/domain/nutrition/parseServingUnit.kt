package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.domain.model.ServingUnit

/**
 * Parses a free-form serving unit label into a canonical [ServingUnit].
 *
 * ## Purpose
 * Provides a small normalization layer for converting user-entered or imported text
 * (e.g., "grams", "tbsp", "cups") into the domain enum [ServingUnit].
 *
 * ## Rationale (why this exists)
 * External inputs (USDA import text, barcode label text, manual entry) frequently provide
 * serving units as loosely formatted strings. This helper consolidates that variability into
 * a single parsing rule-set so downstream nutrition math can rely on a stable enum.
 *
 * NOTE: This function is currently believed to be refactored out of active use in favor of
 * other parsing/mapping logic. It remains here only for legacy compatibility and may be
 * deleted once all call sites are confirmed removed.
 *
 * ## Behavior
 * - Trims and lowercases [raw].
 * - Maps common synonyms/abbreviations to a [ServingUnit] constant.
 * - Returns [ServingUnit.SERVING] as a conservative fallback when unknown.
 *
 * ## Parameters
 * @param raw Raw unit string (user-entered or imported), e.g. "g", "grams", "tbsp".
 *
 * ## Return
 * @return A best-effort [ServingUnit] classification. Unknown values return [ServingUnit.SERVING].
 *
 * ## Edge cases
 * - Blank or whitespace-only input returns [ServingUnit.SERVING].
 * - Unrecognized unit text (including pluralization not listed) returns [ServingUnit.SERVING].
 *
 * ## Pitfalls / gotchas
 * - The fallback to [ServingUnit.SERVING] is intentionally “safe” but can hide upstream parsing issues.
 *   If precise unit parsing is required (e.g., importer correctness), prefer a stricter parser that
 *   can surface unknown units as warnings instead of silently falling back.
 * - This parser does not attempt fuzzy matching beyond explicit string cases.
 *
 * ## Architectural rules
 * - Domain nutrition math should operate on [ServingUnit] (enum), not raw strings.
 * - If this function is removed, ensure importer/user-entry pipelines still normalize to [ServingUnit]
 *   and still preserve the “no density guessing” rule for mass vs volume conversions.
 */
fun parseServingUnit(raw: String): ServingUnit {
    val s = raw.trim().lowercase()
    return when (s) {
        "g", "gram", "grams" -> ServingUnit.G
        "ml", "milliliter", "milliliters" -> ServingUnit.ML
        "tbsp", "tablespoon", "tablespoons" -> ServingUnit.TBSP
        "tsp", "teaspoon", "teaspoons" -> ServingUnit.TSP
        "cup", "cups" -> ServingUnit.CUP
        "oz", "ounce", "ounces" -> ServingUnit.OZ
        "lb", "pound", "pounds" -> ServingUnit.LB
        "qt", "quart" -> ServingUnit.QUART
        "piece", "pcs", "pc" -> ServingUnit.PIECE
        "slice", "slices" -> ServingUnit.SLICE
        "pack", "packet", "pouch" -> ServingUnit.PACK
        "bottle", "bottles" -> ServingUnit.BOTTLE
        "jar", "jars" -> ServingUnit.JAR
        "serving", "servings" -> ServingUnit.SERVING

        else -> ServingUnit.SERVING // safe fallback
    }
}

/**
 * =============================================================================
 * FOR FUTURE AI ASSISTANT — parseServingUnit invariants and deletion notes
 * =============================================================================
 *
 * Status
 * - Likely legacy / no longer used after importer and editor refactors.
 * - Keep only until all call sites are confirmed removed.
 *
 * Invariants (if it remains)
 * - Must be deterministic (no locale-dependent behavior beyond lowercase()).
 * - Must never throw for arbitrary input.
 * - Must preserve conservative fallback (SERVING) unless callers are updated to handle failures.
 *
 * Do not refactor
 * - Do not add “smart” guessing that changes meaning (e.g., interpreting "fl oz" as OZ or ML).
 * - Do not introduce density-based conversions here.
 *
 * Deletion plan
 * - Before deleting:
 *   1) Confirm no USDA import path calls this.
 *   2) Confirm no manual entry/editor path calls this.
 *   3) Confirm tests/import logs do not depend on these exact mappings.
 *
 * Future improvement (if revived)
 * - Prefer returning a sealed result: Parsed(unit) | Unknown(raw) so import pipelines can warn.
 */