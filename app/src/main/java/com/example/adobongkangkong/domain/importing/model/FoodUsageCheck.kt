package com.example.adobongkangkong.domain.importing.model

enum class UsageContext { LOGGING, RECIPE }

sealed interface FoodUsageCheck {
    data object Ok : FoodUsageCheck
    data class Blocked(val message: String) : FoodUsageCheck
}