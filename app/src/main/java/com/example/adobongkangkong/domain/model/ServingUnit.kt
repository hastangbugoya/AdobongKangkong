package com.example.adobongkangkong.domain.model

/**
 * NOTE ON AMBIGUOUS VOLUME UNITS:
 * - We keep volume units fully-qualified where they commonly vary by locale.
 * - We intentionally omit the US customary cup (~236.588 mL). For nutrition/label math
 *   we standardize on CUP_US = 240 mL.
 *
 * Deterministic conversion fields:
 * - asMl: built-in mL conversion per 1 unit, when the unit has a conventional volume conversion.
 * - asG: built-in grams conversion per 1 unit, when the unit has a conventional mass conversion.
 *
 * Important:
 * - asMl/asG describe a built-in conversion, not the only valid way a food may be grounded.
 * - Example: CUP_US has asMl=240, but a solid food like rice may still be grams-grounded
 *   as "1 cup = 180 g".
 *
 * Bridge policy:
 * - LOCKED_MASS: true physical mass unit; user does not need manual gram bridge.
 * - LOCKED_VOLUME: true physical volume unit; user does not need manual mL bridge.
 * - FLEXIBLE: household/container/serving unit; user may provide grams and/or mL grounding.
 *
 * RCCUP POLICY:
 * - Rice cooker cup remains FLEXIBLE.
 * - Even though it often implies a real-world volume, AK should not force it to mL by default.
 * - Rice nutrition workflows are usually grams-based.
 */
enum class ServingUnit(
    val display: String,
    val asMl: Double? = null,
    val asG: Double? = null,
    val bridgePolicy: ServingUnitBridgePolicy
) {
    // ----------------
    // Physical mass units
    // ----------------
    MG("mg", asG = 0.001, bridgePolicy = ServingUnitBridgePolicy.LOCKED_MASS),
    G("g", asG = 1.0, bridgePolicy = ServingUnitBridgePolicy.LOCKED_MASS),
    KG("kg", asG = 1000.0, bridgePolicy = ServingUnitBridgePolicy.LOCKED_MASS),
    OZ("oz", asG = 28.349523125, bridgePolicy = ServingUnitBridgePolicy.LOCKED_MASS),
    LB("lb", asG = 453.59237, bridgePolicy = ServingUnitBridgePolicy.LOCKED_MASS),

    // ----------------
    // Physical metric volume units
    // ----------------
    ML("ml", asMl = 1.0, bridgePolicy = ServingUnitBridgePolicy.LOCKED_VOLUME),
    L("L", asMl = 1000.0, bridgePolicy = ServingUnitBridgePolicy.LOCKED_VOLUME),

    // ----------------
    // Household / label volume units
    // These have conventional mL conversions, but are FLEXIBLE because solids are often
    // measured with them and should be allowed to use grams-per-unit.
    // ----------------
    TSP_US("tsp (US)", asMl = 5.0, bridgePolicy = ServingUnitBridgePolicy.FLEXIBLE),
    TBSP_US("tbsp (US)", asMl = 15.0, bridgePolicy = ServingUnitBridgePolicy.FLEXIBLE),
    FL_OZ_US("fl oz (US)", asMl = 30.0, bridgePolicy = ServingUnitBridgePolicy.LOCKED_VOLUME),
    CUP_US("cup (US)", asMl = 240.0, bridgePolicy = ServingUnitBridgePolicy.FLEXIBLE),
    PINT_US("pt (US)", asMl = 480.0, bridgePolicy = ServingUnitBridgePolicy.LOCKED_VOLUME),
    QUART_US("qt (US)", asMl = 960.0, bridgePolicy = ServingUnitBridgePolicy.LOCKED_VOLUME),
    GALLON_US("gal (US)", asMl = 3840.0, bridgePolicy = ServingUnitBridgePolicy.LOCKED_VOLUME),

    CUP_METRIC("cup (metric)", asMl = 250.0, bridgePolicy = ServingUnitBridgePolicy.FLEXIBLE),
    CUP_JP("cup (JP)", asMl = 200.0, bridgePolicy = ServingUnitBridgePolicy.FLEXIBLE),

    FL_OZ_IMP("fl oz (imp)", asMl = 28.4130625, bridgePolicy = ServingUnitBridgePolicy.LOCKED_VOLUME),
    PINT_IMP("pt (imp)", asMl = 568.26125, bridgePolicy = ServingUnitBridgePolicy.LOCKED_VOLUME),
    QUART_IMP("qt (imp)", asMl = 1136.5225, bridgePolicy = ServingUnitBridgePolicy.LOCKED_VOLUME),
    GALLON_IMP("gal (imp)", asMl = 4546.09, bridgePolicy = ServingUnitBridgePolicy.LOCKED_VOLUME),

    // ----------------
    // Container / subjective / count units
    // ----------------
    BUNCH("bch", bridgePolicy = ServingUnitBridgePolicy.FLEXIBLE),
    BOX("box", bridgePolicy = ServingUnitBridgePolicy.FLEXIBLE),
    SCOOP("scoop", bridgePolicy = ServingUnitBridgePolicy.FLEXIBLE),
    BAG("bag", bridgePolicy = ServingUnitBridgePolicy.FLEXIBLE),
    PACKET("pkt", bridgePolicy = ServingUnitBridgePolicy.FLEXIBLE),
    CAN("can", bridgePolicy = ServingUnitBridgePolicy.FLEXIBLE),
    PIECE("pc", bridgePolicy = ServingUnitBridgePolicy.FLEXIBLE),
    SLICE("slice", bridgePolicy = ServingUnitBridgePolicy.FLEXIBLE),
    PACK("pack", bridgePolicy = ServingUnitBridgePolicy.FLEXIBLE),
    BOTTLE("bottle", bridgePolicy = ServingUnitBridgePolicy.FLEXIBLE),
    JAR("jar", bridgePolicy = ServingUnitBridgePolicy.FLEXIBLE),
    SERVING("serving", bridgePolicy = ServingUnitBridgePolicy.FLEXIBLE),
    STICK("stick", bridgePolicy = ServingUnitBridgePolicy.FLEXIBLE),
    OTHER("other", bridgePolicy = ServingUnitBridgePolicy.FLEXIBLE),
    BATCH("batch", bridgePolicy = ServingUnitBridgePolicy.FLEXIBLE),
    RCCUP("rc cup", bridgePolicy = ServingUnitBridgePolicy.FLEXIBLE),

    @Deprecated("Use TSP_US (or a fully-qualified tsp) instead.")
    TSP("tsp", asMl = 5.0, bridgePolicy = ServingUnitBridgePolicy.FLEXIBLE),

    @Deprecated("Use TBSP_US (or a fully-qualified tbsp) instead.")
    TBSP("tbsp", asMl = 15.0, bridgePolicy = ServingUnitBridgePolicy.FLEXIBLE),

    @Deprecated("Use CUP_US / CUP_METRIC / CUP_JP instead.")
    CUP("cup", asMl = 240.0, bridgePolicy = ServingUnitBridgePolicy.FLEXIBLE),

    @Deprecated("Use QUART_US / QUART_IMP instead.")
    QUART("qt", asMl = 960.0, bridgePolicy = ServingUnitBridgePolicy.LOCKED_VOLUME);

    companion object
}

enum class ServingUnitBridgePolicy {
    LOCKED_MASS,
    LOCKED_VOLUME,
    FLEXIBLE
}

/**
 * Backward-compatible grounding kind.
 *
 * Important:
 * - This now reflects bridge policy, not merely asG/asMl availability.
 * - A unit may have asMl and still be FLEXIBLE, such as CUP_US or TBSP_US.
 */
enum class ServingUnitGroundingKind {
    MASS,
    VOLUME,
    FLEXIBLE
}

fun ServingUnit.groundingKind(): ServingUnitGroundingKind {
    return when (bridgePolicy) {
        ServingUnitBridgePolicy.LOCKED_MASS -> ServingUnitGroundingKind.MASS
        ServingUnitBridgePolicy.LOCKED_VOLUME -> ServingUnitGroundingKind.VOLUME
        ServingUnitBridgePolicy.FLEXIBLE -> ServingUnitGroundingKind.FLEXIBLE
    }
}

fun ServingUnit.canResolveToGramsDeterministically(): Boolean {
    return asG != null
}

fun ServingUnit.canResolveToMlDeterministically(): Boolean {
    return asMl != null
}

fun ServingUnit.requiresExplicitGrounding(): Boolean {
    return bridgePolicy == ServingUnitBridgePolicy.FLEXIBLE && asG == null && asMl == null
}

fun ServingUnit.allowsManualGramBridge(): Boolean {
    return bridgePolicy != ServingUnitBridgePolicy.LOCKED_MASS
}

fun ServingUnit.allowsManualMlBridge(): Boolean {
    return bridgePolicy != ServingUnitBridgePolicy.LOCKED_VOLUME
}

fun ServingUnit.shouldAllowExplicitGroundingOverride(): Boolean {
    return bridgePolicy == ServingUnitBridgePolicy.FLEXIBLE
}

@Deprecated(
    message = "Use bridgePolicy/allowsManualGramBridge()/allowsManualMlBridge()/requiresExplicitGrounding() instead."
)
fun ServingUnit.isAmbiguousForGrounding(): Boolean {
    return requiresExplicitGrounding()
}