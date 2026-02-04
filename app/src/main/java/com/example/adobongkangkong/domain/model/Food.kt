package com.example.adobongkangkong.domain.model

data class Food(
    val id: Long,
    val stableId: String,

    val name: String,
    val brand: String?,

    val servingSize: Double,
    val servingUnit: ServingUnit,

    val gramsPerServingUnit: Double?,
    val servingsPerPackage: Double?,

    val isRecipe: Boolean,
    val isLowSodium: Boolean? = null
)