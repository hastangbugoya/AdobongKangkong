package com.example.adobongkangkong.domain.model

data class FoodGoalFlags(
    val foodId: Long,
    val eatMore: Boolean,
    val limit: Boolean,
    val favorite: Boolean
)
