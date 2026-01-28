package com.example.adobongkangkong.domain.model

data class UserNutrientTarget(
    val nutrientCode: String,
    val minPerDay: Double? = null,
    val targetPerDay: Double? = null,
    val maxPerDay: Double? = null
)
