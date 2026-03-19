package com.example.adobongkangkong.domain.model

import kotlin.math.abs

/** True if this unit is fundamentally volume-like (convertible to mL). */
fun ServingUnit.isVolumeUnit(): Boolean = this.asMl != null

/** True if this unit is fundamentally mass-like (convertible to grams). */
fun ServingUnit.isMassUnit(): Boolean = this.asG != null

/**
 * Units where "1 serving" implies volume-ish or container-ish, and must be backed by gramsPerServingUnit
 * to be safely converted to weight-based nutrition (unless volume-grounded with mlPerServingUnit).
 */
fun ServingUnit.requiresGramsPerServing(): Boolean = when {
    // If it’s a deterministic volume unit, it’s “volume-ish” and often needs a grams bridge
    // UNLESS the caller is treating it as volume-grounded via mlPerServingUnit / PER_100ML workflows.
    this.isVolumeUnit() -> true

    // Container-ish / subjective count-ish also need grams (or a truth bridge like mlPerServingUnit)
    this in setOf(
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
        ServingUnit.BUNCH
    ) -> true

    // Mass units already grounded
    this.isMassUnit() -> false

    else -> false
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
 * Returns null if the serving unit is not volume-based or if mlPerUnit would be zero.
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

/** Back-compat helper: throws for non-mass units (matches your old behavior). */
fun ServingUnit.gPerUnit(): Double =
    this.asG ?: throw IllegalArgumentException("ServingUnit $this is not a mass unit")

/** Back-compat helper: nullable. */
fun ServingUnit.gPerUnitOrNull(): Double? = this.asG

/**
 * Helper for comparing values with a tolerance (useful for tests).
 */
fun nearlyEquals(a: Double, b: Double, eps: Double = 1e-9): Boolean = abs(a - b) <= eps

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

        // Legacy household-ish
        "TSP" -> ServingUnit.TSP
        "TBSP" -> ServingUnit.TBSP
        "CUP" -> ServingUnit.CUP
        "QT", "QUART" -> ServingUnit.QUART

        "FLOZ", "FL OZ" -> ServingUnit.FL_OZ_US

        else -> null
    }
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

    else -> throw IllegalArgumentException(
        "ServingUnit $this is not a volume unit"
    )
}

fun ServingUnit.Companion.isAmbiguousForGrounding(unit: ServingUnit): Boolean {
    return when (unit) {
        ServingUnit.TSP_US,
        ServingUnit.TBSP_US,
        ServingUnit.FL_OZ_US,
        ServingUnit.CUP_US,
        ServingUnit.CUP_METRIC,
        ServingUnit.CUP_JP,
        ServingUnit.RCCUP,
        ServingUnit.TSP,
        ServingUnit.TBSP,
        ServingUnit.CUP,
        ServingUnit.CAN,
        ServingUnit.BOTTLE,
        ServingUnit.JAR,
        ServingUnit.SERVING,
        ServingUnit.OTHER -> true
        else -> false
    }
}

fun ServingUnit.isAmbiguousForGrounding(): Boolean {
    return when (this) {
        ServingUnit.TSP_US,
        ServingUnit.TBSP_US,
        ServingUnit.FL_OZ_US,
        ServingUnit.CUP_US,
        ServingUnit.CUP_METRIC,
        ServingUnit.CUP_JP,
        ServingUnit.RCCUP,
        ServingUnit.TSP,
        ServingUnit.TBSP,
        ServingUnit.CUP,
        ServingUnit.CAN,
        ServingUnit.BOTTLE,
        ServingUnit.JAR,
        ServingUnit.SERVING,
        ServingUnit.OTHER -> true
        else -> false
    }
}