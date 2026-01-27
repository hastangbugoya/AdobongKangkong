package com.example.adobongkangkong.domain.logging.model

import java.time.Instant

data class BatchSummary(
    val batchId: Long,
    val recipeId: Long,
    val cookedYieldGrams: Double,
    val servingsYieldUsed: Double?,
    val createdAt: Instant
) {
    fun gramsPerServingCooked(fallbackServings: Double): Double {
        val servings = servingsYieldUsed ?: fallbackServings
        return cookedYieldGrams / servings
    }
}
