package com.example.adobongkangkong.domain.model

data class FoodCategory(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val isSystem: Boolean,
)
