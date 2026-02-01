package com.example.adobongkangkong.domain.repository

data class RecipeNutritionComputationResult(
    val rawYieldGrams: Double,
    val totalNutrients: Map<Long, Double>, // nutrientId -> total amount for whole recipe
    val per100g: Map<Long, Double>,        // nutrientId -> amount per 100g (based on rawYield)
    val blockedMessages: List<String>
)
