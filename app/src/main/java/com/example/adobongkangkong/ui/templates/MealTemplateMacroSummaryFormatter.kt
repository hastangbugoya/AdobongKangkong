package com.example.adobongkangkong.ui.templates

import com.example.adobongkangkong.domain.model.MacroTotals
import kotlin.math.roundToInt

/**
 * Template macro summary formatting helpers for list-like UI.
 *
 * ## For developers
 * - This file is the single presentation formatter for compact meal-template macro strings.
 * - It intentionally formats in the same style used by the template picker so the picker and
 *   template list never drift visually.
 * - Keep this focused on display formatting only; do not move macro aggregation logic here.
 * - Rounding policy is deliberately compact and scan-friendly:
 *   - kcal -> nearest whole number
 *   - protein/carbs/fat -> nearest whole gram
 *
 * ## Scope
 * This formatter is safe to reuse from:
 * - template picker rows
 * - template list rows
 * - future compact template chips / bottom sheets
 */
fun MacroTotals.toMealTemplateMacroSummaryLine(): String {
    val kcal = caloriesKcal.roundToInt()
    val protein = proteinG.roundToInt()
    val carbs = carbsG.roundToInt()
    val fat = fatG.roundToInt()
    return "$kcal kcal • P $protein • C $carbs • F $fat"
}

/**
 * Bottom KDoc for future AI assistant.
 *
 * This file exists to prevent format drift between the meal template picker and the meal template
 * list screen. If one screen needs a new compact macro text format, update this shared formatter
 * instead of duplicating formatting logic in multiple composables or ViewModels.
 *
 * Important boundary:
 * - aggregation/computation belongs upstream (use cases / ViewModels)
 * - this file only converts a [MacroTotals] value into stable UI text
 */
