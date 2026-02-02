package com.example.adobongkangkong.domain.model

/**
 * NOTE ON AMBIGUOUS VOLUME UNITS:
 * - We keep volume units fully-qualified where they commonly vary by locale (e.g., cup variants).
 * - We intentionally omit the US customary cup (~236.588 mL). For nutrition/label math we standardize on CUP_US = 240 mL.
 *
 * Display strings are user-facing and may include suffixes like "cup (US)" to avoid ambiguity.
 */
enum class ServingUnit(val display: String) {
    // Mass
    MG("mg"),
    G("g"),
    KG("kg"),
    OZ("oz"),
    LB("lb"),

    // Metric volume
    ML("ml"),
    L("L"),

    // US (nutrition-standard) volume set (internally consistent: 1 cup = 240 mL, 1 fl oz = 30 mL)
    TSP_US("tsp (US)"),
    TBSP_US("tbsp (US)"),
    FL_OZ_US("fl oz (US)"),
    CUP_US("cup (US)"),
    PINT_US("pt (US)"),
    QUART_US("qt (US)"),
    GALLON_US("gal (US)"),

    // Cup variants (explicit)
    CUP_METRIC("cup (metric)"), // 250 mL
    CUP_JP("cup (JP)"),         // 200 mL

    // Imperial / UK volume set (exact)
    FL_OZ_IMP("fl oz (imp)"),
    PINT_IMP("pt (imp)"),
    QUART_IMP("qt (imp)"),
    GALLON_IMP("gal (imp)"),

    // Legacy / custom / container-ish units (often require grams-per-serving to be meaningful)
    BUNCH("bch"),
    BOX("box"),
    SCOOP("scoop"),
    RCCUP("rc cup (180 mL)"),
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

    /**
     * Legacy aliases kept for backward compatibility.
     * Prefer the fully-qualified units above for new code/data.
     */
    @Deprecated("Use TSP_US (or a fully-qualified tsp) instead.")
    TSP("tsp"),
    @Deprecated("Use TBSP_US (or a fully-qualified tbsp) instead.")
    TBSP("tbsp"),
    @Deprecated("Use CUP_US / CUP_METRIC / CUP_JP instead.")
    CUP("cup"),
    @Deprecated("Use QUART_US / QUART_IMP instead.")
    QUART("qt")
}
