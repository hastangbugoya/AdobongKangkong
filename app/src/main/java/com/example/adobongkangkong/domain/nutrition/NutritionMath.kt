package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity

sealed interface IntakeAmount {
    data class Servings(val value: Double) : IntakeAmount
    data class Grams(val value: Double) : IntakeAmount
}
