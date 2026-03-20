package com.example.adobongkangkong.ui.common

import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.isMassUnit
import java.util.Locale

/**
 * QuantityDisplayFormatter
 *
 * Centralized UI formatter for displaying food/ingredient quantities.
 *
 * RULES:
 * 1. If unit is MASS (g, kg, oz, lb):
 *    - Show ONLY total grams
 *    - Do NOT show servings (avoids leaking 100g abstraction)
 *
 * 2. If unit is NON-MASS (cup, tbsp, piece, etc.):
 *    - Show "servings + unit • grams"
 *
 * 3. All grams displayed are TOTAL grams (already resolved upstream)
 *
 * This must remain PURE UI LOGIC.
 * - No conversions here
 * - No domain decisions here
 *
 * Upstream must provide:
 * - servings (Double)
 * - unit (ServingUnit)
 * - totalGrams (Double)
 */
object QuantityDisplayFormatter {

    fun format(
        servings: Double,
        unit: ServingUnit,
        totalGrams: Double
    ): String {
        return if (unit.isMassUnit()) {
            formatGrams(totalGrams)
        } else {
            "${formatServings(servings)} ${unit.display} • ${formatGrams(totalGrams)}"
        }
    }

    // -----------------------
    // Helpers
    // -----------------------

    private fun formatGrams(grams: Double): String {
        // Avoid noisy decimals for whole numbers
        return if (grams % 1.0 == 0.0) {
            "${grams.toInt()} g"
        } else {
            "${round(grams)} g"
        }
    }

    private fun formatServings(servings: Double): String {
        return if (servings % 1.0 == 0.0) {
            servings.toInt().toString()
        } else {
            round(servings)
        }
    }

    private fun round(value: Double): String {
        return String.format(Locale.US, "%.2f", value)
            .trimEnd('0')
            .trimEnd('.')
    }
}

/**
 * ------------------------------------------------------------------------
 * FUTURE-YOU NOTES (AI ASSISTANT CONTEXT)
 * ------------------------------------------------------------------------
 * Timestamp: 2026-03-19
 *
 * WHY THIS EXISTS:
 * - Prevent UI from leaking "100g per serving" abstraction
 * - Avoid confusing displays like: "4.5 g • 450 g"
 *
 * INVARIANT:
 * - Mass-based units MUST collapse to grams-only display
 *
 * DO NOT:
 * - Add conversions here (belongs in domain)
 * - Infer grams from unit (must be passed in)
 *
 * SAFE EXTENSIONS:
 * - Add localized unit labels
 * - Add optional secondary text (e.g., "entered as ...")
 *
 * RISK:
 * - If upstream passes incorrect grams, UI will still look valid
 *   → Always verify upstream math in ViewModel/use cases
 */