package com.example.adobongkangkong.data.csvimport

import com.example.adobongkangkong.domain.model.ServingUnit

/**
 * =============================================================================
 * CSV UNIT PARSING — CANONICAL SOURCE OF TRUTH
 * =============================================================================
 *
 * This file is responsible for:
 * 1) Determining the **serving unit identity** for imported foods
 * 2) Extracting **grams-per-serving** when weight is provided
 *
 * -----------------------------------------------------------------------------
 * 🚨 CRITICAL DESIGN PRINCIPLE (DO NOT VIOLATE)
 * -----------------------------------------------------------------------------
 *
 * UNIT IDENTITY and UNIT GROUNDING are SEPARATE concerns.
 *
 * - Unit identity answers: "what is the serving unit?"
 *      → e.g. LB, CUP, SERVING
 *
 * - Grounding answers: "how many grams or mL is 1 unit?"
 *      → gramsPerServingUnit / mlPerServingUnit
 *
 * DO NOT mix these concerns.
 * DO NOT infer one from the other.
 *
 * -----------------------------------------------------------------------------
 * ✅ SERVING UNIT RESOLUTION — STRICT PRECEDENCE RULE
 * -----------------------------------------------------------------------------
 *
 * The importer MUST follow this exact order:
 *
 *   1. If `serv` column yields a valid unit → USE IT
 *   2. Else if `weight` column contains a known unit → DERIVE FROM WEIGHT
 *   3. Else → fallback to OTHER
 *
 * This rule is:
 * - deterministic
 * - non-heuristic
 * - non-lossy
 *
 * DO NOT change precedence.
 * DO NOT "improve" with guesses.
 *
 * -----------------------------------------------------------------------------
 * ❌ ABSOLUTE DO-NOT-DO RULES
 * -----------------------------------------------------------------------------
 *
 * - NEVER infer grams from volume (no density guessing)
 * - NEVER infer volume from grams
 * - NEVER override an explicit `serv` value with weight
 * - NEVER collapse LB/OZ into G at the unit level
 * - NEVER introduce "smart" heuristics
 *
 * -----------------------------------------------------------------------------
 * 🧠 WHY THIS EXISTS
 * -----------------------------------------------------------------------------
 *
 * Historical bug:
 * - Foods with weight "1lb" and no `serv` were assigned:
 *     servingUnit = OTHER ❌
 *     gramsPerServingUnit = 453.59 ✅
 *
 * This created:
 * - correct math
 * - WRONG unit identity
 *
 * Result:
 * - UI showed "other"
 * - system lost semantic meaning (LB)
 *
 * This file ensures:
 * → unit identity is ALWAYS preserved when possible
 *
 * -----------------------------------------------------------------------------
 * 🧪 EXPECTED BEHAVIOR
 * -----------------------------------------------------------------------------
 *
 * CSV INPUT                         → RESULT
 * ----------------------------------------------------------------
 * serv="lb", weight="1lb"          → LB
 * serv=null, weight="1lb"          → LB
 * serv=null, weight="165g"         → G
 * serv=null, weight="400ml"        → ML
 * serv="cup", weight="200g"        → CUP  (serv wins)
 * serv=null, weight=null           → OTHER
 *
 * -----------------------------------------------------------------------------
 * 🔒 FUTURE SAFETY NOTE
 * -----------------------------------------------------------------------------
 *
 * If this logic changes, you MUST:
 * - update importer
 * - update FoodEditor assumptions
 * - validate against existing DB data
 *
 * This is NOT a "refactor-friendly" area.
 * This is a **data contract boundary**.
 * =============================================================================
 */
object CsvUnits {

    /**
     * Parsed weight result.
     *
     * @property grams grams equivalent IF safely computable
     * @property original original raw string (for debugging / traceability)
     */
    data class ParsedWeight(
        val grams: Double?,
        val original: String
    )

    /**
     * Parses a weight string into grams when possible.
     *
     * Supported:
     * - g, kg, oz, lb
     *
     * NOT supported:
     * - volume → grams (no density guessing)
     *
     * Examples:
     * - "165g"      → 165
     * - "1lb"       → 453.59237
     * - "8floz"     → null (volume)
     */
    fun parseWeightToGrams(raw: String?): ParsedWeight {
        val s = raw?.trim().orEmpty()
        if (s.isBlank()) return ParsedWeight(null, s)

        val token = s.lowercase()
            .replace(" ", "")
            .takeWhile { it.isDigit() || it == '.' || it.isLetter() }

        val num = token.takeWhile { it.isDigit() || it == '.' }.toDoubleOrNull()
            ?: return ParsedWeight(null, s)

        val unit = token.dropWhile { it.isDigit() || it == '.' }

        val grams = when (unit) {
            "g" -> num
            "kg" -> num * 1000.0
            "oz" -> num * 28.349523125
            "lb","lbs" -> num * 453.59237

            // volume units → DO NOT CONVERT
            "ml", "floz", "fl", "fl oz" -> null

            else -> null
        }

        return ParsedWeight(grams = grams, original = s)
    }

    /**
     * Extracts ONLY the unit identity from weight.
     *
     * This is used when `serv` is missing.
     *
     * IMPORTANT:
     * - This does NOT compute grams
     * - This does NOT infer anything
     *
     * It only answers:
     * → "what unit is this?"
     */
    fun parseWeightUnit(raw: String?): ServingUnit? {
        val s = raw?.trim().orEmpty()
        if (s.isBlank()) return null

        val token = s.lowercase()
            .replace(" ", "")
            .takeWhile { it.isDigit() || it == '.' || it.isLetter() }

        val unit = token.dropWhile { it.isDigit() || it == '.' }

        return when (unit) {
            "g" -> ServingUnit.G
            "kg" -> ServingUnit.KG
            "oz" -> ServingUnit.OZ
            "lb","lbs" -> ServingUnit.LB
            "ml" -> ServingUnit.ML
            "floz", "fl", "fl oz" -> ServingUnit.FL_OZ_US
            else -> null
        }
    }

    /**
     * Parses serving unit from CSV "serv" column.
     *
     * If unknown → returns OTHER (NOT null).
     */
    fun parseServingUnit(raw: String?): ServingUnit {
        val s = raw?.trim().orEmpty().lowercase()
        if (s.isBlank()) return ServingUnit.OTHER

        val letters = s.filter { it.isLetter() || it == ' ' }.trim()

        return when (letters) {
            "g", "gram", "grams" -> ServingUnit.G
            "oz", "ounce", "ounces" -> ServingUnit.OZ
            "lb", "pound", "pounds" -> ServingUnit.LB
            "ml" -> ServingUnit.ML
            "rc" -> ServingUnit.RCCUP
            "cup", "cups" -> ServingUnit.CUP
            "tbsp", "tablespoon", "tablespoons" -> ServingUnit.TBSP
            "tsp", "teaspoon", "teaspoons" -> ServingUnit.TSP

            "pc", "pcs", "piece", "pieces" -> ServingUnit.PIECE
            "serv", "serving", "servings", "1 serv", "1 serving" -> ServingUnit.SERVING

            "bunch" -> ServingUnit.BUNCH
            "can" -> ServingUnit.CAN
            "bag" -> ServingUnit.BAG
            "box" -> ServingUnit.BOX
            "jar" -> ServingUnit.JAR
            "packet" -> ServingUnit.PACKET
            "pack" -> ServingUnit.PACK

            "qt" -> ServingUnit.QUART

            else -> ServingUnit.OTHER
        }
    }

    /**
     * =============================================================================
     * 🔥 CANONICAL UNIT RESOLUTION ENTRY POINT
     * =============================================================================
     *
     * This is the ONLY function the importer should call.
     *
     * DO NOT reimplement this logic elsewhere.
     *
     * -----------------------------------------------------------------------------
     * RULE:
     * -----------------------------------------------------------------------------
     *
     * 1) serv → if valid (not OTHER) → use it
     * 2) else weight → if recognizable → use it
     * 3) else → OTHER
     *
     * -----------------------------------------------------------------------------
     * THIS FUNCTION MUST REMAIN PURE + DETERMINISTIC
     * -----------------------------------------------------------------------------
     */
    fun resolveServingUnit(
        servRaw: String?,
        weightRaw: String?
    ): ServingUnit {
        val fromServ = parseServingUnit(servRaw)
        if (fromServ != ServingUnit.OTHER) return fromServ

        val fromWeight = parseWeightUnit(weightRaw)
        if (fromWeight != null) return fromWeight

        return ServingUnit.OTHER
    }

    /**
     * Stable ID generator (unchanged).
     */
    fun stableFoodId(name: String, serv: String?, weight: String?): Long {
        val key = buildString {
            append(name.trim().lowercase())
            append("|")
            append(serv?.trim()?.lowercase().orEmpty())
            append("|")
            append(weight?.trim()?.lowercase().orEmpty())
        }

        var hash = -0x340d631b7bdddcdbL
        val prime = 1099511628211L

        for (ch in key) {
            hash = hash xor ch.code.toLong()
            hash *= prime
        }

        val positive = (hash and Long.MAX_VALUE)
        return if (positive == 0L) 1L else positive
    }
}