package com.example.adobongkangkong.data.csvimport

import com.example.adobongkangkong.domain.model.ServingUnit

object CsvUnits {

    data class ParsedWeight(
        val grams: Double?,          // if we can compute grams
        val original: String
    )

    fun parseWeightToGrams(raw: String?): ParsedWeight {
        val s = raw?.trim().orEmpty()
        if (s.isBlank()) return ParsedWeight(null, s)

        // Take first token that looks like "<number><unit>"
        // Examples: "165g", "1lb", "85g(x5.bag)", "8floz"
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
            "lb" -> num * 453.59237
            // volume units: we do NOT convert to grams without density
            "ml", "floz", "fl", "fl oz" -> null
            else -> null
        }

        return ParsedWeight(grams = grams, original = s)
    }

    fun parseServingUnit(raw: String?): ServingUnit {
        val s = raw?.trim().orEmpty().lowercase()
        if (s.isBlank()) return ServingUnit.OTHER

        // handle combined forms like "1tbsp" or "0.5cup"
        val letters = s.filter { it.isLetter() || it == ' ' }.trim()

        return when (letters) {
            "g", "gram", "grams" -> ServingUnit.G
            "oz", "ounce", "ounces" -> ServingUnit.OZ
            "lb", "pound", "pounds" -> ServingUnit.LB
            "ml" -> ServingUnit.ML
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
            else -> ServingUnit.OTHER
        }
    }

    /**
     * Stable-ish ID for CSV foods when FoodEntity expects "stable ID from CSV".
     * Uses a deterministic hash of the normalized name + serving + weight string.
     */
    fun stableFoodId(name: String, serv: String?, weight: String?): Long {
        val key = buildString {
            append(name.trim().lowercase())
            append("|")
            append(serv?.trim()?.lowercase().orEmpty())
            append("|")
            append(weight?.trim()?.lowercase().orEmpty())
        }
        // 64-bit FNV-1a
        var hash = -0x340d631b7bdddcdbL
        val prime = 1099511628211L
        for (ch in key) {
            hash = hash xor ch.code.toLong()
            hash *= prime
        }
        // Keep it positive and non-zero
        val positive = (hash and Long.MAX_VALUE)
        return if (positive == 0L) 1L else positive
    }
}
