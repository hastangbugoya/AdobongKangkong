package com.example.adobongkangkong.ui.format

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Formats a Double for UI display:
 *
 * - Removes trailing zeros
 * - Avoids scientific notation
 * - Keeps sensible decimal precision
 *
 * Examples:
 *  1.0     -> "1"
 *  1.50    -> "1.5"
 *  1.23456 -> "1.23"
 *  12.000  -> "12"
 *  0.33333 -> "0.33"
 *
 * Intended for:
 *  - servings
 *  - grams
 *  - nutrient values
 *  - package multipliers
 */
fun Double.ui(
    decimals: Int = 2
): String {
    if (!this.isFinite()) return ""

    return BigDecimal(this)
        .setScale(decimals, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}

/**
 * Nullable convenience for UI binding.
 */
fun Double?.ui(
    decimals: Int = 2
): String = this?.ui(decimals) ?: ""

