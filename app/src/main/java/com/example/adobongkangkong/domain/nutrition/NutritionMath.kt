package com.example.adobongkangkong.domain.nutrition

sealed interface IntakeAmount {
    data class Servings(val value: Double) : IntakeAmount
    data class Grams(val value: Double) : IntakeAmount
}
