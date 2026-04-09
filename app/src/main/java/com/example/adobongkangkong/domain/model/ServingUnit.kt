package com.example.adobongkangkong.domain.model

/**
 * NOTE ON AMBIGUOUS VOLUME UNITS:
 * - We keep volume units fully-qualified where they commonly vary by locale (e.g., cup variants).
 * - We intentionally omit the US customary cup (~236.588 mL). For nutrition/label math we standardize on CUP_US = 240 mL.
 *
 * Deterministic conversion fields:
 * - asMl: mL per 1 unit (for volume-like units only)
 * - asG: grams per 1 unit (for mass-like units only)
 *
 * Units that are container-ish / subjective / count-ish should keep both null.
 *
 * RCCUP POLICY (locked in):
 * - Rice cooker cup remains explicitly grounded / ambiguous.
 * - Even though a rice cooker cup often implies a real-world volume, AK should not treat it as
 *   deterministically mL-grounded by default.
 * - Rationale: rice nutrition/label workflows in AK are primarily grams-based, and assuming a fixed
 *   mL grounding for rc cup would create misleading confidence for serving-based nutrition math.
 */
enum class ServingUnit(
    val display: String,
    val asMl: Double? = null,
    val asG: Double? = null
) {
    // ----------------
    // Mass (asG)
    // ----------------
    MG("mg", asG = 0.001),
    G("g", asG = 1.0),
    KG("kg", asG = 1000.0),
    OZ("oz", asG = 28.349523125),
    LB("lb", asG = 453.59237),

    // ----------------
    // Metric volume (asMl)
    // ----------------
    ML("ml", asMl = 1.0),
    L("L", asMl = 1000.0),

    // ----------------
    // US (nutrition-standard) volume set
    // Internally consistent: 1 cup = 240 mL, 1 fl oz = 30 mL
    // ----------------
    TSP_US("tsp (US)", asMl = 5.0),
    TBSP_US("tbsp (US)", asMl = 15.0),
    FL_OZ_US("fl oz (US)", asMl = 30.0),
    CUP_US("cup (US)", asMl = 240.0),
    PINT_US("pt (US)", asMl = 480.0),
    QUART_US("qt (US)", asMl = 960.0),
    GALLON_US("gal (US)", asMl = 3840.0),

    // Cup variants (explicit)
    CUP_METRIC("cup (metric)", asMl = 250.0),
    CUP_JP("cup (JP)", asMl = 200.0),

    // Imperial / UK volume set (exact)
    FL_OZ_IMP("fl oz (imp)", asMl = 28.4130625),
    PINT_IMP("pt (imp)", asMl = 568.26125),
    QUART_IMP("qt (imp)", asMl = 1136.5225),
    GALLON_IMP("gal (imp)", asMl = 4546.09),

    // ----------------
    // Legacy / custom / container-ish units (no deterministic conversion)
    // ----------------
    BUNCH("bch"),
    BOX("box"),
    SCOOP("scoop"),
    BAG("bag"),
    PACKET("pkt"),
    CAN("can"),
    PIECE("pc"),
    SLICE("slice"),
    PACK("pack"),
    BOTTLE("bottle"),
    JAR("jar"),
    SERVING("serving"),
    STICK("stick"),
    OTHER("other"),
    BATCH("batch"),
    // Rice cooker cup intentionally stays ambiguous / explicitly grounded.
    RCCUP("rc cup"),

    /**
     * Legacy aliases kept for backward compatibility.
     * Prefer the fully-qualified units above for new code/data.
     */
    @Deprecated("Use TSP_US (or a fully-qualified tsp) instead.")
    TSP("tsp", asMl = 5.0),

    @Deprecated("Use TBSP_US (or a fully-qualified tbsp) instead.")
    TBSP("tbsp", asMl = 15.0),

    @Deprecated("Use CUP_US / CUP_METRIC / CUP_JP instead.")
    CUP("cup", asMl = 240.0),

    @Deprecated("Use QUART_US / QUART_IMP instead.")
    QUART("qt", asMl = 960.0);

    companion object
}

/**
 * Grounding model for serving units.
 *
 * MASS:
 * - unit is inherently gram-resolvable
 *
 * VOLUME:
 * - unit is inherently mL-resolvable
 *
 * FLEXIBLE:
 * - unit is container-ish / subjective and requires explicit user grounding
 */
enum class ServingUnitGroundingKind {
    MASS,
    VOLUME,
    FLEXIBLE
}

fun ServingUnit.groundingKind(): ServingUnitGroundingKind {
    return when {
        asG != null -> ServingUnitGroundingKind.MASS
        asMl != null -> ServingUnitGroundingKind.VOLUME
        else -> ServingUnitGroundingKind.FLEXIBLE
    }
}

fun ServingUnit.canResolveToGramsDeterministically(): Boolean {
    return groundingKind() == ServingUnitGroundingKind.MASS
}

fun ServingUnit.canResolveToMlDeterministically(): Boolean {
    return groundingKind() == ServingUnitGroundingKind.VOLUME
}

fun ServingUnit.requiresExplicitGrounding(): Boolean {
    return groundingKind() == ServingUnitGroundingKind.FLEXIBLE
}

/**
 * Policy helper:
 * even if a unit is already mass/volume grounded by nature, the editor may still
 * allow the user to enter explicit grounding values for override/precision.
 */
fun ServingUnit.shouldAllowExplicitGroundingOverride(): Boolean {
    return when (this) {
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
        ServingUnit.RCCUP,
        ServingUnit.TSP,
        ServingUnit.TBSP,
        ServingUnit.CUP,
        ServingUnit.QUART,
        ServingUnit.CAN,
        ServingUnit.BOTTLE,
        ServingUnit.JAR,
        ServingUnit.SERVING,
        ServingUnit.OTHER -> true

        else -> false
    }
}

/**
 * Backward-compatibility shim.
 *
 * Historically this mixed together:
 * - "unit needs explicit grounding"
 * - "unit should allow manual override"
 *
 * That caused volume units like TBSP/CUP to be treated too much like SERVING/JAR.
 *
 * Keep this temporarily so old call sites compile, but migrate call sites to:
 * - requiresExplicitGrounding()
 * - canResolveToMlDeterministically()
 * - canResolveToGramsDeterministically()
 * - shouldAllowExplicitGroundingOverride()
 */
@Deprecated(
    message = "Use requiresExplicitGrounding()/canResolveToMlDeterministically()/canResolveToGramsDeterministically()/shouldAllowExplicitGroundingOverride() instead."
)
fun ServingUnit.isAmbiguousForGrounding(): Boolean {
    return requiresExplicitGrounding()
}