package com.example.adobongkangkong.domain.model

import com.example.adobongkangkong.domain.nutrition.NutrientKey
/**
 * Domain command representing a user's intent to upsert a nutrient target.
 * Values are normalized numeric inputs, validated and persisted by domain.
 */
data class TargetEdit(
    val key: NutrientKey,
    val min: Double? = null,
    val target: Double? = null,
    val max: Double? = null
)