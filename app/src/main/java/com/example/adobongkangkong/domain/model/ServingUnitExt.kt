package com.example.adobongkangkong.domain.model

import kotlin.math.abs

/** True if this unit is fundamentally volume-like (convertible to mL). */
fun ServingUnit.isVolumeUnit(): Boolean = this.asMl != null

/** True if this unit is fundamentally mass-like (convertible to grams). */
fun ServingUnit.isMassUnit(): Boolean = this.asG != null

/**
 * Units where "1 serving" implies volume-ish or container-ish, and must be backed by gramsPerServingUnit
 * to be safely converted to weight-based nutrition (unless volume-grounded).
 *
 * IMPORTANT:
 * - Volume units no longer imply "needs grams" by default.
 * - Only FLEXIBLE units require explicit grounding.
 */
fun ServingUnit.requiresGramsPerServing(): Boolean {
    return this.requiresExplicitGrounding()
}

/** Convert a volume amount in this unit to milliliters, or null if not a volume unit. */
fun ServingUnit.toMilliliters(amount: Double): Double? = this.asMl?.let { amount * it }

/** Convert a milliliter amount into this unit, or null if not a volume unit. */
fun ServingUnit.fromMilliliters(ml: Double): Double? = this.asMl?.let { per ->
    if (per == 0.0) null else ml / per
}

/** Convert a mass amount in this unit to grams, or null if not a mass unit. */
fun ServingUnit.toGrams(amount: Double): Double? = this.asG?.let { amount * it }

/** Convert grams into this unit, or null if not a mass unit. */
fun ServingUnit.fromGrams(g: Double): Double? = this.asG?.let { per ->
    if (per == 0.0) null else g / per
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
 * Returns null if the serving unit is not volume-based.
 */
fun deriveDensityGramsPerMl(
    gramsPerServingUnit: Double,
    servingUnit: ServingUnit
): Double? {
    val ml = servingUnit.toMilliliters(1.0) ?: return null
    if (ml == 0.0) return null
    return gramsPerServingUnit / ml
}

/**
 * Convert a volume amount to grams using a known density (g/mL).
 */
fun volumeToGrams(
    amount: Double,
    unit: ServingUnit,
    densityGPerMl: Double
): Double? {
    val ml = unit.toMilliliters(amount) ?: return null
    return ml * densityGPerMl
}

/** Convert grams to a volume unit using a known density (g/mL). */
fun gramsToVolume(
    grams: Double,
    unit: ServingUnit,
    densityGPerMl: Double
): Double? {
    if (densityGPerMl == 0.0) return null
    val ml = grams / densityGPerMl
    return unit.fromMilliliters(ml)
}

/** Back-compat helper: throws for non-mass units (matches your old behavior). */
fun ServingUnit.gPerUnit(): Double =
    this.asG ?: throw IllegalArgumentException("ServingUnit $this is not a mass unit")

/** Back-compat helper: nullable. */
fun ServingUnit.gPerUnitOrNull(): Double? = this.asG

/** Helper for comparing values with a tolerance (useful for tests). */
fun nearlyEquals(a: Double, b: Double, eps: Double = 1e-9): Boolean =
    abs(a - b) <= eps

/**
 * Map USDA servingSizeUnit strings to our canonical ServingUnit.
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

        // Household
        "TSP" -> ServingUnit.TSP
        "TBSP" -> ServingUnit.TBSP
        "CUP" -> ServingUnit.CUP
        "QT", "QUART" -> ServingUnit.QUART
        "FLOZ", "FL OZ" -> ServingUnit.FL_OZ_US

        else -> null
    }
}