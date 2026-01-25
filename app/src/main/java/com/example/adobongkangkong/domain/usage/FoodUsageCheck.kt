package com.example.adobongkangkong.domain.usage

sealed interface FoodUsageCheck {
    data object Ok : FoodUsageCheck
    data class Blocked(
        val reason: BlockReason,
        val message: String
    ) : FoodUsageCheck
}

enum class BlockReason {
    MissingGramsPerServing
}
