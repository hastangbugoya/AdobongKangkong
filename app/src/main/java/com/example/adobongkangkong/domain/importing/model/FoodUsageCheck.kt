package com.example.adobongkangkong.domain.importing.model

sealed interface FoodUsageCheck {
    data object Ok : FoodUsageCheck
    data class Blocked(val message: String) : FoodUsageCheck
}