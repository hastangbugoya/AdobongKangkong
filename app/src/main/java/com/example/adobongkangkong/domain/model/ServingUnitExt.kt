package com.example.adobongkangkong.domain.model

import kotlin.math.abs

/**
 * Helpers for unit classification + deterministic conversions.
 *
 * Canonical bases:
 * - Volume: milliliters (mL)
 * - Mass: grams (g)
 *
 * Design notes:
 * - CUP_US is the nutrition-label standard (240 mL). We omit the US customary cup.
 * - The US "nutrition" ladder is internally consistent: 1 CUP_US = 8 FL_OZ_US = 16 TBSP_US = 48 TSP_US.
 * - Imperial units use the exact standard values (pt/qt/gal based on Imperial fl oz).
 *
 * These are math-only utilities. Food-specific density (mL↔g) should be derived separately
 * from (gramsPerServingUnit / mlPerServing) when the serving unit is volume-based.
 */

/** True if this unit is fundamentally volume-like (convertible to mL). */
fun ServingUnit.isVolumeUnit(): Boolean = when (this) {
    ServingUnit.ML,
    ServingUnit.L,

    ServingUnit.TSP_US,
    ServingUnit.TBSP_US,
    ServingUnit.FL_OZ_US,
    ServingUnit.CUP_US,
    ServingUnit.PINT_US,
    ServingUnit.QUART_US,
    ServingUnit.GALLON_US,

    ServingUnit.CUP_METRIC,
    ServingUnit.CUP_JP,

    ServingUnit.FL_OZ_IMP,
    ServingUnit.PINT_IMP,
    ServingUnit.QUART_IMP,
    ServingUnit.GALLON_IMP,

        // Legacy aliases (treat as US for compatibility)
    ServingUnit.TSP,
    ServingUnit.TBSP,
    ServingUnit.CUP,
    ServingUnit.QUART -> true

    else -> false
}

/** True if this unit is fundamentally mass-like (convertible to grams). */
fun ServingUnit.isMassUnit(): Boolean = when (this) {
    ServingUnit.MG,
    ServingUnit.G,
    ServingUnit.KG,
    ServingUnit.OZ,
    ServingUnit.LB -> true

    else -> false
}

/**
 * Units where "1 serving" implies volume-ish or container-ish, and must be backed by gramsPerServingUnit
 * to be safely converted to weight-based nutrition.
 */
fun ServingUnit.requiresGramsPerServing(): Boolean = when (this) {

    // Volume-based units (need grams to be meaningful)
    in volumeUnits() -> true

    // Container-ish / subjective count-ish (also need grams)
    ServingUnit.SCOOP,
    ServingUnit.BOTTLE,
    ServingUnit.JAR,
    ServingUnit.CAN,
    ServingUnit.BOX,
    ServingUnit.BAG,
    ServingUnit.PACKET,
    ServingUnit.PACK,
    ServingUnit.SERVING,
    ServingUnit.OTHER,
    ServingUnit.PIECE,
    ServingUnit.SLICE,
    ServingUnit.BATCH,
    ServingUnit.BUNCH -> true

    // Mass units are already grounded
    ServingUnit.MG,
    ServingUnit.G,
    ServingUnit.KG,
    ServingUnit.OZ,
    ServingUnit.LB -> false
    else -> false
}


private fun volumeUnits(): Set<ServingUnit> = setOf(
    ServingUnit.ML,
    ServingUnit.L,

    ServingUnit.TSP_US,
    ServingUnit.TBSP_US,
    ServingUnit.FL_OZ_US,
    ServingUnit.CUP_US,
    ServingUnit.PINT_US,
    ServingUnit.QUART_US,
    ServingUnit.GALLON_US,

    ServingUnit.CUP_METRIC,
    ServingUnit.CUP_JP,

    ServingUnit.RCCUP,

    ServingUnit.FL_OZ_IMP,
    ServingUnit.PINT_IMP,
    ServingUnit.QUART_IMP,
    ServingUnit.GALLON_IMP,

    // Legacy aliases
    ServingUnit.TSP,
    ServingUnit.TBSP,
    ServingUnit.CUP,
    ServingUnit.QUART
)

/** Convert a volume amount in this unit to milliliters, or null if not a volume unit. */
fun ServingUnit.toMilliliters(amount: Double): Double? {
    if (!isVolumeUnit()) return null
    return amount * mlPerUnit()
}

/** Convert a milliliter amount into this unit, or null if not a volume unit. */
fun ServingUnit.fromMilliliters(ml: Double): Double? {
    if (!isVolumeUnit()) return null
    val per = mlPerUnit()
    if (per == 0.0) return null
    return ml / per
}

/** Convert a mass amount in this unit to grams, or null if not a mass unit. */
fun ServingUnit.toGrams(amount: Double): Double? {
    if (!isMassUnit()) return null
    return amount * gPerUnit()
}

/** Convert grams into this unit, or null if not a mass unit. */
fun ServingUnit.fromGrams(g: Double): Double? {
    if (!isMassUnit()) return null
    val per = gPerUnit()
    if (per == 0.0) return null
    return g / per
}

/** Convert a volume amount between two volume units. Returns null if either is non-volume. */
fun convertVolume(amount: Double, from: ServingUnit, to: ServingUnit): Double? {
    val ml = from.toMilliliters(amount) ?: return null
    return to.fromMilliliters(ml)
}

/** Convert a mass amount between two mass units. Returns null if either is non-mass. */
fun convertMass(amount: Double, from: ServingUnit, to: ServingUnit): Double? {
    val g = from.toGrams(amount) ?: return null
    return to.fromGrams(g)
}

/**
 * Derive food-specific density (g/mL) from a serving definition.
 *
 * Returns null if the serving unit is not volume-based or if mlPerServing would be zero.
 */
fun deriveDensityGramsPerMl(gramsPerServingUnit: Double, servingUnit: ServingUnit): Double? {
    val ml = servingUnit.toMilliliters(1.0) ?: return null
    if (ml == 0.0) return null
    return gramsPerServingUnit / ml
}

/**
 * Convert a volume amount to grams using a known density (g/mL).
 * This is the “bridge” that makes cup/tbsp entry compatible with gram-based nutrition math.
 */
fun volumeToGrams(amount: Double, unit: ServingUnit, densityGPerMl: Double): Double? {
    val ml = unit.toMilliliters(amount) ?: return null
    return ml * densityGPerMl
}

/** Convert grams to a volume unit using a known density (g/mL). */
fun gramsToVolume(grams: Double, unit: ServingUnit, densityGPerMl: Double): Double? {
    if (densityGPerMl == 0.0) return null
    val ml = grams / densityGPerMl
    return unit.fromMilliliters(ml)
}

// --------------------
// Canonical constants
// --------------------

private fun ServingUnit.mlPerUnit(): Double = when (this) {
    // Metric
    ServingUnit.ML -> 1.0
    ServingUnit.L -> 1000.0

    // US nutrition-standard set (internally consistent)
    ServingUnit.CUP_US -> 240.0
    ServingUnit.TBSP_US -> 240.0 / 16.0          // 15.0
    ServingUnit.TSP_US -> (240.0 / 16.0) / 3.0    // 5.0
    ServingUnit.FL_OZ_US -> 240.0 / 8.0           // 30.0
    ServingUnit.PINT_US -> 240.0 * 2.0            // 480.0
    ServingUnit.QUART_US -> 240.0 * 4.0           // 960.0
    ServingUnit.GALLON_US -> 240.0 * 16.0         // 3840.0

    // Cup variants
    ServingUnit.CUP_METRIC -> 250.0
    ServingUnit.CUP_JP -> 200.0

    ServingUnit.RCCUP -> 180.0 // rice cooker cup (international)

    // Imperial (exact)
    ServingUnit.FL_OZ_IMP -> 28.4130625
    ServingUnit.PINT_IMP -> 568.26125
    ServingUnit.QUART_IMP -> 1136.5225
    ServingUnit.GALLON_IMP -> 4546.09

    // Legacy aliases: treat as US for compatibility
    ServingUnit.CUP -> ServingUnit.CUP_US.mlPerUnit()
    ServingUnit.TBSP -> ServingUnit.TBSP_US.mlPerUnit()
    ServingUnit.TSP -> ServingUnit.TSP_US.mlPerUnit()
    ServingUnit.QUART -> ServingUnit.QUART_US.mlPerUnit()

    else ->  throw IllegalArgumentException(
        "ServingUnit $this is not a volume unit"
    )
}

fun ServingUnit.gPerUnit(): Double = when (this) {
    ServingUnit.MG -> 0.001
    ServingUnit.G -> 1.0
    ServingUnit.KG -> 1000.0
    ServingUnit.OZ -> 28.349523125
    ServingUnit.LB -> 453.59237
    else -> throw IllegalArgumentException(
        "ServingUnit $this is not a mass unit"
    )
}

fun ServingUnit.gPerUnitOrNull(): Double? = when (this) {
    ServingUnit.G -> 1.0
    ServingUnit.KG -> 1000.0
    ServingUnit.MG -> 0.001
    ServingUnit.OZ -> 28.349523125
    ServingUnit.LB -> 453.59237
    else -> null
}

/**
 * Helper for comparing values with a tolerance (useful for tests).
 */
fun nearlyEquals(a: Double, b: Double, eps: Double = 1e-9): Boolean = abs(a - b) <= eps


/**
 * Map USDA servingSizeUnit strings to our canonical ServingUnit.
 *
 * Observed in USDA Branded search:
 * - "GRM" (grams)
 * - "MLT" (milliliters)
 * Also be tolerant of lowercase variants (e.g. "g") and common synonyms.
 *
 * Returns null if unknown/unhandled.
 */
fun ServingUnit.Companion.fromUsda(unit: String?): ServingUnit? {
    if (unit.isNullOrBlank()) return null

    return when (unit.trim().uppercase()) {
        // Mass
        "G", "GRM", "GRAM", "GRAMS" -> ServingUnit.G
        "MG", "MGM", "MILLIGRAM", "MILLIGRAMS" -> ServingUnit.MG
        "KG", "KGM", "KILOGRAM", "KILOGRAMS" -> ServingUnit.KG
        "OZ" -> ServingUnit.OZ
        "LB", "LBR" -> ServingUnit.LB

        // Volume
        "ML", "MLT", "MILLILITER", "MILLILITERS" -> ServingUnit.ML
        "L", "LTR", "LITER", "LITERS" -> ServingUnit.L

        // Sometimes seen / helpful if USDA ever sends them:
        "TSP" -> ServingUnit.TSP          // legacy alias in your enum
        "TBSP" -> ServingUnit.TBSP        // legacy alias in your enum
        "CUP" -> ServingUnit.CUP          // legacy alias in your enum
        "QT", "QUART" -> ServingUnit.QUART // legacy alias in your enum

        // If you ever decide to parse householdServingFullText into US units, you'd use:
        "FLOZ", "FL OZ" -> ServingUnit.FL_OZ_US
        // but USDA servingSizeUnit for Branded usually comes as GRM/MLT.

        else -> null
    }
}
