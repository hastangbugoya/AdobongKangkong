package com.example.adobongkangkong.domain.logging.model

sealed interface AmountInput {
    data class ByServings(val servings: Double) : AmountInput
    data class ByGrams(val grams: Double) : AmountInput
}