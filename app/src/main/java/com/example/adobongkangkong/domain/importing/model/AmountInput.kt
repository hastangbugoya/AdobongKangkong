package com.example.adobongkangkong.domain.importing.model

sealed interface AmountInput {
    data class ByServings(val servings: Double) : AmountInput
    data class ByGrams(val grams: Double) : AmountInput
}