package com.example.adobongkangkong.domain.model

data class Food(
    val id: Long,
    val stableId: String,

    val name: String,
    val brand: String?,

    val servingSize: Double,
    val servingUnit: ServingUnit,

    val gramsPerServing: Double?,
    val servingsPerPackage: Double?,

    val isRecipe: Boolean
)