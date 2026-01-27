package com.example.adobongkangkong.domain.logging.model

data class LogPreview(
    val calories: Double?,
    val proteinGrams: Double?,
    val carbsGrams: Double?,
    val fatGrams: Double?,
    val sugarGrams: Double?,
    val nutrientsJson: String // resolved totals for THIS log
)
