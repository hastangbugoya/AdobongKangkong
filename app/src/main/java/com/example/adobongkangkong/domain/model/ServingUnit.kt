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

    // Rice cooker cup (commonly used for dry rice measurement)
    // Deterministic as a volume, but NOT safe to convert to grams without density.
    RCCUP("rc cup (180 g)", asG = 180.0),

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
    OTHER("other"),
    BATCH("batch"),

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