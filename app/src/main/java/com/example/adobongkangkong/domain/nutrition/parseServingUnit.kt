package com.example.adobongkangkong.domain.nutrition


import com.example.adobongkangkong.domain.model.ServingUnit

fun parseServingUnit(raw: String): ServingUnit {
    val s = raw.trim().lowercase()
    return when (s) {
        "g", "gram", "grams" -> ServingUnit.G
        "ml", "milliliter", "milliliters" -> ServingUnit.ML
        "tbsp", "tablespoon", "tablespoons" -> ServingUnit.TBSP
        "tsp", "teaspoon", "teaspoons" -> ServingUnit.TSP
        "cup", "cups" -> ServingUnit.CUP
        "oz", "ounce", "ounces" -> ServingUnit.OZ
        "lb", "pound", "pounds" -> ServingUnit.LB
        "qt", "quart" -> ServingUnit.QUART
        "piece", "pcs", "pc" -> ServingUnit.PIECE
        "slice", "slices" -> ServingUnit.SLICE
        "pack", "packet", "pouch" -> ServingUnit.PACK
        "bottle", "bottles" -> ServingUnit.BOTTLE
        "jar", "jars" -> ServingUnit.JAR
        "serving", "servings" -> ServingUnit.SERVING

        else -> ServingUnit.SERVING // safe fallback
    }
}