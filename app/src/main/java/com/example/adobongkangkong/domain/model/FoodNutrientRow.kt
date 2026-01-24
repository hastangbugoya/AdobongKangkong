package com.example.adobongkangkong.domain.model

import com.example.adobongkangkong.data.local.db.entity.BasisType

data class FoodNutrientRow(
    val nutrient: Nutrient,
    val amount: Double,
    val basisType: BasisType,
    val basisGrams: Double?
)