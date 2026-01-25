package com.example.adobongkangkong.domain.model
enum class NutrientUnit(val symbol: String) {
    KCAL("kcal"),
    KJ("kJ"),

    G("g"),
    MG("mg"),
    UG("µg"),

    IU("IU"),
    PERCENT_DV("%DV"),

    // Some datasets use these occasionally
    MCG_DFE("µg DFE"),   // folate dietary folate equivalents
    MG_NE("mg NE"),      // niacin equivalents
    MG_RAE("µg RAE"),    // vitamin A retinol activity equivalents (often µg RAE)
    OTHER("");           // fallback
    companion object {
        fun fromDb(raw: String): NutrientUnit =
            entries.firstOrNull { it.symbol.equals(raw, ignoreCase = true) }
                ?: G
    }
}