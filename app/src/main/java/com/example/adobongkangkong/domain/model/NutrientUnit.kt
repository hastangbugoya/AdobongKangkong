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
    OTHER("");

    companion object {
        fun fromDb(raw: String): NutrientUnit {
            val normalized = raw.trim()

            return when {
                normalized.equals("mcg", ignoreCase = true) -> UG
                normalized.equals("ug", ignoreCase = true) -> UG
                normalized.equals("µg", ignoreCase = true) -> UG
                normalized.equals("μg", ignoreCase = true) -> UG

                normalized.equals("kcal", ignoreCase = true) -> KCAL
                normalized.equals("kj", ignoreCase = true) -> KJ
                normalized.equals("g", ignoreCase = true) -> G
                normalized.equals("mg", ignoreCase = true) -> MG
                normalized.equals("iu", ignoreCase = true) -> IU
                normalized.equals("%dv", ignoreCase = true) -> PERCENT_DV
                normalized.equals("mcg dfe", ignoreCase = true) -> MCG_DFE
                normalized.equals("µg dfe", ignoreCase = true) -> MCG_DFE
                normalized.equals("μg dfe", ignoreCase = true) -> MCG_DFE
                normalized.equals("mg ne", ignoreCase = true) -> MG_NE
                normalized.equals("mcg rae", ignoreCase = true) -> MG_RAE
                normalized.equals("µg rae", ignoreCase = true) -> MG_RAE
                normalized.equals("μg rae", ignoreCase = true) -> MG_RAE

                else ->
                    entries.firstOrNull { entry ->
                        entry.name.equals(normalized, ignoreCase = true) ||
                                entry.symbol.equals(normalized, ignoreCase = true)
                    } ?: G
            }
        }
    }
}